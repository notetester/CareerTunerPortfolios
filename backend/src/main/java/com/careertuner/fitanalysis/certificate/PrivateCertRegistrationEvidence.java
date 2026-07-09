package com.careertuner.fitanalysis.certificate;

import java.util.List;

/**
 * 민간자격 등록정보 조회 근거(한국직업능력연구원 민간자격등록정보, odcloud 15075600). 존재/등록상태/기관 확인용이며
 * 시험일정은 담지 않는다. C 는 이 근거로 "실재 등록 자격인지/폐지됐는지/어느 기관인지"만 말하고 일정은 만들지 않는다.
 *
 * @param status     조회 상태
 * @param query      조회한 자격명
 * @param matchCount 필터 매칭 총건수(부분일치)
 * @param snapshot   데이터 스냅샷 라벨(예: 20251231 — 비실시간 반기 갱신)
 * @param sourceName 출처명
 * @param sourceUrl  출처(주관기관 확인 유도)
 * @param matches    상위 매칭(자격명/등록번호/현재상태/신청기관/공인여부)
 */
public record PrivateCertRegistrationEvidence(
        PrivateCertRegistrationStatus status,
        String query,
        int matchCount,
        String snapshot,
        String sourceName,
        String sourceUrl,
        List<Match> matches) {

    /** 민간자격 한 건의 등록정보. */
    public record Match(
            String name,
            String registrationNo,
            String currentStatus,
            String institution,
            String accreditation) {
    }
}
