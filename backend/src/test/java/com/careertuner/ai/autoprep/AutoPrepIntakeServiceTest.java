package com.careertuner.ai.autoprep;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.correction.ai.CorrectionModelWarmupService;

class AutoPrepIntakeServiceTest {

    private final AutoPrepPlanner planner = mock(AutoPrepPlanner.class);
    private final ApplicationCaseService applicationCaseService = mock(ApplicationCaseService.class);
    private final CorrectionModelWarmupService warmupService = mock(CorrectionModelWarmupService.class);
    private final AutoPrepIntakeService service = new AutoPrepIntakeService(
            planner, applicationCaseService, warmupService);

    @Test
    void intake_warmsForExplicitWriteIntent() {
        AutoPrepRequest request = request(null, List.of());
        when(planner.plan(1L, request)).thenReturn(plan("CUSTOM_PREP", List.of("WRITE")));

        service.intake(1L, request);

        verify(warmupService).warmAsync("AUTO_PREP_WRITE");
    }

    @Test
    void intake_warmsFullPrepWhenCorrectionSourceExists() {
        AutoPrepRequest request = request("자기소개서 원문", List.of());
        when(planner.plan(1L, request)).thenReturn(plan("FULL_PREP", PrepPlan.defaultSteps()));
        when(applicationCaseService.list(1L, null, false)).thenReturn(List.of());

        service.intake(1L, request);

        verify(warmupService).warmAsync("AUTO_PREP_WRITE");
    }

    @Test
    void intake_skipsWarmupWhenWriteIsNotExpected() {
        AutoPrepRequest request = request(null, List.of());
        when(planner.plan(1L, request)).thenReturn(plan("CUSTOM_PREP", List.of("PROFILE")));

        service.intake(1L, request);

        verify(warmupService, never()).warmAsync("AUTO_PREP_WRITE");
    }

    private AutoPrepRequest request(String coverLetterText, List<Long> attachmentFileIds) {
        return new AutoPrepRequest("준비해줘", null, null, coverLetterText, attachmentFileIds);
    }

    private PrepPlan plan(String intent, List<String> steps) {
        return new PrepPlan(intent, new PrepSlots(null, null, null, null), steps);
    }
}
