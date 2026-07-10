package com.careertuner.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

class NavigationLinkPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "//evil.example/path", "/\\evil.example", "/\n/evil.example"})
    void internalPathRejectsScriptProtocolRelativeAndParserConfusion(String value) {
        assertThatThrownBy(() -> NavigationLinkPolicy.optionalInternalPath(value, "링크"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/community/posts/1", "/billing?tab=history", "/#section"})
    void internalPathKeepsLegitimateRoutes(String value) {
        assertThat(NavigationLinkPolicy.optionalInternalPath(value, "링크")).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://careertuner.kro.kr/pricing", "http://localhost:5173/demo", "/applications"})
    void webOrInternalKeepsSupportedAdvertisementTargets(String value) {
        assertThat(NavigationLinkPolicy.optionalWebOrInternal(value, "광고 링크")).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"data:text/html,hello", "javascript:alert(1)", "//evil.example", "https:\\evil.example"})
    void webOrInternalRejectsExecutableOrAmbiguousTargets(String value) {
        assertThatThrownBy(() -> NavigationLinkPolicy.optionalWebOrInternal(value, "광고 링크"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
