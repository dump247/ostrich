package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.Closeable;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ServicePoolProxyTest {
    private static final Service FOO_SERVICE = mock(Service.class);
    private static final RetryPolicy NEVER_RETRY = mock(RetryPolicy.class);

    @SuppressWarnings("unchecked")
    private final ServicePool<Service> _pool = mock(ServicePool.class);

    @Test
    public void testProxyDoesNotOverrideClose() throws IOException {
        // Because this proxy is created with shutdownPoolOnClose=false, the Service.close() method is passed
        // through to the underlying service implementation.
        Service service = ServicePoolProxy.create(Service.class, NEVER_RETRY, _pool, false);
        service.close();

        // Capture and execute the callback.
        @SuppressWarnings("unchecked") ArgumentCaptor<ServiceCallback<Service, ?>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(ServiceCallback.class);
        verify(_pool).execute(same(NEVER_RETRY), captor.capture());
        captor.getValue().call(FOO_SERVICE);

        verify(FOO_SERVICE).close();
        verify(_pool, never()).close();
    }

    @Test
    public void testProxyDoesNotImplementCloseable() throws IOException {
        Service service = ServicePoolProxy.create(Service.class, NEVER_RETRY, _pool, false);

        assertFalse(service instanceof Closeable);
    }

    @Test
    public void testProxyImplementsCloseable() throws IOException {
        Service service = ServicePoolProxy.create(Service.class, NEVER_RETRY, _pool, true);

        assertTrue(service instanceof Closeable);
    }

    @Test
    public void testProxyCallsExecutorShutdownOnClose() throws IOException {
        Service service = ServicePoolProxy.create(Service.class, NEVER_RETRY, _pool, true);
        service.close();

        verify(_pool).close();
    }

    @Test
    public void testGetServicePool() {
        ServicePoolProxy<Service> proxy = new ServicePoolProxy<Service>(Service.class, NEVER_RETRY, _pool, false);

        assertSame(_pool, proxy.getServicePool());
    }

    private static interface Service {
        void close();
    }
}
