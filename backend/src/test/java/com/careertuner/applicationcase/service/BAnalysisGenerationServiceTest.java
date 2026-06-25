package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.domain.ApplicationCase;

import tools.jackson.databind.ObjectMapper;

class BAnalysisGenerationServiceTest {

    @Test
    void localLlmDisabledUsesSelfRulesWithoutCallingOllama() {
        BAnalysisProperties properties = new BAnalysisProperties();
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().usage().model()).isEqualTo(BAnalysisGenerationService.SELF_RULES_MODEL);
        assertThat(result.payload().requiredSkills()).contains("Java", "Spring Boot", "MySQL");
        verify(localLlmClient, never()).chat(anyString(), anyString(), any());
    }

    @Test
    void localLlmValidJsonProducesQwenPayload() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "MID",
                  "requiredSkills": ["Java", "Spring Boot"],
                  "preferredSkills": ["Docker"],
                  "duties": "Spring API 개발과 운영",
                  "qualifications": "Java와 Spring Boot 경험",
                  "difficulty": "NORMAL",
                  "summary": "백엔드 개발자를 위한 공고 분석 요약입니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java와 Spring Boot 경험"}],
                  "ambiguousConditions": [{"condition":"salary","assumption":"not specified"}]
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().usage().model()).isEqualTo("qwen-test");
        assertThat(result.payload().summary()).contains("백엔드 개발자");
        assertThat(result.payload().requiredSkills()).contains("Java", "Spring Boot");
    }

    @Test
    void localLlmExperienceLevelIsCorrectedFromStatedYears() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "JUNIOR",
                  "requiredSkills": ["Java", "Spring Boot"],
                  "preferredSkills": [],
                  "duties": "Spring API 개발과 운영",
                  "qualifications": "Java와 Spring Boot 경험",
                  "difficulty": "NORMAL",
                  "summary": "백엔드 개발자를 위한 공고 분석 요약입니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java"}],
                  "ambiguousConditions": [{"condition":"salary","assumption":"not specified"}]
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(
                applicationCase(), "Java 백엔드 개발 경력 5년 이상. Spring Boot 경험 필수.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().experienceLevel()).isEqualTo("SENIOR");
    }

    @Test
    void localLlmRequiredSkillsDropBusinessSentences() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "MID",
                  "requiredSkills": ["Java", "Spring Boot", "결제 시스템 백엔드 API 설계 및 개발"],
                  "preferredSkills": [],
                  "duties": "결제 시스템 백엔드 API 설계 및 개발",
                  "qualifications": "Java와 Spring Boot 경험",
                  "difficulty": "NORMAL",
                  "summary": "결제 시스템 백엔드 개발자 공고 분석 요약입니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java"}],
                  "ambiguousConditions": [{"condition":"salary","assumption":"not specified"}]
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(
                applicationCase(), "결제 시스템 백엔드 API 설계 및 개발. Java, Spring Boot 경험 필수.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().requiredSkills()).contains("Java", "Spring Boot");
        assertThat(result.payload().requiredSkills()).doesNotContain("결제 시스템 백엔드 API 설계 및 개발");
    }

    @Test
    void localLlmInvalidJsonFallsBackToSelfRules() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("{}");
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result = service.generateCompanyAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isTrue();
        assertThat(result.fallbackAttemptedModel()).isEqualTo("qwen-test");
        assertThat(result.fallbackReason()).contains("fallback to self-rules-v1");
        assertThat(result.payload().usage().model()).isEqualTo(BAnalysisGenerationService.SELF_RULES_MODEL);
        assertThat(result.payload().companySummary()).contains("Acme");
    }

    private static BAnalysisGenerationService service(BAnalysisProperties properties, BLocalLlmClient localLlmClient) {
        return new BAnalysisGenerationService(
                properties,
                localLlmClient,
                new BJobSentenceClassifier(),
                new ObjectMapper());
    }

    private static ApplicationCase applicationCase() {
        return ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Acme")
                .jobTitle("Backend Engineer")
                .status("DRAFT")
                .build();
    }

    private static String postingText() {
        return """
                Acme is hiring a Backend Engineer.
                Responsibilities: build Spring Boot APIs, operate MySQL services, and improve Docker deployment.
                Qualifications: Java, Spring Boot, MyBatis, MySQL, REST, Docker, Testing.
                Preferred: React and TypeScript collaboration experience.
                """;
    }
}
