package com.careertuner.ai.intake;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.ai.autoprep.AutoPrepIntakeService;
import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.chat.MessageSanitizer;
import com.careertuner.ai.intake.dto.IntakeAskResponse;

/**
 * 인테이크 한 턴 처리 코어. {@link IntakeController} 가 쓰던 본문을 그대로 옮긴 것으로,
 * 통합 라우터(① 입구)가 ③ 입구로 보낼 때도 같은 로직을 재사용한다(중복 제거).
 *
 * <p><b>범위:</b> 한 턴만 처리한다 — 슬롯 접지·ready 판정 위임 등 ③ 내부 멀티턴 동작은 변경하지 않는다.
 * conversationId 발급은 호출부가 책임진다(여기는 non-null 가정).</p>
 */
@Service
public class IntakeAskService {

    private static final Logger log = LoggerFactory.getLogger(IntakeAskService.class);

    private final IntakeChatAgent agent;
    private final IntakeSlotTrace trace;
    private final AutoPrepIntakeService autoPrepIntakeService;

    public IntakeAskService(IntakeChatAgent agent,
                            IntakeSlotTrace trace,
                            AutoPrepIntakeService autoPrepIntakeService) {
        this.agent = agent;
        this.trace = trace;
        this.autoPrepIntakeService = autoPrepIntakeService;
    }

    public IntakeAskResponse ask(Long userId, String message, Long conversationId) {
        trace.clear();
        try {
            // 요청 컨텍스트 주입 + 첫 발화면 originalQuery 고정(툴이 userId 를, 컨트롤러가 query 를 읽는다).
            trace.begin(userId, conversationId, message);

            String answer = MessageSanitizer.stripMarkdown(agent.chat(conversationId, message));

            // 코드가 검증·확정한 슬롯만 그릇에 채운다. (coverLetterText·attachmentFileIds 는 2단계)
            IntakeSlotTrace.IntakeSlots slots = trace.snapshot();
            AutoPrepRequest autoPrepRequest = new AutoPrepRequest(
                    slots.originalQuery(), slots.caseId(), slots.mode(), null, null);

            // ready/nextAsk 판정은 D 의 기존 서비스에 위임(의존그래프·파트선택 재구현 금지).
            AutoPrepIntakeResponse check = autoPrepIntakeService.intake(userId, autoPrepRequest);

            return new IntakeAskResponse(
                    conversationId, answer, check.ready(), check.nextAsk(), autoPrepRequest);
        } catch (Exception e) {
            log.error("인테이크 에이전트 응답 실패 (Ollama 장애 추정): {}", e.getMessage(), e);
            return new IntakeAskResponse(
                    conversationId,
                    "지금은 답변을 생성하기 어렵습니다. 잠시 후 다시 시도해 주세요.",
                    false, null, null);
        } finally {
            trace.clear();
        }
    }
}
