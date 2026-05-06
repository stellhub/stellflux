package io.github.stellflux.http.client.config;

import io.github.stellflux.http.client.StellfluxHttpClient;
import io.github.stellflux.http.client.StellfluxHttpClientFactory;
import io.github.stellflux.http.client.StellfluxHttpClientOptions;
import org.springframework.beans.factory.FactoryBean;

/** Factory bean for annotated Stellflux HTTP clients. */
public class StellfluxAnnotatedHttpClientFactoryBean implements FactoryBean<StellfluxHttpClient> {

    private final StellfluxHttpClientFactory factory = new StellfluxHttpClientFactory();

    private StellfluxHttpClientOptions options;

    public void setOptions(StellfluxHttpClientOptions options) {
        this.options = options;
    }

    @Override
    public StellfluxHttpClient getObject() {
        return this.factory.create(this.options);
    }

    @Override
    public Class<?> getObjectType() {
        return StellfluxHttpClient.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
