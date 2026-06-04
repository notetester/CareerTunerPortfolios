package com.careertuner.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * springdoc OpenAPI 문서 메타데이터. Swagger UI: /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI careerTunerOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("CareerTuner API")
                .description("채용공고 기반 AI 취업 전략·가상 면접 준비 플랫폼 백엔드 API")
                .version("v0.1.0"));
    }
}
