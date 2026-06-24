package com.careertuner.ai.intake;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.ai.autoprep.AutoPrepIntakeService;
import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.chat.MessageSanitizer;
import com.careertuner.ai.chat.MyBatisChatMemoryStore;
import com.careertuner.ai.intake.dto.IntakeAskRequest;
import com.careertuner.ai.intake.dto.IntakeAskResponse;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

/**
 * 인테이크 챗봇 진입점. {@code ChatbotController.ask} 를 복제하되 F 네임스페이스
 * {@code POST /api/chatbot/intake/ask} 로 신설한다(D 의 {@code /api/auto-prep} 에 추가하지 않음).
 *
 * <p>흐름: 대화로 슬롯(caseId·mode)을 채워 검증·확정 → 검증된 {@link AutoPrepRequest} 조립 →
 * D 의 {@link AutoPrepIntakeService#intake} 로 ready 판정 위임 → 응답 반환.
 * 실제 실행({@code /run/stream})은 클라이언트가 ready=true 인 autoPrepRequest 로 D 컨트롤러를 직접 연다.
 * F 는 SSE 를 프록시하지 않는다.</p>
 *
 * <p><b>슬롯 접지</b>: LLM 이 말한 caseId/mode 를 직접 신뢰하지 않고, {@link IntakeSlotTrace#snapshot()} 의
 * 검증된 확정값만 AutoPrepRequest 에 넣는다.</p>
 */
@RestController
@RequestMapping("/api/chatbot/intake")
public class IntakeController {

    private static final Logger log = LoggerFactory.getLogger(IntakeController.class);

    private final IntakeChatAgent agent;
    private final IntakeSlotTrace trace;
    private final MyBatisChatMemoryStore memoryStore;
    private final AutoPrepIntakeService autoPrepIntakeService;

    public IntakeController(IntakeChatAgent agent,
                            IntakeSlotTrace trace,
                            MyBatisChatMemoryStore memoryStore,
                            AutoPrepIntakeService autoPrepIntakeService) {
        this.agent = agent;
        this.trace = trace;
        this.memoryStore = memoryStore;
        this.autoPrepIntakeService = autoPrepIntakeService;
    }

    @PostMapping("/ask")
    public ApiResponse<IntakeAskResponse> ask(@RequestBody IntakeAskRequest req,
                                              @AuthenticationPrincipal AuthUser authUser) {
        if (req == null || req.message() == null || req.message().isBlank()) {
            return ApiResponse.error("BAD_REQUEST", "메시지를 입력해 주세요.");
        }

        Long userId = authUser != null ? authUser.id() : null;
        Long conversationId = req.conversationId() != null
                ? req.conversationId()
                : memoryStore.createConversation(userId);

        trace.clear();
        try {
            // 요청 컨텍스트 주입 + 첫 발화면 originalQuery 고정(툴이 userId 를, 컨트롤러가 query 를 읽는다).
            trace.begin(userId, conversationId, req.message());

            String answer = MessageSanitizer.stripMarkdown(agent.chat(conversationId, req.message()));

            // 코드가 검증·확정한 슬롯만 그릇에 채운다. (coverLetterText·attachmentFileIds 는 2단계)
            IntakeSlotTrace.IntakeSlots slots = trace.snapshot();
            AutoPrepRequest autoPrepRequest = new AutoPrepRequest(
                    slots.originalQuery(), slots.caseId(), slots.mode(), null, null);

            // ready/nextAsk 판정은 D 의 기존 서비스에 위임(의존그래프·파트선택 재구현 금지).
            AutoPrepIntakeResponse check = autoPrepIntakeService.intake(userId, autoPrepRequest);

            return ApiResponse.ok(new IntakeAskResponse(
                    conversationId, answer, check.ready(), check.nextAsk(), autoPrepRequest));
        } catch (Exception e) {
            log.error("인테이크 에이전트 응답 실패 (Ollama 장애 추정): {}", e.getMessage(), e);
            return ApiResponse.ok(new IntakeAskResponse(
                    conversationId,
                    "지금은 답변을 생성하기 어렵습니다. 잠시 후 다시 시도해 주세요.",
                    false, null, null));
        } finally {
            trace.clear();
        }
    }
}
