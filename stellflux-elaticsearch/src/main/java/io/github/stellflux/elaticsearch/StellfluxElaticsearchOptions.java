package io.github.stellflux.elaticsearch;

import java.util.ArrayList;
import java.util.List;

/** Elaticsearch 客户端配置。 */
public class StellfluxElaticsearchOptions {

    private List<String> endpoints = new ArrayList<>(List.of("http://localhost:9200"));

    private String username;

    private String password;

    private String apiKey;

    private String pathPrefix;

    private int connectTimeoutMillis = 1000;

    private int socketTimeoutMillis = 30000;

    private int connectionRequestTimeoutMillis;

    private boolean compressionEnabled;

    private boolean metaHeaderEnabled = true;

    public List<String> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<String> endpoints) {
        this.endpoints = endpoints == null ? new ArrayList<>() : new ArrayList<>(endpoints);
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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getSocketTimeoutMillis() {
        return socketTimeoutMillis;
    }

    public void setSocketTimeoutMillis(int socketTimeoutMillis) {
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    public int getConnectionRequestTimeoutMillis() {
        return connectionRequestTimeoutMillis;
    }

    public void setConnectionRequestTimeoutMillis(int connectionRequestTimeoutMillis) {
        this.connectionRequestTimeoutMillis = connectionRequestTimeoutMillis;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    public boolean isMetaHeaderEnabled() {
        return metaHeaderEnabled;
    }

    public void setMetaHeaderEnabled(boolean metaHeaderEnabled) {
        this.metaHeaderEnabled = metaHeaderEnabled;
    }
}
