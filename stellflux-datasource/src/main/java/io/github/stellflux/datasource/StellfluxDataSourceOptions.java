package io.github.stellflux.datasource;

/** DataSource 客户端配置。 */
public class StellfluxDataSourceOptions {

    private String url;

    private String username;

    private String password;

    private int loginTimeoutSeconds;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getLoginTimeoutSeconds() {
        return loginTimeoutSeconds;
    }

    public void setLoginTimeoutSeconds(int loginTimeoutSeconds) {
        this.loginTimeoutSeconds = loginTimeoutSeconds;
    }
}
