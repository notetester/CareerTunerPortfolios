package com.careertuner.common.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/** 관리자 입력 링크가 브라우저 탐색 sink에 도달하기 전 적용하는 공통 허용 정책. */
public final class NavigationLinkPolicy {

    private NavigationLinkPolicy() {
    }

    /** 비어 있으면 null, 값이 있으면 동일 출처 내부 절대 경로만 허용한다. */
    public static String optionalInternalPath(String raw, String fieldName) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (!isInternalPath(value)) {
            throw invalid(fieldName, "서비스 내부 경로(/...)만 입력할 수 있습니다.");
        }
        return value;
    }

    /** 광고처럼 외부 이동이 제품 계약인 경우 내부 경로 또는 명시적 http(s) URL만 허용한다. */
    public static String optionalWebOrInternal(String raw, String fieldName) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (isInternalPath(value) || isHttpUrl(value)) {
            return value;
        }
        throw invalid(fieldName, "서비스 내부 경로 또는 http(s) URL만 입력할 수 있습니다.");
    }

    static boolean isInternalPath(String value) {
        return value.startsWith("/")
                && !value.startsWith("//")
                && !hasUnsafeCharacters(value);
    }

    static boolean isHttpUrl(String value) {
        if (hasUnsafeCharacters(value)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return false;
            }
            String normalized = scheme.toLowerCase(Locale.ROOT);
            return "http".equals(normalized) || "https".equals(normalized);
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private static boolean hasUnsafeCharacters(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || Character.isISOControl(character) || Character.isWhitespace(character)) {
                return true;
            }
        }
        return false;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static BusinessException invalid(String fieldName, String detail) {
        return new BusinessException(ErrorCode.INVALID_INPUT, fieldName + "에는 " + detail);
    }
}
