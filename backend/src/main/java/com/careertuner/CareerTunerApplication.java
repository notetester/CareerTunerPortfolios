package com.careertuner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CareerTunerApplication {

    private static final String LANGCHAIN4J_HTTP_CLIENT_FACTORY = "langchain4j.http.clientBuilderFactory";
    private static final String SPRING_REST_CLIENT_FACTORY =
            "dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilderFactory";

    public static void main(String[] args) {
        if (System.getProperty(LANGCHAIN4J_HTTP_CLIENT_FACTORY) == null
                || System.getProperty(LANGCHAIN4J_HTTP_CLIENT_FACTORY).isBlank()) {
            System.setProperty(LANGCHAIN4J_HTTP_CLIENT_FACTORY, SPRING_REST_CLIENT_FACTORY);
        }
        SpringApplication.run(CareerTunerApplication.class, args);
    }

}
