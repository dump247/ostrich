package com.bazaarvoice.soa.discovery;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointJsonCodec;
import com.bazaarvoice.zookeeper.internal.CuratorConnection;
import com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.recipes.cache.ChildData;
import com.netflix.curator.framework.recipes.cache.PathChildrenCache;
import com.netflix.curator.framework.recipes.cache.PathChildrenCacheEvent;
import com.netflix.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ThreadFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The <code>ZooKeeperHostDiscovery</code> class watches a service path in ZooKeeper and will monitor which hosts are
 * available.  As hosts come and go the results of calling the <code>#getHosts</code> method changes.
 */
public class ZooKeeperHostDiscovery implements HostDiscovery {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperHostDiscovery.class);

    private final CuratorFramework _curator;
    private final Set<ServiceEndPoint> _endPoints;
    private final Set<EndPointListener> _listeners;
    private final PathChildrenCache _pathCache;

    public ZooKeeperHostDiscovery(ZooKeeperConnection connection, String serviceName) {
        this(((CuratorConnection) checkNotNull(connection)).getCurator(), serviceName);
    }

    @VisibleForTesting
    ZooKeeperHostDiscovery(CuratorFramework curator, String serviceName) {
        checkNotNull(curator);
        checkNotNull(serviceName);
        checkArgument(curator.isStarted());
        checkArgument(!"".equals(serviceName));

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(getClass().getSimpleName() + "(" + serviceName + ")-%d")
                .setDaemon(true)
                .build();
        String servicePath = ZooKeeperServiceRegistry.makeServicePath(serviceName);

        _curator = curator;
        _endPoints = Sets.newSetFromMap(Maps.<ServiceEndPoint, Boolean>newConcurrentMap());
        _listeners = Sets.newSetFromMap(Maps.<EndPointListener, Boolean>newConcurrentMap());

        _pathCache = new PathChildrenCache(_curator, servicePath, true, threadFactory);
        try {
            _pathCache.getListenable().addListener(new ServiceListener());

            // This must be synchronized so async remove events aren't processed between start() and adding end points.
            // Use synchronous start(true) instead of asynchronous start(false) so we can tell when it's done and the
            // HostDiscovery set is usable.
            synchronized (this) {
                _pathCache.start(true);
                for (ChildData childData : _pathCache.getCurrentData()) {
                    addEndPoint(toEndPoint(childData));
                }
            }
        } catch (Throwable t) {
            Closeables.closeQuietly(this);
            throw Throwables.propagate(t);
        }
    }

    @Override
    public Iterable<ServiceEndPoint> getHosts() {
        return Iterables.unmodifiableIterable(_endPoints);
    }

    @Override
    public boolean contains(ServiceEndPoint endPoint) {
        return _endPoints.contains(endPoint);
    }

    @Override
    public void addListener(EndPointListener listener) {
        _listeners.add(listener);
    }

    @Override
    public void removeListener(EndPointListener listener) {
        _listeners.remove(listener);
    }

    @Override
    public synchronized void close() throws IOException {
        _listeners.clear();
        _pathCache.close();
        _endPoints.clear();
    }

    @VisibleForTesting
    CuratorFramework getCurator() {
        return _curator;
   }

    private synchronized void addEndPoint(ServiceEndPoint endPoint) {
        // synchronize the modification of _endPoints and firing of events so listeners always receive events in the
        // order they occur.
        if (_endPoints.add(endPoint)) {
            fireAddEvent(endPoint);
        }
    }

    private synchronized void removeEndPoint(ServiceEndPoint endPoint) {
        // synchronize the modification of _endPoints and firing of events so listeners always receive events in the
        // order they occur.
        if (_endPoints.remove(endPoint)) {
            fireRemoveEvent(endPoint);
        }
    }

    private synchronized void clearEndPoints() {
        // synchronize the modification of _endPoints and firing of events so listeners always receive events in the
        // order they occur.
        Collection<ServiceEndPoint> endPoints = ImmutableList.copyOf(_endPoints);
        _endPoints.clear();
        for (ServiceEndPoint endPoint : endPoints) {
            fireRemoveEvent(endPoint);
        }
    }

    private void fireAddEvent(ServiceEndPoint endPoint) {
        for (EndPointListener listener : _listeners) {
            listener.onEndPointAdded(endPoint);
        }
    }

    private void fireRemoveEvent(ServiceEndPoint endPoint) {
        for (EndPointListener listener : _listeners) {
            listener.onEndPointRemoved(endPoint);
        }
    }

    private ServiceEndPoint toEndPoint(ChildData data) {
        String json = new String(data.getData(), Charsets.UTF_8);
        return ServiceEndPointJsonCodec.fromJson(json);
    }

    /** A curator <code>PathChildrenCacheListener</code> */
    private final class ServiceListener implements PathChildrenCacheListener {
        @Override
        public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
            if (event.getType() == PathChildrenCacheEvent.Type.RESET) {
                clearEndPoints();
                return;
            }

            ServiceEndPoint endPoint = toEndPoint(event.getData());
            switch (event.getType()) {
                case CHILD_ADDED:
                    addEndPoint(endPoint);
                    break;

                case CHILD_REMOVED:
                    removeEndPoint(endPoint);
                    break;

                case CHILD_UPDATED:
                    LOG.info("ServiceEndPoint data changed unexpectedly. End point ID: {}; ZooKeeperPath {}",
                            endPoint.getId(), event.getData().getPath());
                    break;
            }
        }
    }
}
