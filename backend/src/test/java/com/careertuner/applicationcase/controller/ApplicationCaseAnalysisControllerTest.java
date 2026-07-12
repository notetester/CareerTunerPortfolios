package com.careertuner.applicationcase.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.jobposting.service.JobPostingUploadLimitPolicy;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.exception.GlobalExceptionHandler;
import com.careertuner.common.security.AuthUser;

/**
 * strict 재분석 EP 의 HTTP 계약 검증 — 서비스가 provider 를 필수로 강제해 던진 INVALID_INPUT 이
 * {@link GlobalExceptionHandler} 를 거쳐 {@code ApiResponse} envelope 형태의 400 으로 나오는지 확인한다
 * (서비스 단위 테스트로는 envelope 를 보장할 수 없어 web 계층에서 별도 검증).
 */
class ApplicationCaseAnalysisControllerTest {

    private final ApplicationCaseService service = mock(ApplicationCaseService.class);
    private final JobPostingUploadLimitPolicy uploadLimitPolicy = mock(JobPostingUploadLimitPolicy.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ApplicationCaseController(service, uploadLimitPolicy))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(AuthUser.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return new AuthUser(1L, "test@careertuner.dev", "USER");
                    }
                })
                .build();
    }

    @Test
    void jobAnalysisWithoutProviderReturnsEnvelope400() throws Exception {
        when(service.createJobAnalysis(eq(1L), eq(10L), isNull()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "재분석할 모델(provider)을 지정해 주세요."));

        mockMvc.perform(post("/api/application-cases/10/job-analysis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void jobAnalysisWithInvalidProviderReturnsEnvelope400() throws Exception {
        when(service.createJobAnalysis(eq(1L), eq(10L), eq("GEMINI")))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 분석 모델입니다: GEMINI"));

        mockMvc.perform(post("/api/application-cases/10/job-analysis").param("provider", "GEMINI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void companyAnalysisWithInvalidProviderReturnsEnvelope400() throws Exception {
        when(service.createCompanyAnalysis(eq(1L), eq(10L), eq("BAD")))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 분석 모델입니다: BAD"));

        mockMvc.perform(post("/api/application-cases/10/company-analysis").param("provider", "BAD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void uploadLimitReturnsCurrentMaxBytes() throws Exception {
        when(uploadLimitPolicy.currentMaxBytes()).thenReturn(10L * 1024 * 1024);

        mockMvc.perform(get("/api/application-cases/upload-limit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maxBytes").value(10L * 1024 * 1024));
    }
}
