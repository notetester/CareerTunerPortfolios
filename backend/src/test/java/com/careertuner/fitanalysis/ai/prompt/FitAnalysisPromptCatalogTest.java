package com.careertuner.fitanalysis.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FitAnalysisPromptCatalogTest {

    private static String prompt(String companyContext) {
        return FitAnalysisPromptCatalog.fitExplainUserPrompt(
                "테스트기업", "백엔드 개발자", "백엔드 개발자",
                "Java, Spring", "AWS", "REST API 개발",
                "Java", "정보처리기사",
                70, "APPLY", "Java", "Spring", "AWS", companyContext);
    }

    @Test
    void companyContextAbsentKeepsCoreBodyOnly() {
        // 기업 맥락이 없으면(null/blank) 프롬프트는 학습 데이터(build_fit_user)와 동일한 본문만 — skew 없음.
        String withoutCtx = prompt(null);
        assertThat(withoutCtx).doesNotContain("기업 맥락");
        assertThat(prompt("   ")).isEqualTo(withoutCtx);      // blank 도 동일 취급
        assertThat(withoutCtx).contains("규칙엔진 사전계산").contains("적합도 점수(fitScore): 70");
    }

    @Test
    void companyContextPresentAppendsLabeledSectionAfterCoreBody() {
        String withCtx = prompt("- 회사 요약: 결제 플랫폼 스타트업\n- 최근 이슈: 대규모 트래픽 대응");
        // 본문은 그대로 두고 뒤에 라벨 섹션만 덧붙는다(혼동 금지 지시 포함).
        assertThat(withCtx).startsWith(prompt(null));
        assertThat(withCtx).contains("## 기업 맥락").contains("결제 플랫폼 스타트업")
                .contains("지원자 보유역량과 혼동 금지");
    }

    @Test
    void profileInsightAppendsLabeledConflationSafeSection() {
        String withInsight = FitAnalysisPromptCatalog.fitExplainUserPrompt(
                "테스트기업", "백엔드 개발자", "백엔드 개발자",
                "Java, Spring", "AWS", "REST API 개발", "Java", "정보처리기사",
                70, "APPLY", "Java", "Spring", "AWS", null,
                "- 요약: 백엔드 기본기가 탄탄함\n- 강점: 문제해결\n- 보완점: 클라우드 경험");

        // profileInsight 없는 본문 그대로 + 라벨 섹션(보유 확정 아님 명시)만 뒤에 덧붙는다.
        assertThat(withInsight).startsWith(prompt(null));
        assertThat(withInsight).contains("## 지원자 프로필 AI 분석 요약").contains("보유 확정 아님")
                .contains("문제해결");
    }

    @Test
    void profileInsightNullKeepsCoreBodyIdentical() {
        String base = prompt(null);
        String withNull = FitAnalysisPromptCatalog.fitExplainUserPrompt(
                "테스트기업", "백엔드 개발자", "백엔드 개발자",
                "Java, Spring", "AWS", "REST API 개발", "Java", "정보처리기사",
                70, "APPLY", "Java", "Spring", "AWS", null, null);
        assertThat(withNull).isEqualTo(base); // train/serve 정합 — 추가 컨텍스트 없으면 byte 동일
    }
}
