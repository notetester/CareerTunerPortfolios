package com.careertuner.fitanalysis.dto;

import java.util.List;

/**
 * 장기 커리어 자격증 전략 — <b>현재 지원 건 전략과 분리</b>된 사용자 단위(desiredJob 기준) 결과.
 * "이번 공고에 도움되는가"가 아니라 "희망 직군에서 장기적으로 취득 가치가 있는가"를 다룬다.
 *
 * <p>결정론 규칙(카탈로그) 소유이며 외부 API 를 호출하지 않는다(읽기 경로 provider 금지). 일정은 근거 확인
 * 전까지 말하지 않고, 자격증보다 프로젝트/실무경험 우선 원칙을 note 로 항상 동반한다.
 *
 * @param desiredJob         희망 직무(프로필). 비어 있으면 후보를 내지 않는다.
 * @param heldStrengths      이미 보유한 자격증(장기 관점의 어필 자산)
 * @param longTermCandidates 장기 취득 후보(보유분 제외). 이번 지원 전략이 아니다.
 * @param note               솔직한 우선순위 안내(실무 경험 우선, 일정은 공식 확인 필요)
 */
public record CareerCertificateStrategyResponse(
        String desiredJob,
        List<String> heldStrengths,
        List<CareerCertificateCandidate> longTermCandidates,
        String note) {

    /** 장기 후보 1건 — 이번 지원용이 아닌 커리어 관점 사유를 담는다. */
    public record CareerCertificateCandidate(String name, String reason) {
    }
}
