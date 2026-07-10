package com.careertuner.consent.domain;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/** 회원이 직접 관리하는 동의 유형과 공개 경로 식별자. */
public enum ConsentType {

    TERMS("terms", "서비스 이용약관", true, "v2026.07"),
    PRIVACY("privacy", "개인정보 처리방침", true, "v2026.07"),
    AI_DATA("ai-data", "AI 데이터 이용 동의", false, "v2026.07"),
    RESUME_ANALYSIS("resume-analysis", "이력서 분석 개인정보 수집·이용 동의", false, "v2026.07"),
    MARKETING("marketing", "마케팅 수신 동의", false, "v2026.07");

    private final String slug;
    private final String label;
    private final boolean requiredForService;
    private final String currentVersion;

    ConsentType(String slug, String label, boolean requiredForService, String currentVersion) {
        this.slug = slug;
        this.label = label;
        this.requiredForService = requiredForService;
        this.currentVersion = currentVersion;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    public boolean requiredForService() {
        return requiredForService;
    }

    public String currentVersion() {
        return currentVersion;
    }

    public static ConsentType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "동의 유형이 비어 있습니다.");
        }
        String normalized = raw.trim();
        for (ConsentType type : values()) {
            if (type.name().equalsIgnoreCase(normalized) || type.slug.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 동의 유형입니다: " + raw);
    }
}
