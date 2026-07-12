package com.careertuner.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.controller.AnalysisController;
import com.careertuner.billing.policy.RequiresAiCharge;
import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.policy.RequiresConsent;
import com.careertuner.home.controller.HomeController;

class DashboardControllerConsentTest {

    @Test
    void aiSummaryEndpointsRequireAiDataConsentButDeterministicTodoReadDoesNot() throws Exception {
        assertAiDataConsent(DashboardController.class.getDeclaredMethod("summary", AuthUser.class));
        assertAiDataConsent(DashboardController.class.getDeclaredMethod("refresh", AuthUser.class));
        assertAiDataConsent(HomeController.class.getDeclaredMethod("summary", AuthUser.class));

        Method todos = DashboardController.class.getDeclaredMethod("todos", AuthUser.class);
        assertThat(todos.getAnnotation(RequiresConsent.class)).isNull();
    }

    @Test
    void explicitRefreshRequiresMatchingChargeAcknowledgement() throws Exception {
        assertAiCharge(
                DashboardController.class.getDeclaredMethod("refresh", AuthUser.class),
                "DASHBOARD_SUMMARY");
        assertAiCharge(
                AnalysisController.class.getDeclaredMethod("refresh", AuthUser.class),
                "CAREER_TREND");

        Method dashboardRead = DashboardController.class.getDeclaredMethod("summary", AuthUser.class);
        Method analysisRead = AnalysisController.class.getDeclaredMethod("summary", AuthUser.class);
        assertThat(dashboardRead.getAnnotation(RequiresAiCharge.class)).isNull();
        assertThat(analysisRead.getAnnotation(RequiresAiCharge.class)).isNull();
    }

    private static void assertAiDataConsent(Method method) {
        RequiresConsent annotation = method.getAnnotation(RequiresConsent.class);
        assertThat(annotation).as(method.getName()).isNotNull();
        assertThat(annotation.value()).containsExactly(ConsentType.AI_DATA);
    }

    private static void assertAiCharge(Method method, String featureType) {
        RequiresAiCharge annotation = method.getAnnotation(RequiresAiCharge.class);
        assertThat(annotation).as(method.getName()).isNotNull();
        assertThat(annotation.value()).isEqualTo(featureType);
    }
}
