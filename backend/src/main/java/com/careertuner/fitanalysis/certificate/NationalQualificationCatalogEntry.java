package com.careertuner.fitanalysis.certificate;

/**
 * 국가자격 종목 목록(15003024) 한 종목의 catalog 항목. <b>jmCd 는 시험일정 조회 파라미터가 아니라 내부 canonical
 * key/메타데이터</b>다(getJMList 는 stdt 로 연도 조회 후 자격명 매칭 — jmCd 를 넘기지 않는다).
 *
 * @param jmCd          종목코드(canonical key)
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

    /** 국가기술자격(T)이면 getJMList 시험일정 조회 대상이다. 전문자격(S)은 일정 체계가 달라 별도 취급. */
    public boolean technical() {
        return "T".equalsIgnoreCase(qualGroupCode);
    }
}
