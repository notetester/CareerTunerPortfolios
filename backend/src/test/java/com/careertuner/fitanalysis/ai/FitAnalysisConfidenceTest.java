package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class FitAnalysisConfidenceTest {

    private static FitAnalysisAiCommand command(String companyContext) {
        return new FitAnalysisAiCommand("회사", "백엔드", List.of("Java"), List.of("AWS"), "API 개발",
                List.of("Java"), List.of("정보처리기사"), "백엔드 개발자", companyContext);
    }

    @Test
    void fullInputWithCompanyContextIsHigh() {
        FitAnalysisConfidence confidence = FitAnalysisConfidence.evaluate(command("회사 요약: ..."));

        assertThat(confidence.score()).isEqualTo(100);
        assertThat(confidence.level()).isEqualTo("HIGH");
        assertThat(confidence.reasons()).isEmpty();
    }

    @Test
    void missingCompanyContextDeductsFiveWithReason() {
        FitAnalysisConfidence withCtx = FitAnalysisConfidence.evaluate(command("회사 요약"));
        FitAnalysisConfidence noCtx = FitAnalysisConfidence.evaluate(command(null));

        // 기업맥락 부재는 판단값에 무관하므로 소폭(-5) 감점 + 사유 노출(전략 기업 맞춤도만 낮아짐을 안내).
        assertThat(noCtx.score()).isEqualTo(withCtx.score() - 5);
        assertThat(noCtx.reasons()).anyMatch(reason -> reason.contains("기업 분석"));
    }

    @Test
    void blankCompanyContextTreatedAsMissing() {
        assertThat(FitAnalysisConfidence.evaluate(command("   ")).score())
                .isEqualTo(FitAnalysisConfidence.evaluate(command(null)).score());
    }
}
