package com.careertuner.support.chatbot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.chatbot")
public class ChatbotProperties {

    /** 임베딩 모델명 (bge-m3) */
    private String embeddingModel = "bge-m3";

    /** 답변 생성 모델명 (gemma4) */
    private String chatModel = "gemma4";

    /** 코사인 유사도 임계값 (미만이면 "관련 FAQ 없음"). 검색 후보 컷오프. */
    private double similarityThreshold = 0.5;

    /**
     * FAQ 임베딩 게이트 임계값. 질문 원문 top-1 FAQ 코사인이 이 값 이상이면
     * 에이전트 우회·즉답(결정적 FAQ 경로). similarityThreshold(검색 후보 컷오프)와
     * <b>물리적으로 별개 키</b> — 게이트만 독립 튜닝 가능.
     */
    private double faqGateThreshold = 0.35;

    /** 검색 결과 상위 K개 */
    private int topK = 3;

    /** 통합 라우팅 경계 폭(deadband). |intakeScore - faqScore| 가 이 값 미만이면 화행 분류로 판정(그 이상은 argmax 결정적). */
    private double routeBoundary = 0.10;

    /**
     * 약신호 게이트. faqScore·intakeScore 가 <b>둘 다</b> 이 값 미만이면 "FAQ도 아니고 명확한 의도도
     * 없는" 미달로 보고 정중한 되묻기(fallback)로 끊는다. 코퍼스 밖·무의미 입력 차단용.
     * 실측(2026-06-25): 비FAQ faqScore ≤0.45 / 진짜 FAQ ≥0.57 로 분리 → 0.52 채택(B/C 회귀 무손실).
     */
    private double weakGate = 0.52;

    /** 화행 분류(QUESTION/COMMAND)에 쓰는 모델. ③ 인테이크 에이전트와 동일한 qwen3:8b. */
    private String speechActModel = "qwen3:8b";
}
