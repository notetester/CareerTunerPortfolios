package com.careertuner.fitanalysis.dto;

import java.util.List;

/**
 * 자격증 근거 snapshot — 적합도 분석 <b>생성 시 1회</b> 수집해 fit_analysis.certificate_evidence(JSON)에 저장한다.
 * 조회(읽기) 경로에서는 외부 API 를 호출하지 않고 이 snapshot 만 읽는다(Q-Net 장애가 화면 조회 성능에 영향 없음).
 *
 * @param generatedAt 근거 수집 시각(freshness)
 * @param items       자격증별 근거(비면 게이트 OFF/미연동 — 응답에 자격증 근거 섹션 없음)
 */
public record CertificateEvidenceSnapshot(
        String generatedAt,
        List<CertificateEvidenceResponse> items) {
}
