package com.careertuner.interview.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 면접 답변 평가 모델 설정.
 * provider=openai(기본/폴백) | oss(자체 파인튜닝 모델, vLLM/TGI 의 OpenAI 호환 /v1/chat/completions).
 * oss 는 GPU 임대로 학습한 모델을 서빙해 base-url 로 연결한다(로드맵 5장).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.interview.eval")
public class InterviewEvalProperties {

    /** openai | oss */
    private String provider = "openai";
    /** 자체 모델 서빙 엔드포인트 (예: http://localhost:8000/v1). 비어 있으면 oss 호출은 실패한다. */
    private String baseUrl = "";
    /** 자체 모델 id (예: careertuner-interview-qwen2.5-7b-lora). */
    private String model = "";
    /** 자체 서버 인증 키(필요 시). */
    private String apiKey = "";
    private Duration timeout = Duration.ofSeconds(60);
    /**
     * 호출 1건의 총 시간예산. 0 또는 음수 = 무제한(OFF, 기본) — per-request timeout 만 적용.
     * 양수면 요청 타임아웃이 min(timeout, 예산)으로 절삭된다.
     */
    private Duration totalTimeBudget = Duration.ZERO;

    public boolean isOss() {
        return "oss".equalsIgnoreCase(provider);
    }

    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
