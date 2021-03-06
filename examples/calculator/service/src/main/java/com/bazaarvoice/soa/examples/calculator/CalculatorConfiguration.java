package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.zookeeper.dropwizard.ZooKeeperConfiguration;
import com.yammer.dropwizard.config.Configuration;
import org.codehaus.jackson.annotate.JsonProperty;

public class CalculatorConfiguration extends Configuration {

    private ZooKeeperConfiguration _zooKeeperConfiguration = new ZooKeeperConfiguration();

    public ZooKeeperConfiguration getZooKeeperConfiguration() {
        return _zooKeeperConfiguration;
    }

    @JsonProperty("zooKeeper")
    public void setZookeeperConfiguration(ZooKeeperConfiguration zooKeeperConfiguration) {
        _zooKeeperConfiguration = zooKeeperConfiguration;
    }
}
