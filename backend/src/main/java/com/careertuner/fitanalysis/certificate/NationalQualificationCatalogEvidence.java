package com.careertuner.fitanalysis.certificate;

/**
 * 국가자격 목록 조회 근거. C 는 이 근거로 "이 자격이 국가(기술/전문)자격인지, 어느 분야인지"를 말하고,
 * 자격증 라우팅(기술자격 → 시험일정 조회 대상)에 쓴다. 사실 근거는 서버 소유(뉴로-심볼릭).
 *
 * @param status     조회 상태
 * @param query      조회한 자격명
 * @param entry      매칭된 종목(status=FOUND 일 때만 non-null)
 * @param sourceName 출처명
 * @param sourceUrl  출처 URL
 */
public record NationalQualificationCatalogEvidence(
        NationalQualificationCatalogStatus status,
        String query,
        NationalQualificationCatalogEntry entry,
        String sourceName,
        String sourceUrl) {
}
