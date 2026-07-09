package com.careertuner.ai.intake;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 인테이크(AI 오케스트레이터 시작) 전용 대화 에이전트.
 *
 * <p>커뮤니티 챗봇({@code CommunityChatAgent})과 동일한 LangChain4j {@code @AiService} 패턴을 복제하되,
 * 별도 에이전트·툴·컨트롤러로 완전히 분리한다(기존 커뮤니티 챗봇 무수정).</p>
 *
 * <p><b>왜 String 반환인가</b>: 어제 실측 — qwen3:8b 는 구조화 POJO/JSON 반환을 강제하면 tool_call 을
 * 건너뛰고 JSON 만 즉시 뱉는 충돌이 있다. 따라서 반환 타입은 String 으로 두고, 슬롯 값(caseId·mode)은
 * LLM 자유생성 JSON 이 아니라 툴 호출 결과를 코드가 검증·확정한다
 * ({@link IntakeTools} + {@link IntakeSlotTrace}, "슬롯 접지").</p>
 */
public interface IntakeChatAgent {

    @SystemMessage(fromResource = "prompts/intake-chat-system.txt")
    String chat(@MemoryId Long conversationId, @UserMessage String message);
}
