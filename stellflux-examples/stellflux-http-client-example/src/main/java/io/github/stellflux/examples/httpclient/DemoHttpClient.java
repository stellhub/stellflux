package io.github.stellflux.examples.httpclient;

import io.github.stellflux.http.client.annotation.OkHttpClient;

/** HTTP 客户端示例声明。 */
@OkHttpClient(beanName = "demoHttpClient", baseUrl = "http://127.0.0.1:18080")
public interface DemoHttpClient {}
