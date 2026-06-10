package com.careertuner.interview.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 실시간 음성 면접관(OpenAI Realtime) 설정.
 * API 키는 공통 {@code careertuner.openai.api-key} 를 재사용하고, 모델/보이스만 면접 도메인에서 관리한다.
 * (공통 영역을 건드리지 않도록 별도 prefix 로 분리 — 이후 팀 합의 시 ai/common 으로 승격 가능)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.interview.realtime")
public class InterviewRealtimeProperties {

    /** Realtime 전용 모델. 실제 사용 가능한 모델 id 로 env override 할 것. */
    private String model = "gpt-4o-realtime-preview";

    /** 면접관 음성. (alloy/verse/echo/shimmer 등) */
    private String voice = "alloy";
}
