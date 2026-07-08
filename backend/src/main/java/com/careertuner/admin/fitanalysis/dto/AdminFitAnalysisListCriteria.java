package com.careertuner.admin.fitanalysis.dto;

/**
 * 관리자 적합도 목록 서버측 필터·페이징 조건.
 *
 * <p>이전에는 프런트가 전건을 받아 클라이언트에서 필터링했으나(대량 데이터에서 목록이 느려지는 스케일
 * 문제), 모든 필터와 페이징을 서버 SQL 로 옮겼다. {@code band}/{@code result} 가 null 또는 {@code "ALL"}
 * 이면 해당 필터를 적용하지 않는다.
 *
 * @param reviewRequiredOnly 검토 필요(REVIEW_REQUIRED) gate 만
 * @param query             기업/직무/회원명/이메일 부분일치 검색어(빈 문자열이면 무시)
 * @param scoreBand         점수 밴드 ALL/HIGH(≥80)/MID_HIGH(70~80)/MID(50~70)/LOW(&lt;50).
 *                          필드명이 {@code band} 가 아닌 이유: OGNL(MyBatis {@code test=})에서 {@code band}
 *                          는 비트 AND 예약어라 {@code c.band} 가 파싱 오류를 낸다.
 * @param result            분석 상태 ALL/SUCCESS/FAIL(=SUCCESS 아님)
 * @param memoOnly          메모가 1건 이상인 분석만
 * @param reanalysisOnly    재분석(REANALYSIS 메모) 요청된 분석만
 * @param size              페이지 크기
 * @param offset            건너뛸 행 수((page-1)*size)
 */
public record AdminFitAnalysisListCriteria(
        boolean reviewRequiredOnly,
        String query,
        String scoreBand,
        String result,
        boolean memoOnly,
        boolean reanalysisOnly,
        int size,
        int offset) {
}
