package com.careertuner.ai.autoprep;

/**
 * 지원 건 회사명·직무명 "미확인" 판정 공유 검증기 — ③ 인테이크 ready 판정(AutoPrepIntakeService)과
 * ④ 온보딩 resolve(ChatbotController.onboardingResolveCase)가 같은 함수를 쓴다(게이트 비대칭 제거).
 *
 * <p>B 추출 워커(ApplicationCaseExtractionWorker.DEFAULT_*)는 자동판독 실패 시 아래 placeholder
 * 문자열을 그대로 기록한다. 이 검증기는 placeholder 와 null/blank 를 모두 "미확인"으로 취급하므로,
 * 워커가 매직 스트링 대신 null 을 기록하는 방식으로 바뀌어도 호출부 무수정으로 호환된다.</p>
 */
public final class CaseSlotValidator {

    /** B 워커가 기록하는 placeholder 원문 — 워커 쪽 상수 변경 시 함께 갱신. */
    public static final String PLACEHOLDER_COMPANY = "기업명 확인 필요";
    public static final String PLACEHOLDER_JOB_TITLE = "직무명 확인 필요";

    private CaseSlotValidator() {
    }

    /** 회사명/직무명 값이 "미확인"인지 — null·blank·placeholder 전부 미확인. */
    public static boolean isUnresolved(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        return PLACEHOLDER_COMPANY.equals(trimmed) || PLACEHOLDER_JOB_TITLE.equals(trimmed);
    }

    /** 확인된 값만 돌려준다(미확인이면 null) — 사용자 노출 문장 보간 방어용. */
    public static String resolvedOrNull(String value) {
        return isUnresolved(value) ? null : value.trim();
    }
}
