package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.databind.ObjectMapper;

class BAnalysisJsonValidatorTest {

    private final BAnalysisJsonValidator validator = new BAnalysisJsonValidator(new ObjectMapper());

    @Test
    void validObjectArraysAndEmptyArraysPass() {
        assertThat(validator.validateEvidence("[{\"field\":\"requiredSkills\",\"quote\":\"Java\"}]"))
                .isEqualTo("[{\"field\":\"requiredSkills\",\"quote\":\"Java\"}]");
        assertThat(validator.validateAmbiguousConditions("[{\"condition\":\"experience unclear\",\"assumption\":\"junior\"}]"))
                .isEqualTo("[{\"condition\":\"experience unclear\",\"assumption\":\"junior\"}]");
        assertThat(validator.validateVerifiedFacts("[{\"fact\":\"B2B platform\",\"source\":\"job posting\"}]"))
                .isEqualTo("[{\"fact\":\"B2B platform\",\"source\":\"job posting\"}]");
        assertThat(validator.validateAiInferences("[{\"inference\":\"cloud experience matters\",\"basis\":\"AWS mentioned\"}]"))
                .isEqualTo("[{\"inference\":\"cloud experience matters\",\"basis\":\"AWS mentioned\"}]");

        assertThat(validator.validateEvidence("[]")).isEqualTo("[]");
        assertThat(validator.validateAmbiguousConditions("[]")).isEqualTo("[]");
        assertThat(validator.validateVerifiedFacts("[]")).isEqualTo("[]");
        assertThat(validator.validateAiInferences("[]")).isEqualTo("[]");
    }

    @Test
    void additiveKeysSurviveValidationRoundTrip() {
        // 6단계 canonical contract: 검수 저장 시 validator 가 additive key(factId/sourceKind/
        // sourceRef/evidence, inferenceId/basedOn/confidence, kind=UNKNOWN 마커)를 유실하면 안 된다.
        String verifiedFacts = "[{\"fact\":\"B2B platform\",\"source\":\"job posting\","
                + "\"evidence\":\"B2B platform mentioned\",\"factId\":\"F1\","
                + "\"sourceKind\":\"JOB_POSTING\",\"sourceRef\":\"jobPosting:30#rev2\"}]";
        String aiInferences = "[{\"inference\":\"cloud experience matters\",\"basis\":\"AWS mentioned\","
                + "\"inferenceId\":\"I1\",\"basedOn\":[\"F1\"],\"confidence\":\"MEDIUM\"},"
                + "{\"inference\":\"매출 규모는 현재 입력 자료로 확인되지 않습니다.\",\"basis\":\"공고문에 관련 정보가 없다\","
                + "\"kind\":\"UNKNOWN\",\"topic\":\"매출 규모\"}]";

        assertThat(validator.validateVerifiedFacts(verifiedFacts)).isEqualTo(verifiedFacts);
        assertThat(validator.validateAiInferences(aiInferences)).isEqualTo(aiInferences);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[\"requiredSkills\"]",
            "\"scalar\"",
            "{",
            "[{\"field\":\"requiredSkills\"}]",
            "[{\"field\":\"requiredSkills\",\"quote\":3}]"
    })
    void invalidEvidenceJsonFails(String value) {
        assertThatThrownBy(() -> validator.validateEvidence(value))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("JSON");
    }
}
