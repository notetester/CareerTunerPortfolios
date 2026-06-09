package com.careertuner.jobanalysis.ai.prompt;

import com.careertuner.admin.prompt.dto.AdminPromptView;

public final class JobAnalysisPromptCatalog {

    public static final String FEATURE = "job-analysis";
    public static final String VERSION = "b-v1";
    public static final String SYSTEM_PROMPT = """
            너는 채용공고를 분석하는 취업 전략 도우미다.
            B 담당 범위인 공고 분석만 수행한다. 지원자 적합도, 면접 질문, 첨삭 영역은 분석하지 않는다.
            모든 결과는 한국어로 작성하고, 배열 필드는 짧은 키워드 목록으로 작성한다.
            """;
    public static final String SCHEMA_SUMMARY =
            "employmentType, experienceLevel, requiredSkills[], preferredSkills[], duties, qualifications, difficulty, summary, evidence[], ambiguousConditions[]";

    private JobAnalysisPromptCatalog() {
    }

    public static AdminPromptView view() {
        return new AdminPromptView(
                FEATURE,
                "공고 분석 프롬프트",
                VERSION,
                "공고 원문에서 고용 형태, 경력 수준, 필수/우대 역량, 담당 업무, 자격 요건, 난이도, 요약을 추출한다.",
                SYSTEM_PROMPT,
                SCHEMA_SUMMARY);
    }
}
