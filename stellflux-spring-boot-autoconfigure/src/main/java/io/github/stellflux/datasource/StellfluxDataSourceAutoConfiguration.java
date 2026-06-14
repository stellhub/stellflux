package io.github.stellflux.datasource;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** DataSource auto configuration. */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({MysqlDataSource.class, DataSource.class, StellfluxDataSourceFactory.class})
@ConditionalOnBean(OpenTelemetry.class)
@EnableConfigurationProperties(StellfluxDataSourceProperties.class)
public class StellfluxDataSourceAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxDataSourceAutoConfiguration.class.getName());

    /**
     * 注册 DataSource 工厂。
     *
     * @param openTelemetry OpenTelemetry 实例
     * @return DataSource 工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxDataSourceFactory stellfluxDataSourceFactory(OpenTelemetry openTelemetry) {
        return new StellfluxDataSourceFactory(openTelemetry);
    }

    /**
     * 注册带 OpenTelemetry 的 MySQL DataSource。
     *
     * @param factory DataSource 工厂
     * @param properties DataSource 配置
     * @return 标准 DataSource
     */
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(prefix = "stellflux.datasource", name = "url")
    public DataSource stellfluxDataSource(
            StellfluxDataSourceFactory factory, StellfluxDataSourceProperties properties) {
        return factory.createDataSource(properties.toOptions());
    }

    /**
     * 记录 DataSource starter 启动日志。
     *
     * @param properties DataSource 配置
     * @param moduleInfoMeterProvider 模块指标注册器
     * @return 启动日志探针
     */
    @Bean("stellfluxDataSourceStarterStartupLogger")
    public SmartInitializingSingleton stellfluxDataSourceStarterStartupLogger(
            StellfluxDataSourceProperties properties,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule("stellflux-datasource", StellfluxDataSourceFactory.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-datasource started successfully"
                                    + ", configured="
                                    + (properties.getUrl() != null && !properties.getUrl().isBlank())
                                    + ", telemetry=true");
        };
    }
}
