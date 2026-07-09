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

        // 소유권 가드(IDOR 방어): 기존 conversationId 는 본인 소유만 허용. 익명/미존재 행(owner==null)은 통과.
        // (이 엔드포인트는 authenticated 라 userId 는 항상 non-null — 타유저 소유 대화 접근만 차단한다.)
        if (req.conversationId() != null) {
            Long owner = memoryStore.findOwnerUserId(conversationId);
            if (owner != null && !owner.equals(userId)) {
                return ApiResponse.error("FORBIDDEN", "접근할 수 없는 대화입니다.");
            }
        }

        return ApiResponse.ok(intakeAskService.ask(userId, req.message(), conversationId));
    }
}
