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
    /** 범용 모델. 태깅·면접추출·신고분류가 쓴다. */
    private String model = "gemma4";
    /** 텍스트 검열(abuse/spam/ad/normal 판정) 전용 파인튜닝 모델.
     *  careertuner-mod 는 Modelfile 에 num_predict=64 가 박혀 있어 짧은 판정 JSON 에만 적합하다 —
     *  긴 JSON 을 뽑는 태깅/면접추출이 같은 모델을 쓰면 출력이 잘리므로 {@link #model} 과 분리한다.
     *  미설치 환경이면 검열 호출이 실패하고 게이트웨이가 Claude/OpenAI 로 폴백한다. */
    private String moderationModel = "careertuner-mod";
    /** 이미지(vision) 검열 전용 모델. gemma4 는 이미지 속 광고/텍스트 인식이 약해 전용 VL 모델을 쓴다.
     *  미설치 환경이면 vision 호출이 실패하고 게이트웨이가 Claude/OpenAI vision 으로 폴백한다. */
    private String visionModel = "qwen2.5vl:7b";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(30);

    /**
     * Ollama 호출 1건(재시도·백오프 포함)의 총 시간예산. 0 또는 음수 = 무제한(OFF, 기본).
     * 이 설정은 ai.ollama 를 쓰는 5개 클라이언트(moderation/chatbot/embedding/admin-ticket/admin-faq)에 공통 적용.
     */
    private Duration totalTimeBudget = Duration.ZERO;
}
