package io.github.stellflux.stellnula;

import java.util.Set;
import org.springframework.context.ApplicationEvent;

/** Stellnula 配置中心变更事件。 */
public class StellfluxStellnulaConfigChangeEvent extends ApplicationEvent {

    private final Set<String> keys;
    private final long revision;

    public StellfluxStellnulaConfigChangeEvent(Object source, Set<String> keys, long revision) {
        super(source);
        this.keys = Set.copyOf(keys);
        this.revision = revision;
    }

    /**
     * 返回本次变更的配置键。
     *
     * @return 配置键集合
     */
    public Set<String> getKeys() {
        return this.keys;
    }

    /**
     * 返回变更后的 revision。
     *
     * @return 配置 revision
     */
    public long getRevision() {
        return this.revision;
    }
}
