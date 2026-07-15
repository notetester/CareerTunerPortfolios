package com.careertuner.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.careertuner.billing.service.BillingService;

class BillingPaymentBoundaryTest {

    @Test
    void billingControllerDoesNotExposeLegacyImmediateGrantMutations() {
        Set<String> methodNames = Arrays.stream(BillingController.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> postMappings = Arrays.stream(BillingController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Stream.concat(
                        Arrays.stream(annotation.value()),
                        Arrays.stream(annotation.path())))
                .collect(Collectors.toSet());

        assertThat(methodNames)
                .doesNotContain("subscribe", "purchaseCredits")
                .contains("cancelSubscription");
        assertThat(postMappings)
                .doesNotContain("/subscribe", "/credits/purchase")
                .contains("/subscription/cancel");
    }

    @Test
    void legacyImmediateGrantPathsAreNotRegisteredInSpringMvc() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new BillingController(mock(BillingService.class)))
                .build();

        mockMvc.perform(post("/api/billing/subscribe"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/billing/credits/purchase"))
                .andExpect(status().isNotFound());
    }
}
