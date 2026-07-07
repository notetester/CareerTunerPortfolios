package com.careertuner.admin.fitanalysis.dto;

/**
 * 관리자 적합도 목록 요청 파라미터(컨트롤러 → 서비스). 서비스가 page/size 를 정규화하고 offset 을 계산해
 * {@link AdminFitAnalysisListCriteria}(SQL 조건)로 변환한다.
 *
 * @param reviewRequiredOnly 검토 필요(REVIEW_REQUIRED) gate 만
 * @param query             기업/직무/회원명/이메일 부분일치 검색어
 * @param band              점수 밴드 ALL/HIGH/MID_HIGH/MID/LOW
 * @param result            분석 상태 ALL/SUCCESS/FAIL
 * @param memoOnly          메모 있는 분석만
 * @param reanalysisOnly    재분석 요청된 분석만
 * @param page              1부터 시작하는 페이지 번호(정규화 전 원값)
 * @param size              페이지 크기(정규화 전 원값)
 */
public record AdminFitAnalysisListQuery(
        boolean reviewRequiredOnly,
        String query,
        String band,
        String result,
        boolean memoOnly,
        boolean reanalysisOnly,
        int page,
        int size) {
}
