package com.careertuner.support.chatbot.quota;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.careertuner.common.exception.BusinessException;

/**
 * 챗봇 쿼터 정책 <b>토글 3상태</b> 검증(사용자 요구: OFF·ON기본·ON변경).
 * assertWithinDailyLimit 이 오늘 사용량 대비 정확히 차단/통과하는지 본다.
 */
class ChatbotQuotaPolicyServiceTest {

    private final ChatbotQuotaPolicyMapper mapper = mock(ChatbotQuotaPolicyMapper.class);
    private final ChatbotQuotaPolicyService service = new ChatbotQuotaPolicyService(mapper);

    // ── OFF(기본): 아무리 써도 통과(무제약) ──
    @Test
    void off_neverBlocks() {
        service.update(false, 100, null);            // OFF
        assertThatCode(() -> service.assertWithinDailyLimit(999_999)).doesNotThrowAnyException();
    }

    // ── ON 기본(100): 한도 미만 통과, 한도 도달 차단 ──
    @Test
    void on_default100_blocksAtLimit() {
        service.update(true, 100, null);             // ON, 100
        assertThatCode(() -> service.assertWithinDailyLimit(99)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertWithinDailyLimit(100)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.assertWithinDailyLimit(150)).isInstanceOf(BusinessException.class);
    }

    // ── ON 변경(5): 값 변경이 의도대로 — 5회째 차단 ──
    @Test
    void on_custom5_blocksAtNewLimit() {
        service.update(true, 5, null);               // ON, 5
        assertThatCode(() -> service.assertWithinDailyLimit(4)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertWithinDailyLimit(5)).isInstanceOf(BusinessException.class);
    }

    // ── 토글을 OFF로 되돌리면 다시 무제약 ──
    @Test
    void toggleBackToOff_restoresUnlimited() {
        service.update(true, 5, null);
        assertThatThrownBy(() -> service.assertWithinDailyLimit(5)).isInstanceOf(BusinessException.class);
        service.update(false, 5, null);              // 다시 OFF
        assertThatCode(() -> service.assertWithinDailyLimit(999)).doesNotThrowAnyException();
    }
}
