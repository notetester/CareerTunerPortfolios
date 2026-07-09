package com.careertuner.interview.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 면접 자율 에이전트 설정.
 * planner=rule 이 운영 기본값이고, planner=llm 으로 켜면 LLM Planner 가 다음 액션을 결정한다(시연 모드).
 * LLM 호출이 실패하면 규칙 정책으로 자동 폴백한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.interview.agent")
public class InterviewAgentProperties {

    /** rule | llm */
    private String planner = "rule";
    /** 무한 루프 방지 상한. */
    private int maxTurns = 6;

    public boolean isLlmPlanner() {
        return "llm".equalsIgnoreCase(planner);
    }
}
