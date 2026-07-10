package com.careertuner.applicationcase.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * B 분석(공고·기업) 실행 provider 식별자 공유 타입.
 *
 * <p>등록 시 사용자 선택(초기 실행 프로필), strict 수동 재분석의 단일 provider, 기업분석 폴백 순서가
 * 모두 같은 값 집합을 쓰도록 job/company/등록 검증에서 공용한다(문자열 분기·오탈자 방지).
 * OCR provider(SELF_OCR 등)와는 별개다 — 여기는 <b>분석 모델</b> provider 만 다룬다.</p>
 */
public enum BAnalysisProvider {
    LOCAL("Local LLM"),
    CLAUDE("Claude"),
    OPENAI("OpenAI");

    private final String label;

    BAnalysisProvider(String label) {
        this.label = label;
    }

    /** 로그·기록용 사람이 읽는 라벨. */
    public String label() {
        return label;
    }

    /**
     * 대소문자·공백 무시 파싱. 빈 문자열/null/알 수 없는 값이면 {@link Optional#empty()}
     * (= 미선택 또는 무효 — 호출부가 400 거절 또는 기본 동작으로 분기).
     */
    public static Optional<BAnalysisProvider> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * 시도한 provider 순서를 {@code attempt_path} JSON 컬럼 값으로 직렬화한다(예: {@code ["LOCAL","LOCAL"]}).
     * enum 이름만이라 escape 가 필요 없어 수동 조립으로도 항상 유효 JSON 이다({@code toString()} 은 무효 JSON).
     * 빈 목록이면 {@code null}(컬럼 NULL) — 시도 기록이 없다는 뜻.
     */
    public static String toAttemptPathJson(List<BAnalysisProvider> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return null;
        }
        return attemptPathJson(attempts.stream().map(Enum::name).toList());
    }

    /**
     * 통제된 토큰 목록(provider 이름 + {@code SELF_RULES} 같은 비-provider 안전망)을 {@code attempt_path} JSON
     * 으로 직렬화한다. 초기 등록 preferred 경로는 폴백·self-rules 까지 시도 순서에 담아야 해서
     * enum 목록만으로는 표현할 수 없다(예: {@code ["CLAUDE","OPENAI","SELF_RULES"]}). 토큰은 enum 이름 계열
     * 대문자라 escape 가 필요 없다 — 임의 사용자 입력을 넘기지 말 것. 빈 목록이면 {@code null}.
     */
    public static String attemptPathJson(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        return tokens.stream()
                .map(token -> "\"" + token + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
