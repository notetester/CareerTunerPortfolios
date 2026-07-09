package com.careertuner.support.chatbot.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import jakarta.annotation.PostConstruct;

/**
 * AI 챗봇 일일 사용 쿼터 정책 캐싱 서비스.
 *
 * <p>챗봇 요청 hot-path 에서 참조한다. 코드 기본값은 <b>OFF(무제약)</b> — 현재 동작과 동일해
 * 정책이 없거나 미적용 DB 여도 챗봇이 제약 없이 동작한다. ON 이면 로그인 사용자의 오늘 사용량이
 * 한도 이상일 때 429 로 차단한다.</p>
 */
@Service
public class ChatbotQuotaPolicyService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotQuotaPolicyService.class);
    private static final int ID = 1;

    static final boolean DEFAULT_ENABLED = false; // 현재 동작 = 무제약
    static final int DEFAULT_DAILY_LIMIT = 100;

    private final ChatbotQuotaPolicyMapper mapper;

    private volatile boolean quotaEnabled = DEFAULT_ENABLED;
    private volatile int dailyLimit = DEFAULT_DAILY_LIMIT;

    public ChatbotQuotaPolicyService(ChatbotQuotaPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    void init() {
        try {
            ChatbotQuotaPolicy p = mapper.findPolicy();
            if (p != null) {
                applyToCache(p.enabled(), p.dailyLimit());
            }
        } catch (Exception e) {
            log.warn("챗봇 쿼터 정책 로드 실패 — 코드 기본값(OFF) 사용(20260706d 미적용 가능): {}", e.getMessage());
        }
        log.info("챗봇 쿼터 정책: enabled={}, dailyLimit={}", quotaEnabled, dailyLimit);
    }

    private void applyToCache(boolean enabled, int limit) {
        this.quotaEnabled = enabled;
        this.dailyLimit = limit;
    }

    /* ── hot-path ── */

    public boolean isEnabled() {
        return quotaEnabled;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    /**
     * 오늘 사용량이 한도 이상이면 429 로 차단한다. OFF 면 아무리 써도 통과(무제약).
     * (컨트롤러는 OFF 일 때 usedToday 를 조회하지 않도록 isEnabled() 로 선-가드한다.)
     */
    public void assertWithinDailyLimit(int usedToday) {
        if (quotaEnabled && usedToday >= dailyLimit) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "오늘 사용할 수 있는 챗봇 질문 수(" + dailyLimit + "회)를 모두 사용했어요. 내일 다시 이용해 주세요.");
        }
    }

    /* ── 관리자 콘솔 ── */

    public ChatbotQuotaPolicy getCurrent() {
        try {
            ChatbotQuotaPolicy p = mapper.findPolicy();
            if (p != null) {
                return p;
            }
        } catch (Exception e) {
            log.warn("챗봇 쿼터 정책 조회 실패 — 캐시 스냅샷 반환(20260706d 미적용 가능): {}", e.getMessage());
        }
        return new ChatbotQuotaPolicy(ID, quotaEnabled, dailyLimit, null, null, null);
    }

    public ChatbotQuotaPolicy update(boolean enabled, int newDailyLimit, Long actorId) {
        mapper.updatePolicy(enabled, newDailyLimit, actorId);
        applyToCache(enabled, newDailyLimit);
        log.info("챗봇 쿼터 정책 변경: enabled={}, dailyLimit={}, actor={}", enabled, newDailyLimit, actorId);
        return getCurrent();
    }
}
