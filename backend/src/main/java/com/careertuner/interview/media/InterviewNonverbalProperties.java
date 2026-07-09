package com.careertuner.interview.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 비언어(음성/영상) 자체 추론 서버(Python FastAPI, ADR-006) 호출 설정.
 * 기본값은 코드에 두고 env 로 덮어쓴다 — application.yaml(공통 영역)은 건드리지 않는다.
 * 서버 미기동 시 호출은 실패하며, 프런트는 {@code capabilities} 로 사전 판단한다.
 *
 * <p>env override:
 * {@code CAREERTUNER_INTERVIEW_NONVERBAL_SERVE_URL}, {@code ..._ENABLED}, {@code ..._TIMEOUT_SECONDS}
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.interview.nonverbal")
public class InterviewNonverbalProperties {

    /** 자체 추론 서버 base URL (로컬 기본). 운영/원격은 env 로 교체. */
    private String serveUrl = "http://127.0.0.1:8500";

    /** 추론 서버 사용 여부. false 면 프런트는 기존 브라우저 점수로 폴백한다. */
    private boolean enabled = true;

    /** 호출 타임아웃(초). 오디오 길이에 따른 ffmpeg 변환 + 추론 시간을 고려. */
    private int timeoutSeconds = 60;

    public boolean configured() {
        return enabled && serveUrl != null && !serveUrl.isBlank();
    }
}
