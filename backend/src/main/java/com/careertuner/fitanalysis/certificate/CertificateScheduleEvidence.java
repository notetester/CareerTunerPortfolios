package com.careertuner.fitanalysis.certificate;

import java.util.List;

/**
 * 국가기술자격 종목의 시험일정 근거(공식 출처 조회 결과). C 는 이 객체 안의 {@link #rounds} 로만 일정을 말하고,
 * {@link #status} 가 VERIFIED_CURRENT 가 아니면 날짜를 단정하지 않는다(뉴로-심볼릭: 사실 근거는 서버 소유).
 *
 * @param status     조회 상태
 * @param jmCd       종목코드
 * @param certName   종목명(공식 응답에서 확인, 없으면 조회 힌트)
 * @param sourceName 출처명
 * @param sourceUrl  출처 URL(사용자 재확인용)
 * @param rounds     확인된 회차별 일정(status=VERIFIED_CURRENT 일 때만 비어있지 않음)
 */
public record CertificateScheduleEvidence(
        ScheduleEvidenceStatus status,
        String jmCd,
        String certName,
        String sourceName,
        String sourceUrl,
        List<ScheduleRound> rounds) {

    /** 국가기술자격 한 회차의 핵심 일정(yyyyMMdd 문자열, 미확인 항목은 null). */
    public record ScheduleRound(
            String round,
            String docRegStart,
            String docRegEnd,
            String docExam,
            String docPass,
            String pracExamStart,
            String pracExamEnd,
            String pracPass) {
    }
}
