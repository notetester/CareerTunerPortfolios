package com.careertuner.legal.domain;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/**
 * 법적 문서 타입. 공개 경로의 docType 슬러그(terms/privacy/...)와
 * DB 컬럼값(TERMS/PRIVACY/...), 한글 라벨을 함께 관리한다.
 * 슬러그는 프론트 routes.ts 의 `legal/<seg>` 마지막 세그먼트와 1:1로 맞춘다.
 */
public enum LegalDocType {

    TERMS("terms", "이용약관"),
    PRIVACY("privacy", "개인정보처리방침"),
    MARKETING("marketing", "마케팅수신동의"),
    AI_CONSENT("ai-data-consent", "AI 데이터 이용 동의"),
    COPYRIGHT("copyright", "저작권 정책");

    private final String slug;
    private final String label;

    LegalDocType(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    /** 공개 경로 슬러그 (terms/privacy/marketing). */
    public String slug() {
        return slug;
    }

    /** 한글 표시 라벨 (이용약관/개인정보처리방침/마케팅수신동의). */
    public String label() {
        return label;
    }

    /** DB 저장값 (TERMS/PRIVACY/MARKETING/AI_CONSENT/COPYRIGHT). */
    public String dbValue() {
        return name();
    }

    /**
     * 슬러그(ai-data-consent 등) 또는 DB 값(AI_CONSENT 등)에서 enum 으로 변환한다.
     * 대소문자 무관. 슬러그와 DB 값이 다른 타입(AI_CONSENT)도 둘 다 받는다.
     * 알 수 없는 값이면 INVALID_INPUT.
     */
    public static LegalDocType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "문서 타입이 비어 있습니다.");
        }
        String v = raw.trim();
        for (LegalDocType type : values()) {
            if (type.slug.equalsIgnoreCase(v) || type.name().equalsIgnoreCase(v)) {
                return type;
            }
        }
        throw new BusinessException(
                ErrorCode.INVALID_INPUT, "알 수 없는 문서 타입입니다: " + raw);
    }
}
