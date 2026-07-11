package com.careertuner.ai.common.model;

/**
 * 폴백 체인의 <b>논리 tier</b>. 도메인별 물리 provider 는 다르다 — CAREERTUNER 는 B 의 LOCAL(Ollama),
 * A 의 FineTuned 모델서버, C·F 의 자체 OSS/Ollama 로 각각 매핑된다. 디스패처가 이 논리 tier 를 자기 도메인의
 * 실제 provider 호출로 해석한다.
 */
public enum AiProviderTier {
    CAREERTUNER,
    CLAUDE,
    OPENAI
}
