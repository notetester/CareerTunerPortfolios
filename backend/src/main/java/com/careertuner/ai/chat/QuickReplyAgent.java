package com.careertuner.ai.chat;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * quickReplies(좁혀가기 칩) 보조 생성기. <b>툴·메모리 없음</b> → 구조화 출력이 툴과 충돌하지 않는다.
 * 실패는 보조 기능이므로 호출부에서 graceful 하게 무시(빈 리스트)한다.
 *
 * <p><b>raw String 반환인 이유</b>: 점수(relevance/importance)까지 받으려면 모델이 JSON 배열을 내야 하는데,
 * LangChain4j 의 {@code List<POJO>} 자동 역직렬화는 8B 가 흘리는 포맷(칩마다 배열 따로 등)에서 깨진다.
 * 그래서 raw 문자열로 받아 {@link QuickReplyParser} 가 견고하게 파싱하고, 선별·점수는 서버가 한다.
 */
public interface QuickReplyAgent {

    @SystemMessage("""
        너는 취업 준비 챗봇의 후속 제안(칩) 생성기다.
        사용자 메시지로 주어지는 맥락(프로필/대화맥락)을 보고, 사용자가 *직접 누르고 싶어질*
        후속 질문/요청 칩 후보를 만든다.

        규칙:
        - 메아리 금지: 챗봇이 방금 한 말을 그대로 되묻지 마라.
        - 맥락 속 사용자 상황에 비춰, 사용자 입장에서 다음에 묻고 싶을 법한 것으로 만든다.
        - 후보는 4~7개 넉넉히 생성한다. 최종 선택은 시스템이 하므로 억지로 채우지 마라.
        - 각 칩 문구는 12자 이내의 짧은 한국어.
        - 각 후보에 두 점수를 매긴다:
          - relevance: 지금 대화 흐름과 얼마나 맞는가 (0~100 정수)
          - importance: 사용자의 목표 진전에 얼마나 중요한가 (0~100 정수)

        출력 형식 (정확히 이대로):
        chips 코드블록 하나만 출력한다. 모든 칩을 하나의 JSON 배열 안에 객체로 담는다.
        - 배열은 정확히 1개다. 칩마다 배열이나 코드블록을 따로 만들지 마라.
        - 점수는 따옴표 없는 정수로 쓴다. ("85"가 아니라 85)
        - 코드블록 밖에는 아무것도 쓰지 마라.
        - 마땅한 후속이 없으면 빈 배열 [] 를 담는다.
        ※ 아래 예시 문구는 구조 참고용 더미다. 절대 그대로 쓰지 말고 이 사용자 맥락에서 새로 만들어라.
        ```chips
        [{"text":"더미문구하나","relevance":90,"importance":80},{"text":"더미문구둘","relevance":75,"importance":70}]
        ```
        """)
    String suggest(@UserMessage String context);
}
