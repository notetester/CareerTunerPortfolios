package com.careertuner.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class TossPaymentPropertiesAwsConfigTest {

    @Test
    void awsProfileUsesProductionPaymentReturnUrls() throws Exception {
        MutablePropertySources propertySources = new MutablePropertySources();
        for (PropertySource<?> propertySource : new YamlPropertySourceLoader()
                .load("application-aws", new ClassPathResource("application-aws.yaml"))) {
            propertySources.addLast(propertySource);
        }

        PropertySourcesPropertyResolver propertyResolver = new PropertySourcesPropertyResolver(propertySources);
        Binder binder = new Binder(
                ConfigurationPropertySources.from(propertySources),
                value -> value instanceof String text ? propertyResolver.resolvePlaceholders(text) : value);
        TossPaymentProperties properties = binder
                .bind("careertuner.toss.payments", Bindable.of(TossPaymentProperties.class))
                .orElseThrow(() -> new AssertionError("AWS Toss payment configuration was not bound"));

        assertThat(properties.getSuccessUrl()).isEqualTo("https://careertuner.example.com/billing/success");
        assertThat(properties.getFailUrl()).isEqualTo("https://careertuner.example.com/billing/fail");
    }

    @Test
    void productionComposeForcesPaymentReturnUrls() throws Exception {
        String compose = Files.readString(Path.of("../docker-compose.prod.yml"));

        assertThat(compose)
                .contains("CAREERTUNER_TOSS_PAYMENTS_SUCCESS_URL: https://careertuner.example.com/billing/success")
                .contains("CAREERTUNER_TOSS_PAYMENTS_FAIL_URL: https://careertuner.example.com/billing/fail");
    }
}
