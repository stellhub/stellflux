package io.github.stellflux.stellorbit;

import io.github.stellnula.client.StellnulaClient;
import io.github.stellnula.config.StellnulaConfigEntry;
import io.github.stellnula.config.StellnulaListenerRegistration;
import io.github.stellnula.config.StellnulaSnapshot;
import io.github.stellorbit.client.StellorbitClientException;
import io.github.stellorbit.client.rule.GovernanceRule;
import io.github.stellorbit.client.rule.GovernanceRuleParser;
import io.github.stellorbit.client.rule.GovernanceRuleRegistry;
import io.github.stellorbit.client.source.GovernanceRuleSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 复用 Stellflux 已装配 StellnulaClient 的治理规则源。 */
public class StellfluxStellnulaGovernanceRuleSource implements GovernanceRuleSource {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellnulaGovernanceRuleSource.class.getName());

    private final StellnulaClient stellnulaClient;
    private final StellfluxStellorbitClientOptions options;
    private final GovernanceRuleParser parser;
    private final AtomicReference<GovernanceRuleRegistry> registry =
            new AtomicReference<>(GovernanceRuleRegistry.empty());
    private volatile StellnulaListenerRegistration listenerRegistration;

    public StellfluxStellnulaGovernanceRuleSource(
            StellnulaClient stellnulaClient, StellfluxStellorbitClientOptions options) {
        this(stellnulaClient, options, new GovernanceRuleParser());
    }

    public StellfluxStellnulaGovernanceRuleSource(
            StellnulaClient stellnulaClient,
            StellfluxStellorbitClientOptions options,
            GovernanceRuleParser parser) {
        this.stellnulaClient =
                Objects.requireNonNull(stellnulaClient, "stellnulaClient must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
    }

    /** 启动规则源并加载初始治理规则。 */
    @Override
    public void start() {
        try {
            replaceRegistry(stellnulaClient.snapshot());
            if (options.isWatchEnabled()) {
                listenerRegistration =
                        stellnulaClient.listen(event -> replaceRegistry(event.currentSnapshot()), true);
            }
        } catch (RuntimeException ex) {
            if (options.isFailFastOnBootstrap()) {
                throw new StellorbitClientException("Failed to bootstrap StellOrbit governance rules", ex);
            }
            LOGGER.log(Level.WARNING, ex, () -> "Failed to bootstrap StellOrbit governance rules");
        }
    }

    /** 返回当前治理规则注册表。 */
    @Override
    public GovernanceRuleRegistry registry() {
        return registry.get();
    }

    /** 关闭规则监听，不关闭共享 StellnulaClient。 */
    @Override
    public void close() {
        StellnulaListenerRegistration registration = listenerRegistration;
        if (registration != null) {
            registration.close();
        }
    }

    private void replaceRegistry(StellnulaSnapshot snapshot) {
        if (snapshot == null) {
            registry.set(GovernanceRuleRegistry.empty());
            return;
        }
        GovernanceRuleRegistry previous = registry.get();
        GovernanceRuleRegistry next = parse(snapshot, previous);
        registry.set(next);
    }

    private GovernanceRuleRegistry parse(
            StellnulaSnapshot snapshot, GovernanceRuleRegistry previous) {
        GovernanceRuleRegistry previousRegistry =
                previous == null ? GovernanceRuleRegistry.empty() : previous;
        if (snapshot.entries().isEmpty()) {
            return GovernanceRuleRegistry.of(snapshot.revision(), snapshot.checksum(), List.of());
        }

        Map<String, List<GovernanceRule>> previousRules = indexPreviousRules(previousRegistry);
        List<GovernanceRule> parsed = new ArrayList<>();
        boolean hasDeletedEntry = false;
        boolean hasInvalidEntry = false;

        for (StellnulaConfigEntry entry : snapshot.entries()) {
            if (entry.deleted()) {
                hasDeletedEntry = true;
                removePreviousRules(previousRules, entry);
                continue;
            }
            try {
                List<GovernanceRule> rules = parser.parseAll(entry, snapshot.checksum());
                parsed.addAll(rules);
                removePreviousRules(previousRules, entry);
            } catch (RuntimeException ex) {
                hasInvalidEntry = true;
                List<GovernanceRule> fallback = removePreviousRules(previousRules, entry);
                if (!fallback.isEmpty()) {
                    parsed.addAll(fallback);
                } else {
                    LOGGER.log(
                            Level.WARNING,
                            "Skip invalid governance rule {0}: {1}",
                            new Object[] {entry.configId(), ex.getMessage()});
                }
            }
        }

        if (parsed.isEmpty()
                && hasInvalidEntry
                && !hasDeletedEntry
                && !previousRegistry.rules().isEmpty()) {
            LOGGER.warning("All governance rules failed to parse, keep last-known-good registry");
            return previousRegistry;
        }
        return GovernanceRuleRegistry.of(snapshot.revision(), snapshot.checksum(), parsed);
    }

    private Map<String, List<GovernanceRule>> indexPreviousRules(GovernanceRuleRegistry previous) {
        Map<String, List<GovernanceRule>> indexed = new LinkedHashMap<>();
        for (GovernanceRule rule : previous.rules()) {
            indexPreviousRule(indexed, rule.ruleId(), rule);
            indexPreviousRule(indexed, rule.configKey(), rule);
        }
        return indexed;
    }

    private void indexPreviousRule(
            Map<String, List<GovernanceRule>> indexed, String key, GovernanceRule rule) {
        if (key == null || key.isBlank()) {
            return;
        }
        indexed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rule);
    }

    private List<GovernanceRule> removePreviousRules(
            Map<String, List<GovernanceRule>> previousRules, StellnulaConfigEntry entry) {
        Set<GovernanceRule> removed = new LinkedHashSet<>();
        removePreviousRules(previousRules, entry.configId(), removed);
        removePreviousRules(previousRules, entry.configKey(), removed);
        return List.copyOf(removed);
    }

    private void removePreviousRules(
            Map<String, List<GovernanceRule>> previousRules, String key, Set<GovernanceRule> removed) {
        if (key == null || key.isBlank()) {
            return;
        }
        List<GovernanceRule> rules = previousRules.remove(key);
        if (rules != null) {
            removed.addAll(rules);
        }
    }
}
