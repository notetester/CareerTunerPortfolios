package com.careertuner.payment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.GlobalExceptionHandler;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.common.web.FrontendReturnUrlResolver;
import com.careertuner.common.web.SitesFinancialMutation;
import com.careertuner.common.web.SitesFinancialMutationInterceptor;

class SitesFinancialMutationEnvelopeTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CareerTunerProperties props = new CareerTunerProperties();
        props.getApp().setFrontendUrl("https://careertuner.example.com");
        props.getApp().setSitesFrontendUrl("https://sites.example.com");
        var interceptor = new SitesFinancialMutationInterceptor(new FrontendReturnUrlResolver(props));
        mockMvc = MockMvcBuilders.standaloneSetup(new TestFinancialController())
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void blockedMutationUsesStandardApiErrorEnvelope() throws Exception {
        mockMvc.perform(post("/test/financial-mutation")
                        .header(FrontendReturnUrlResolver.FRONTEND_CLIENT_HEADER, "sites"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Sites 백업에서는 금융 거래 기능을 이용할 수 없습니다."));
    }

    @Test
    void encodedMutationPathIsRejectedOrStillGuarded() throws Exception {
        mockMvc.perform(post(URI.create("/test/%66inancial-mutation"))
                        .header(FrontendReturnUrlResolver.FRONTEND_CLIENT_HEADER, "sites"))
                .andExpect(status().is4xxClientError());
    }

    @RestController
    static class TestFinancialController {

        @SitesFinancialMutation
        @PostMapping("/test/financial-mutation")
        ApiResponse<Void> mutate() {
            return ApiResponse.ok();
        }
    }
}
