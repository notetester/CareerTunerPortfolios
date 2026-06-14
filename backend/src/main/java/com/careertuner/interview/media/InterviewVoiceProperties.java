package com.careertuner.interview.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 음성 분석(Inworld STT voice profiling) 설정.
 * 기본값은 코드에 두고 env 로 덮어쓴다 — application.yaml(공통 영역)은 건드리지 않는다.
 * 키 바인딩: run-local.bat 에서 INWORLD_API_KEY → CAREERTUNER_INTERVIEW_VOICE_API_KEY 로 미러링.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.interview.voice")
public class InterviewVoiceProperties {

    /** Inworld API 키 (포털이 주는 Basic base64 서명 그대로). 비어 있으면 감정 분석은 건너뛴다. */
    private String apiKey = "";

    private String baseUrl = "https://api.inworld.ai";

    /** Inworld STT 모델. 한국어 전사 지원 모델. */
    private String modelId = "inworld/inworld-stt-1";

    /** voice profile 카테고리별 상위 라벨 수. */
    private int profileTopN = 3;

    /** Inworld 호출 타임아웃(초). */
    private int timeoutSeconds = 30;

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
