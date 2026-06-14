package io.github.stellflux.examples.httpclient;

import io.github.stellflux.http.client.StellfluxHttpClient;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** HTTP 客户端示例启动逻辑。 */
@Component
public class HttpClientExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(HttpClientExampleRunner.class.getName());

    private final StellfluxHttpClient demoHttpClient;
    private final Environment environment;

    public HttpClientExampleRunner(
            @Qualifier("demoHttpClient") StellfluxHttpClient demoHttpClient, Environment environment) {
        this.demoHttpClient = demoHttpClient;
        this.environment = environment;
    }

    /**
     * 启动后输出示例客户端信息，并按需发起演示请求。
     *
     * @param args 启动参数
     * @throws IOException 请求执行异常
     */
    @Override
    public void run(ApplicationArguments args) throws IOException {
        HttpUrl url = demoHttpClient.buildUrl("/api/example/hello");
        LOGGER.info(() -> "Prepared StellfluxHttpClient targetUrl=" + url);

        boolean invokeOnStartup =
                environment.getProperty("example.http.client.invoke-on-startup", Boolean.class, false);
        if (!invokeOnStartup) {
            return;
        }

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = demoHttpClient.newCall(request).execute()) {
            LOGGER.info(() -> "HTTP example response status=" + response.code());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "HTTP example request failed", ex);
        }
    }
}
