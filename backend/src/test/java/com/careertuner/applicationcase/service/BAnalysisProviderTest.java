package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BAnalysisProviderTest {

    @Test
    void toAttemptPathJsonSerializesValidJsonArray() {
        // toString() 은 [LOCAL, LOCAL] 로 무효 JSON — enum 이름만 큰따옴표로 감싸 유효 JSON 으로 직렬화한다.
        assertThat(BAnalysisProvider.toAttemptPathJson(List.of(BAnalysisProvider.LOCAL, BAnalysisProvider.LOCAL)))
                .isEqualTo("[\"LOCAL\",\"LOCAL\"]");
        assertThat(BAnalysisProvider.toAttemptPathJson(List.of(BAnalysisProvider.CLAUDE)))
                .isEqualTo("[\"CLAUDE\"]");
        assertThat(BAnalysisProvider.toAttemptPathJson(List.of())).isNull();
        assertThat(BAnalysisProvider.toAttemptPathJson(null)).isNull();
    }

    @Test
    void parseIsCaseInsensitiveAndEmptyForBlankOrUnknown() {
        assertThat(BAnalysisProvider.parse("claude")).contains(BAnalysisProvider.CLAUDE);
        assertThat(BAnalysisProvider.parse("  OPENAI ")).contains(BAnalysisProvider.OPENAI);
        assertThat(BAnalysisProvider.parse("LOCAL")).contains(BAnalysisProvider.LOCAL);
        // 빈 값·null·알 수 없는 값은 모두 empty — required 경로(strict)는 400, optional 경로(등록)는 blank 만 자동.
        assertThat(BAnalysisProvider.parse("")).isEmpty();
        assertThat(BAnalysisProvider.parse("   ")).isEmpty();
        assertThat(BAnalysisProvider.parse(null)).isEmpty();
        assertThat(BAnalysisProvider.parse("GEMINI")).isEmpty();
    }
}
