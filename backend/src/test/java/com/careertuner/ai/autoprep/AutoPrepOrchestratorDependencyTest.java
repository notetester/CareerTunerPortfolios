package com.careertuner.ai.autoprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.autoprep.dto.AutoPrepResponse;

class AutoPrepOrchestratorDependencyTest {

    @Test
    void fitIsSkippedWhenRequiredJobStepFails() {
        AutoPrepPlanner planner = mock(AutoPrepPlanner.class);
        AutoPrepAttachmentLoader attachmentLoader = mock(AutoPrepAttachmentLoader.class);
        PrepStepHandler job = mock(PrepStepHandler.class);
        PrepStepHandler fit = mock(PrepStepHandler.class);
        AutoPrepRequest request = new AutoPrepRequest("적합도 분석", 10L, null, null, List.of());

        when(planner.plan(1L, request)).thenReturn(new PrepPlan(
                "CUSTOM_PREP",
                new PrepSlots("테스트 회사", "백엔드", "BASIC", 10L),
                List.of("JOB", "FIT")));
        when(attachmentLoader.load(1L, List.of())).thenReturn(List.of());
        when(job.key()).thenReturn("JOB");
        when(job.enabled()).thenReturn(true);
        when(job.handle(any(), any())).thenReturn(PrepStepResult.failed("JOB", "공고 분석 실패", 1L));
        when(fit.key()).thenReturn("FIT");
        when(fit.enabled()).thenReturn(true);

        AutoPrepOrchestrator orchestrator = new AutoPrepOrchestrator(planner, attachmentLoader, List.of(job, fit));
        try {
            AutoPrepResponse response = orchestrator.run(1L, request);

            assertThat(response.steps())
                    .filteredOn(result -> "FIT".equals(result.key()))
                    .singleElement()
                    .satisfies(result -> {
                        assertThat(result.status()).isEqualTo("SKIPPED");
                        assertThat(result.summary()).contains("JOB");
                    });
            verify(fit, never()).handle(any(), any());
        } finally {
            orchestrator.shutdown();
        }
    }
}
