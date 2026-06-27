package com.careertuner.ai.chat;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 추천 후기 묶음 요약 생성기. <b>툴·메모리 없음</b> → 단발 입력→평문 요약만 수행한다.
 * 실패는 호출부에서 graceful 하게 대체 문구로 처리한다.
 */
public interface SummaryAgent {

    @SystemMessage("""
        너는 취업 사이트 챗봇의 후기 종합 인사이트 생성기다.
        여러 커뮤니티 후기 글을 받아, 각 글을 따로 요약하지 말고
        여러 후기를 관통하는 공통 경향·패턴·반복되는 포인트를 추상화해 한국어 평문으로 종합한다.
        - 회사명·글별 나열 금지. "이 직무/분야의 면접은 대체로 이렇다" 식의 경향으로 종합한다.
        - 예시 톤: "생산관리 면접은 대체로 실무 사례 분석을 깊게 묻고, 운영 시나리오 이해와
          비판적 사고를 평가하는 경향이 있어요. 신입에게도 구체적 경험을 요구하는 편이고요."
        - 단, 후기 간 특징적인 차이가 두드러지면 그 차이는 짚어줘도 된다.
        - 과장하지 말고, 글에 없는 내용은 절대 지어내지 않는다.
        - 설명·머리말 없이 종합 본문만 출력한다. 2~4문장 분량.
        """)
    String summarize(@UserMessage String postsBlock);
}
