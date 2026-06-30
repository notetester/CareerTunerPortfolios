package com.careertuner.profile.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "careertuner.profile.ai.finetuned")
public class FineTunedProfileAiProperties {

    /**
     * 자체 파인튜닝 모델 서버 사용 여부입니다.
     * false이면 기존 OpenAI/규칙 기반 경로만 사용합니다.
     */
    private boolean enabled = false;

    /**
     * 학습 PC에서 실행 중인 FastAPI 모델 서버 주소입니다.
     * 예: http://localhost:8000
     */
    private String baseUrl = "";

    /**
     * ai_usage_log와 화면 메타에 남길 모델 이름입니다.
     */
    private String model = "qwen3-profile-lora-v3";

    /**
     * 모델 추론은 일반 API보다 오래 걸릴 수 있어 기본 timeout을 길게 둡니다.
     */
    private Duration timeout = Duration.ofSeconds(60);

    public boolean configured() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }
}
