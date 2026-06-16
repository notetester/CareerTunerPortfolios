package com.careertuner.community.moderation.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Ollama 로컬 LLM 연동 설정.
 * 팀장 소유 ai/ 공통 패키지를 건드리지 않기 위해 community 도메인 내에 임시 배치.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.ollama")
public class OllamaProperties {

    private String baseUrl = "http://localhost:11434";
    private String model = "gemma4";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(30);
}
