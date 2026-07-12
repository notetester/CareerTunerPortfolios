package com.careertuner.fitanalysis.dto;

import java.util.List;

/**
 * 자격증 통합 검색 결과 — 국가자격(오프라인 스냅샷)과 민간자격(등록정보 라이브 조회)을 함께 반환한다.
 * 검색은 조회일 뿐 추천이 아니다(cert-need-gate 판정과 무관). 민간 조회 실패는 privateLookupFailed 로
 * 솔직히 표시한다(빈 결과 = 미등록으로 오독 방지).
 *
 * @param query               사용자가 입력한 검색어
 * @param resolvedAlias       검증된 별칭이 적용됐다면 그 공식 명칭(예: SQLD → SQL), 아니면 null
 * @param national            국가자격 매칭(스냅샷 기준)
 * @param nationalUnavailable 국가자격 스냅샷 미로드 여부(true 면 국가 결과 없음 ≠ 부재)
 * @param privateMatches      민간자격 등록정보 매칭
 * @param privateLookupFailed 민간 조회 실패 여부(true 면 민간 결과 없음 ≠ 미등록)
 */
public record CertificateSearchResponse(
        String query,
        String resolvedAlias,
        List<NationalItem> national,
        boolean nationalUnavailable,
        List<PrivateItem> privateMatches,
        boolean privateLookupFailed) {

    /**
     * @param name              종목명
     * @param kind              NATIONAL_TECHNICAL | NATIONAL_PROFESSIONAL
     * @param scheduleQueryable 종목별 시험일정 자동 조회 가능 여부(검증된 jmCd 보유 또는 사전공고 귀속)
     */
    public record NationalItem(String name, String kind, boolean scheduleQueryable) {
    }

    /**
     * @param name           등록 자격명
     * @param currentStatus  현재상태(등록완료/등록폐지 등 — 원문 그대로)
     * @param institution    신청기관
     * @param registrationNo 등록번호
     */
    public record PrivateItem(String name, String currentStatus, String institution, String registrationNo) {
    }
}
