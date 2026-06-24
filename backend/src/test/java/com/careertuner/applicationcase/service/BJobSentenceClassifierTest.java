package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BJobSentenceClassifierTest {

    private final BJobSentenceClassifier classifier = new BJobSentenceClassifier();

    @Test
    void classifiesCoreJobPostingSentences() {
        BJobSentenceClassifier.Classification result = classifier.classify("""
                회사 소개
                CareerTuner는 채용 준비를 돕는 문서 AI 서비스를 운영합니다.
                담당 업무
                담당 업무: Spring API를 개발하고 MySQL 서비스를 운영합니다.
                필수 조건
                필수 조건: Java와 Spring Boot 경험이 필요합니다.
                우대 조건
                우대 조건: Docker와 React 협업 경험을 우대합니다.
                전형 방법
                서류 전형 후 면접을 진행합니다.
                """);

        assertThat(result.textsByLabel(BJobSentenceClassifier.COMPANY_INFO))
                .anyMatch(sentence -> sentence.contains("문서 AI 서비스"));
        assertThat(result.textsByLabel(BJobSentenceClassifier.RESPONSIBILITY))
                .anyMatch(sentence -> sentence.contains("Spring API"));
        assertThat(result.textsByLabel(BJobSentenceClassifier.REQUIRED))
                .anyMatch(sentence -> sentence.contains("Java"));
        assertThat(result.textsByLabel(BJobSentenceClassifier.PREFERRED))
                .anyMatch(sentence -> sentence.contains("Docker"));
        assertThat(result.textsByLabel(BJobSentenceClassifier.APPLICATION_INFO))
                .anyMatch(sentence -> sentence.contains("면접"));
    }
}
