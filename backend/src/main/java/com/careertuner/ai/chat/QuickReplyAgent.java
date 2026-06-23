package com.careertuner.ai.chat;

import java.util.List;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * quickReplies(좁혀가기 칩) 보조 생성기. <b>툴·메모리 없음</b> → 구조화 출력이 툴과 충돌하지 않는다.
 * 실패는 보조 기능이므로 호출부에서 graceful 하게 무시(빈 리스트)한다.
 */
public interface QuickReplyAgent {

    @SystemMessage("""
        너는 취업 사이트 챗봇의 후속 제안 생성기다.
        직전 대화 맥락을 보고, 사용자가 이어서 누를 만한 짧은 후속 질문/요청을 1~3개 만든다.
        - 각 항목은 12자 이내의 짧은 한국어 문구. (예: "신입 위주로", "다른 직무", "이 글 요약해줘")
        - 마땅한 후속이 없으면 빈 배열.
        - 설명 없이 칩 문구만.
        """)
    List<String> suggest(@UserMessage String context);
}
