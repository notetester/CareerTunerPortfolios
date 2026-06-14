package com.careertuner.interview.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 아바타 화상 면접(HeyGen LiveAvatar) 설정.
 * 기본값은 코드에 두고 env 로 덮어쓴다 — application.yaml(공통 영역)은 건드리지 않는다.
 * 키 바인딩: run-local.bat 에서 HEYGEN_API_KEY → CAREERTUNER_INTERVIEW_AVATAR_API_KEY 로 미러링.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.interview.avatar")
public class InterviewAvatarProperties {

    /** LiveAvatar API 키 (app.liveavatar.com/developers 발급). 비어 있으면 아바타 면접 비활성. */
    private String apiKey = "";

    private String baseUrl = "https://api.liveavatar.com";

    /** 사용할 아바타 id(uuid). 비어 있으면 바디에서 생략 (sandbox 는 Wayne 고정). */
    private String avatarId = "";

    /** 아바타 음성 id. 비어 있으면 아바타 기본 음성. */
    private String voiceId = "";

    /** 아바타 발화 언어. 한국어 FULL 모드 지원은 실키로 /v1/languages 확인 필요. */
    private String language = "ko";

    /**
     * 샌드박스 모드 — 크레딧 소모 0, Wayne 아바타 고정, 세션 약 1분 제한.
     * 기본 true 로 두어 키만 넣으면 과금 없이 동작 확인 가능. 실연 시 env 로 false.
     */
    private boolean sandbox = true;

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
