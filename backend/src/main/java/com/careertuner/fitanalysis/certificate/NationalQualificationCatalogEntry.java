package com.careertuner.fitanalysis.certificate;

/**
 * 국가자격 종목 목록 한 종목의 catalog 항목. <b>jmCd 는 통합 시험일정 API({@link UnifiedExamScheduleProvider})의
 * 종목별 조회 키</b>다 — null 이면(매핑 미확보·canonical 미확정 종목) 일정 조회가 레거시 getJMList(stdt 연도 조회 후
 * 자격명 매칭, jmCd 불사용) 경로로만 동작한다. 값의 소스는 번들 검증 매핑(오프라인 스냅샷 경로) 또는 getList 응답
 * (네트워크 경로) — 둘 다 한국산업인력공단 공식 코드 체계다.
 *
 * @param jmCd          종목코드(통합 일정 API 조회 키, 미확보 시 null)
 * @param certName      종목명(jmfldnm)
 * @param qualGroupCode 자격구분코드(T=기술자격, S=전문자격)
 * @param qualGroupName 자격구분명
 * @param seriesName    계열명
 * @param jobField      대직무분야명
 * @param midJobField   중직무분야명
 */
public record NationalQualificationCatalogEntry(
        String jmCd,
        String certName,
        String qualGroupCode,
        String qualGroupName,
        String seriesName,
        String jobField,
        String midJobField) {

    /** 국가기술자격(T)이면 종목별 시험일정 조회 대상이다. 전문자격(S)은 일정 체계가 달라 별도 취급. */
    public boolean technical() {
        return "T".equalsIgnoreCase(qualGroupCode);
    }
}
