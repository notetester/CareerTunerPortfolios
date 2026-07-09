package com.careertuner.fitanalysis.dto;

import java.util.List;

/**
 * 자격증 근거 snapshot — 적합도 분석 <b>생성 시 1회</b> 수집해 fit_analysis.certificate_evidence(JSON)에 저장한다.
 * 조회(읽기) 경로에서는 외부 API 를 호출하지 않고 이 snapshot 만 읽는다(Q-Net 장애가 화면 조회 성능에 영향 없음).
 *
 * @param generatedAt       근거 수집 시각(freshness)
 * @param strategyStatus    cert-need-gate 의 자격증 전략 판정(NOT_NEEDED/OPTIONAL_LOW_PRIORITY/RECOMMENDED/
 *                          REQUIRED_OR_STRONGLY_PREFERRED/USE_EXISTING_AS_STRENGTH). <b>탭 요청이어도 무조건 추천이
 *                          아니라 평가</b>이므로 후순위/불필요도 정상 결과다.
 * @param triggeredSignals  게이트를 켠 신호 코드(설명/디버그용)
 * @param items             자격증별 근거(비면 게이트 OFF/미연동 — 자격증 카드 없음)
 */
public record CertificateEvidenceSnapshot(
        String generatedAt,
        String strategyStatus,
        List<String> triggeredSignals,
        List<CertificateEvidenceResponse> items) {
}
