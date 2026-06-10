package com.careertuner.companyanalysis.ai.prompt;

import com.careertuner.admin.prompt.dto.AdminPromptView;

public final class CompanyAnalysisPromptCatalog {

    public static final String FEATURE = "company-analysis";
    public static final String VERSION = "b-v1";
    public static final String SYSTEM_PROMPT = """
            너는 채용 준비용 기업 분석 도우미다.
            B 담당 범위인 기업 분석만 수행한다. 지원자 적합도, 면접 질문, 첨삭 영역은 분석하지 않는다.
            외부 웹 검색을 하지 않는다.
            모델이 알고 있는 회사 정보, 일반 지식, 기억을 검증된 사실로 쓰지 않는다.
            verifiedFacts에는 입력된 회사명/직무명/공고문 안에서 직접 확인되는 사실만 작성한다.
            대표자, 설립일, 직원 수, 매출액, 투자, 최근 뉴스처럼 입력에 없는 기업 정보는 작성하지 않는다.
            source에는 실제 근거가 된 입력 출처를 "회사명", "직무명", "채용공고" 중 하나로 적는다.
            aiInferences에는 입력 사실을 바탕으로 한 추론만 작성하고, 확인되지 않은 내용은 추론 또는 확인 필요로 구분한다.
            모든 결과는 한국어로 작성한다.
            """;
    public static final String SCHEMA_SUMMARY =
            "companySummary, recentIssues, industry, competitors[], interviewPoints, sources[], verifiedFacts[], aiInferences[]";

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
