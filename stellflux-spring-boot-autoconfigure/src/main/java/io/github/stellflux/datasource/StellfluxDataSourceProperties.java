package io.github.stellflux.datasource;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** DataSource properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.datasource")
public class StellfluxDataSourceProperties {

    /** JDBC URL. */
    private String url;

    /** Database username. */
    private String username;

    /** Database password. */
    private String password;

    /** Login timeout in seconds. */
    private int loginTimeoutSeconds;

    /**
     * 转换为核心 DataSource 配置。
     *
     * @return DataSource 配置
     */
    public StellfluxDataSourceOptions toOptions() {
        StellfluxDataSourceOptions options = new StellfluxDataSourceOptions();
        options.setUrl(this.url);
        options.setUsername(this.username);
        options.setPassword(this.password);
        options.setLoginTimeoutSeconds(this.loginTimeoutSeconds);
        return options;
    }
}
