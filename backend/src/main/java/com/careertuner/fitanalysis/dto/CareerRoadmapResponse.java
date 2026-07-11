package com.careertuner.fitanalysis.dto;

import java.util.List;

/**
 * 장기 커리어 로드맵 — 목표 기간(개월) 안의 <b>결정론 계획</b>. 날짜의 출처는 두 종류뿐이다:
 * ① 공식/사전공고로 확인된 자격증 회차·사용자 지원건 마감(실데이터, {@code planningBlock=false})
 * ② 월 단위 학습 계획 블록(사용자 계획 제안, {@code planningBlock=true} — 임의 '확정 일정'이 아님을 계약으로 구분).
 * LLM 은 관여하지 않는다(뉴로-심볼릭: 날짜·배치는 서버 결정론 소유).
 *
 * @param desiredJob    프로필 희망 직무(없으면 로드맵 생성 불가 안내)
 * @param horizonMonths 로드맵 기간(개월)
 * @param generatedAt   생성 시각
 * @param items         시간순 로드맵 항목
 * @param basisNotes    산출 근거·한계(어떤 데이터로 만들었는지 솔직 고지)
 */
public record CareerRoadmapResponse(
        String desiredJob,
        int horizonMonths,
        String generatedAt,
        List<RoadmapItem> items,
        List<String> basisNotes) {

    /**
     * 로드맵 한 항목.
     *
     * @param type          CERT_REGISTRATION | CERT_EXAM | CERT_PRACTICAL | SKILL_LEARNING | APPLICATION_DEADLINE
     * @param title         표시 제목
     * @param startDate     시작일(yyyy-MM-dd)
     * @param endDate       종료일(yyyy-MM-dd, 단일일이면 null)
     * @param certName      자격증 항목이면 자격증명
     * @param detail        보조 설명(회차·사전공고 표시 등)
     * @param sourceName    실데이터 출처명(계획 블록은 null)
     * @param planningBlock true=월 단위 학습 계획 제안(실일정 아님), false=확인된 실일정
     */
    public record RoadmapItem(
            String type,
            String title,
            String startDate,
            String endDate,
            String certName,
            String detail,
            String sourceName,
            boolean planningBlock) {
    }
}
