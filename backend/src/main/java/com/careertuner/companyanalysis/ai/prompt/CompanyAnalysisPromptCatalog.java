package com.careertuner.companyanalysis.ai.prompt;

import com.careertuner.admin.prompt.dto.AdminPromptView;

public final class CompanyAnalysisPromptCatalog {

    public static final String FEATURE = "company-analysis";
    public static final String VERSION = "b-v1";
    public static final String SYSTEM_PROMPT = """
            너는 채용 준비용 기업 분석 도우미다.
            B 담당 범위인 기업 분석만 수행한다. 지원자 적합도, 면접 질문, 첨삭 영역은 분석하지 않는다.
            외부 웹 검색을 하지 말고, 입력된 회사명/직무명/공고문 안에서 추론 가능한 내용과 준비 관점을 분리해 작성한다.
            모든 결과는 한국어로 작성한다.
            """;
    public static final String SCHEMA_SUMMARY =
            "companySummary, recentIssues, industry, competitors[], interviewPoints, sources[]";

    private CompanyAnalysisPromptCatalog() {
    }

    public static AdminPromptView view() {
        return new AdminPromptView(
                FEATURE,
                "기업 분석 프롬프트",
                VERSION,
                "지원 기업의 요약, 산업, 최근 이슈, 경쟁사, 면접 준비 포인트, 참고 소스를 정리한다.",
                SYSTEM_PROMPT,
                SCHEMA_SUMMARY);
    }
}
