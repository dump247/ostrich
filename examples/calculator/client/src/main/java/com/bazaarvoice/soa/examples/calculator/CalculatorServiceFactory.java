package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.ServicePoolStatistics;
import com.bazaarvoice.soa.loadbalance.RandomAlgorithm;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.ApacheHttpClient4Handler;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;
import com.yammer.dropwizard.client.HttpClientConfiguration;
import com.yammer.dropwizard.client.HttpClientFactory;
import com.yammer.dropwizard.jersey.JacksonMessageBodyProvider;
import com.yammer.dropwizard.json.Json;
import org.apache.http.client.HttpClient;

import java.net.URI;

public class CalculatorServiceFactory implements ServiceFactory<CalculatorService> {
    private final Client _client;

    /**
     * Connects to the CalculatorService using the Apache commons http client library.
     */
    public CalculatorServiceFactory(HttpClientConfiguration configuration) {
        this(createDefaultJerseyClient(configuration));
    }

    /**
     * Connects to the CalculatorService using the specified Jersey client.  If you're writing a Dropwizard server,
     * use @{link JerseyClientFactory} to create the Jersey client.
     */
    public CalculatorServiceFactory(Client jerseyClient) {
        _client = jerseyClient;
    }

    private static ApacheHttpClient4 createDefaultJerseyClient(HttpClientConfiguration configuration) {
        HttpClient httpClient = new HttpClientFactory(configuration).build();
        ApacheHttpClient4Handler handler = new ApacheHttpClient4Handler(httpClient, null, true);
        ApacheHttpClient4Config config = new DefaultApacheHttpClient4Config();
        config.getSingletons().add(new JacksonMessageBodyProvider(new Json()));
        return new ApacheHttpClient4(handler, config);
    }

    @Override
    public String getServiceName() {
        return "calculator";
    }

    @Override
    public LoadBalanceAlgorithm getLoadBalanceAlgorithm(ServicePoolStatistics stats) {
        return new RandomAlgorithm();
    }

    @Override
    public CalculatorService create(ServiceEndPoint endPoint) {
        return new CalculatorClient(endPoint, _client);
    }

    @Override
    public boolean isRetriableException(Exception exception) {
        // Try another server if network error (ClientHandlerException) or 5xx response code (UniformInterfaceException)
        return exception instanceof ClientHandlerException ||
                (exception instanceof UniformInterfaceException &&
                        ((UniformInterfaceException) exception).getResponse().getStatus() >= 500);
    }

    @Override
    public boolean isHealthy(ServiceEndPoint endPoint) {
        URI adminUrl = Payload.valueOf(endPoint.getPayload()).getAdminUrl();
        return _client.resource(adminUrl).path("/healthcheck").head().getStatus() == 200;
    }
}
