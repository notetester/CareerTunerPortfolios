package com.careertuner.ai.autoprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.applicationcase.dto.ApplicationCaseExtractionResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.correction.ai.CorrectionModelWarmupService;

class AutoPrepIntakeServiceTest {

    private final AutoPrepPlanner planner = mock(AutoPrepPlanner.class);
    private final ApplicationCaseService applicationCaseService = mock(ApplicationCaseService.class);
    private final CorrectionModelWarmupService warmupService = mock(CorrectionModelWarmupService.class);
    private final AutoPrepCaseCreationService caseCreationService = mock(AutoPrepCaseCreationService.class);
    private final AutoPrepIntakeService service = new AutoPrepIntakeService(
            planner, applicationCaseService, warmupService, caseCreationService);

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

    // ── 공고 첨부 → 지원 건 자동 생성 → 추출 폴링(EXTRACTING) ──

    @Test
    void intake_createsCaseFromJobPostingAttachment_thenExtracting() {
        AutoPrepRequest request = new AutoPrepRequest("면접 준비해줘", null, "BASIC", null, null, List.of(55L));
        when(planner.plan(1L, request)).thenReturn(new PrepPlan("CUSTOM_PREP",
                new PrepSlots(null, null, "BASIC", null), List.of("JOB", "INTERVIEW")));
        when(caseCreationService.createOrReuse(1L, List.of(55L), null)).thenReturn(42L);
        when(applicationCaseService.getLatestJobPostingExtraction(1L, 42L)).thenReturn(extraction("QUEUED"));

        AutoPrepIntakeResponse res = service.intake(1L, request);

        assertThat(res.ready()).isFalse();
        assertThat(res.nextAsk()).isEqualTo("EXTRACTING");
        assertThat(res.applicationCaseId()).isEqualTo(42L);
        verify(caseCreationService).createOrReuse(1L, List.of(55L), null);
    }

    @Test
    void intake_jobPostingAttachmentOverridesPlannerAutoSelectedCase() {
        AutoPrepRequest request = new AutoPrepRequest("면접 준비해줘", null, "BASIC", null, null, List.of(55L));
        when(planner.plan(1L, request)).thenReturn(new PrepPlan("CUSTOM_PREP",
                new PrepSlots("기존 기업", "기존 직무", "BASIC", 7L), List.of("JOB", "INTERVIEW")));
        when(caseCreationService.createOrReuse(1L, List.of(55L), null)).thenReturn(42L);
        when(applicationCaseService.getLatestJobPostingExtraction(1L, 42L)).thenReturn(extraction("QUEUED"));

        AutoPrepIntakeResponse res = service.intake(1L, request);

        assertThat(res.nextAsk()).isEqualTo("EXTRACTING");
        assertThat(res.applicationCaseId()).isEqualTo(42L);
        assertThat(res.plan().slots().applicationCaseId()).isEqualTo(42L);
        assertThat(res.plan().slots().company()).isNull();
        verify(applicationCaseService, never()).getLatestJobPostingExtraction(1L, 7L);
    }

    @Test
    void intake_pollsWhenProvidedCaseStillExtracting() {
        // 프론트가 이미지/PDF 공고로 지원 건을 먼저 만들어 applicationCaseId 로 넘긴 경우 — 추출 중이면 폴링.
        AutoPrepRequest request = new AutoPrepRequest("면접 준비해줘", 99L, "BASIC", null, null);
        when(planner.plan(1L, request)).thenReturn(new PrepPlan("CUSTOM_PREP",
                new PrepSlots(CaseSlotValidator.PLACEHOLDER_COMPANY, CaseSlotValidator.PLACEHOLDER_JOB_TITLE, "BASIC", 99L),
                List.of("JOB", "INTERVIEW")));
        when(applicationCaseService.getLatestJobPostingExtraction(1L, 99L)).thenReturn(extraction("RUNNING"));

        AutoPrepIntakeResponse res = service.intake(1L, request);

        assertThat(res.nextAsk()).isEqualTo("EXTRACTING");
        assertThat(res.applicationCaseId()).isEqualTo(99L);
        verifyNoInteractions(caseCreationService);
    }

    @Test
    void intake_responseLossRetryRehydratesCompletedCaseSlotsFromDurableMapping() {
        AutoPrepRequest request = new AutoPrepRequest(
                "면접 준비해줘", null, "BASIC", null, null, List.of(55L));
        when(planner.plan(1L, request)).thenReturn(new PrepPlan("CUSTOM_PREP",
                new PrepSlots(null, null, "BASIC", null), List.of("JOB", "INTERVIEW")));
        when(caseCreationService.createOrReuse(1L, List.of(55L), null)).thenReturn(42L);
        when(applicationCaseService.getLatestJobPostingExtraction(1L, 42L)).thenReturn(extraction("SUCCEEDED"));
        when(applicationCaseService.get(1L, 42L)).thenReturn(new ApplicationCaseResponse(
                42L, "카카오", "프론트엔드 개발자", null, null, "TEXT", "READY",
                false, false, null, null, null, null));

        AutoPrepIntakeResponse res = service.intake(1L, request);

        assertThat(res.ready()).isTrue();
        assertThat(res.plan().slots().applicationCaseId()).isEqualTo(42L);
        assertThat(res.plan().slots().company()).isEqualTo("카카오");
        assertThat(res.plan().slots().jobTitle()).isEqualTo("프론트엔드 개발자");
    }

    private AutoPrepRequest request(String coverLetterText, List<Long> attachmentFileIds) {
        return new AutoPrepRequest("준비해줘", null, null, coverLetterText, attachmentFileIds);
    }

    private PrepPlan plan(String intent, List<String> steps) {
        return new PrepPlan(intent, new PrepSlots(null, null, null, null), steps);
    }

    private ApplicationCaseExtractionResponse extraction(String status) {
        return new ApplicationCaseExtractionResponse(1L, 42L, null, "TEXT", status, null, null, null,
                null, null, null, false, null, null, null, null, null, null);
    }
}
