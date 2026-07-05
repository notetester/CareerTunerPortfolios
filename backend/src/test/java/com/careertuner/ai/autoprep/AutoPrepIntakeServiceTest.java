package com.careertuner.ai.autoprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
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

    // ── 버그1: placeholder 지원 건 게이트(③이 ④와 같은 CaseSlotValidator 를 쓴다) ──

    @Test
    void intake_blocksWhenResolvedCaseHasPlaceholderCompany() {
        AutoPrepRequest request = request(null, List.of());
        when(planner.plan(1L, request)).thenReturn(new PrepPlan("CUSTOM_PREP",
                new PrepSlots(CaseSlotValidator.PLACEHOLDER_COMPANY, "백엔드 개발자", "BASIC", 7L),
                List.of("JOB", "INTERVIEW")));

        AutoPrepIntakeResponse res = service.intake(1L, request);

        assertThat(res.ready()).isFalse();
        assertThat(res.nextAsk()).isNull();
        assertThat(res.message()).contains("회사명").doesNotContain(CaseSlotValidator.PLACEHOLDER_COMPANY);
    }

    @Test
    void intake_blocksWhenResolvedCaseHasBlankJobTitle() {
        AutoPrepRequest request = request(null, List.of());
        when(planner.plan(1L, request)).thenReturn(new PrepPlan("CUSTOM_PREP",
                new PrepSlots("네이버", "  ", "BASIC", 7L),
                List.of("JOB", "INTERVIEW")));

        AutoPrepIntakeResponse res = service.intake(1L, request);

        assertThat(res.ready()).isFalse();
        assertThat(res.nextAsk()).isNull();
        assertThat(res.message()).contains("직무명").doesNotContain("네이버 ");
    }

    @Test
    void intake_readyMessageNeverInterpolatesPlaceholder() {
        // 게이트를 우회하는 조합이 생겨도 보간 방어가 placeholder 를 문장에 싣지 않는다(이중 방어).
        AutoPrepRequest request = new AutoPrepRequest("커뮤니티 준비해줘", null, null, null, List.of());
        when(planner.plan(1L, request)).thenReturn(new PrepPlan("CUSTOM_PREP",
                new PrepSlots(CaseSlotValidator.PLACEHOLDER_COMPANY, CaseSlotValidator.PLACEHOLDER_JOB_TITLE,
                        null, null),
                List.of("COMMUNITY")));

        AutoPrepIntakeResponse res = service.intake(1L, request);

        assertThat(res.ready()).isTrue();
        assertThat(res.message())
                .doesNotContain(CaseSlotValidator.PLACEHOLDER_COMPANY)
                .doesNotContain(CaseSlotValidator.PLACEHOLDER_JOB_TITLE)
                .contains("선택한 내용");
    }

    @Test
    void intake_proceedsWhenResolvedCaseHasCleanSlots() {
        AutoPrepRequest request = new AutoPrepRequest("면접 준비해줘", 7L, "BASIC", null, List.of());
        when(planner.plan(1L, request)).thenReturn(new PrepPlan("CUSTOM_PREP",
                new PrepSlots("네이버", "백엔드 개발자", "BASIC", 7L),
                List.of("JOB", "INTERVIEW")));

        AutoPrepIntakeResponse res = service.intake(1L, request);

        assertThat(res.ready()).isTrue();
        assertThat(res.message()).contains("네이버 백엔드 개발자");
    }

    private AutoPrepRequest request(String coverLetterText, List<Long> attachmentFileIds) {
        return new AutoPrepRequest("준비해줘", null, null, coverLetterText, attachmentFileIds);
    }

    private PrepPlan plan(String intent, List<String> steps) {
        return new PrepPlan(intent, new PrepSlots(null, null, null, null), steps);
    }
}
