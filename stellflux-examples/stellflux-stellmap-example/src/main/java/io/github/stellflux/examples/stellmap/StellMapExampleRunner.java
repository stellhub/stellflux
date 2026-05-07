package io.github.stellflux.examples.stellmap;

import io.github.stellmap.StellMapClient;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** StellMap 示例启动逻辑。 */
@Component
public class StellMapExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(StellMapExampleRunner.class.getName());

    private final ObjectProvider<StellMapClient> stellMapClientProvider;
    private final Environment environment;

    public StellMapExampleRunner(
            ObjectProvider<StellMapClient> stellMapClientProvider, Environment environment) {
        this.stellMapClientProvider = stellMapClientProvider;
        this.environment = environment;
    }

    /**
     * 启动后输出 StellMap 示例当前状态。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        StellMapClient stellMapClient = stellMapClientProvider.getIfAvailable();
        if (stellMapClient == null) {
            LOGGER.info(
                    "StellMap client is not initialized. Configure stellflux.stellmap.base-url to enable it.");
            return;
        }

        String baseUrl = environment.getProperty("stellflux.stellmap.base-url", "<unset>");
        LOGGER.info(() -> "StellMap client initialized with baseUrl=" + baseUrl);
    }
}
