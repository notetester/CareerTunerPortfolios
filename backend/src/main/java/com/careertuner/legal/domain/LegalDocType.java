package com.careertuner.legal.domain;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/**
 * 법적 문서 타입. 공개 경로의 docType 슬러그(terms/privacy/marketing)와
 * DB 컬럼값(TERMS/PRIVACY/MARKETING), 한글 라벨을 함께 관리한다.
 */
public enum LegalDocType {

    TERMS("terms", "이용약관"),
    PRIVACY("privacy", "개인정보처리방침"),
    MARKETING("marketing", "마케팅수신동의");

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

    /** DB 저장값 (TERMS/PRIVACY/MARKETING). */
    public String dbValue() {
        return name();
    }

    /**
     * 슬러그 또는 DB 값(대소문자 무관)에서 enum 으로 변환한다.
     * 알 수 없는 값이면 INVALID_INPUT.
     */
    public static LegalDocType from(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "문서 타입이 비어 있습니다.");
        }
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "TERMS" -> TERMS;
            case "PRIVACY" -> PRIVACY;
            case "MARKETING" -> MARKETING;
            default -> throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "알 수 없는 문서 타입입니다: " + raw);
        };
    }
}
