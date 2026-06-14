package io.github.stellflux.examples.stellnula;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StellfluxStellnulaExampleApplication {

    public static void main(String[] args) {
        // only for local mode
        System.setProperty("log.stdout", "true");
        SpringApplication.run(StellfluxStellnulaExampleApplication.class, args);
    }
}
