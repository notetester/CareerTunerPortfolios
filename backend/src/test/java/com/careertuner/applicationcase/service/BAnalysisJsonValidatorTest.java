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
