package io.github.stellflux.stellnula;

import io.github.stellnula.client.StellnulaClient;
import io.github.stellnula.config.StellnulaConfigChange;
import io.github.stellnula.config.StellnulaConfigEntry;
import io.github.stellnula.config.StellnulaListenerRegistration;
import io.github.stellnula.config.StellnulaSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

/** Stellnula PropertySource 运行期刷新器。 */
public class StellfluxStellnulaPropertySourceRefresher
        implements SmartInitializingSingleton, ApplicationEventPublisherAware, DisposableBean {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellnulaPropertySourceRefresher.class.getName());

    private final StellnulaClient client;
    private final StellfluxStellnulaPropertySource propertySource;
    private ApplicationEventPublisher eventPublisher;
    private StellnulaListenerRegistration listenerRegistration;

    public StellfluxStellnulaPropertySourceRefresher(
            StellnulaClient client, StellfluxStellnulaPropertySource propertySource) {
        this.client = client;
        this.propertySource = propertySource;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void afterSingletonsInstantiated() {
        this.listenerRegistration =
                this.client.listen(
                        event -> refreshPropertySource(event.currentSnapshot(), event.changes()));
        LOGGER.info("Stellnula dynamic configuration listener registered");
    }

    @Override
    public void destroy() {
        if (this.listenerRegistration != null) {
            this.listenerRegistration.close();
        }
    }

    private void refreshPropertySource(
            StellnulaSnapshot snapshot, List<StellnulaConfigChange> changes) {
        Map<String, String> properties = StellfluxStellnulaPropertyMapper.toProperties(snapshot);
        this.propertySource.replace(properties);
        Set<String> keys =
                changes.stream()
                        .map(StellfluxStellnulaPropertySourceRefresher::resolveKey)
                        .filter(key -> key != null && !key.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
        LOGGER.info(
                () ->
                        "Stellnula configuration refreshed"
                                + ", revision="
                                + snapshot.revision()
                                + ", changedKeys="
                                + keys
                                + ", propertyCount="
                                + properties.size());
        if (this.eventPublisher != null) {
            this.eventPublisher.publishEvent(
                    new StellfluxStellnulaConfigChangeEvent(this, keys, snapshot.revision()));
        }
    }

    private static String resolveKey(StellnulaConfigChange change) {
        StellnulaConfigEntry entry = change.entry();
        if (entry.configKey() != null && !entry.configKey().isBlank()) {
            return entry.configKey();
        }
        return entry.configId();
    }
}
