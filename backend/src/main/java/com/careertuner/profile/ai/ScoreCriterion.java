package com.careertuner.profile.ai;

public enum ScoreCriterion {
    GOAL_CLARITY("목표 명확성", "희망 직무와 산업, 근무 조건이 구체적으로 정리되어 있는지 평가합니다."),
    EXPERIENCE_SPECIFICITY("경험 구체성", "학력, 경력, 프로젝트, 활동 기록이 역할과 업무 중심으로 작성되어 있는지 평가합니다."),
    ACHIEVEMENT_EVIDENCE("성과 근거", "수치, 결과, 개선 사례처럼 채용 담당자가 확인할 수 있는 근거가 있는지 평가합니다."),
    JOB_SKILL_ALIGNMENT("직무 역량 적합성", "보유 역량과 스킬이 희망 직무군에서 중요하게 보는 항목과 맞는지 평가합니다."),
    DOCUMENT_CONSISTENCY("문서 완성도", "이력서 본문, 자기소개, 포트폴리오, 자격 정보가 서로 연결되어 있는지 평가합니다."),
    IMPROVEMENT_READINESS("개선 실행성", "부족한 항목을 보완하기 쉬운 형태로 정보가 남아 있는지 평가합니다.");

    private final String label;
    private final String description;

    ScoreCriterion(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
