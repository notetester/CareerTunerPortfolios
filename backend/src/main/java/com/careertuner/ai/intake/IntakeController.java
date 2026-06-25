package com.careertuner.ai.intake;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.ai.chat.MyBatisChatMemoryStore;
import com.careertuner.ai.intake.dto.IntakeAskRequest;
import com.careertuner.ai.intake.dto.IntakeAskResponse;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

/**
 * 인테이크 챗봇 진입점. F 네임스페이스 {@code POST /api/chatbot/intake/ask}.
 *
 * <p>한 턴 처리는 {@link IntakeAskService} 로 위임한다(통합 라우터가 ③ 입구로 보낼 때도 같은 서비스 재사용).
 * 이 컨트롤러는 요청 검증과 conversationId 발급만 담당한다 — ③ 내부 멀티턴 동작은 변경 없음.</p>
 */
@RestController
@RequestMapping("/api/chatbot/intake")
public class IntakeController {

    private final IntakeAskService intakeAskService;
    private final MyBatisChatMemoryStore memoryStore;

    public IntakeController(IntakeAskService intakeAskService,
                           MyBatisChatMemoryStore memoryStore) {
        this.intakeAskService = intakeAskService;
        this.memoryStore = memoryStore;
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

        return ApiResponse.ok(intakeAskService.ask(userId, req.message(), conversationId));
    }
}
