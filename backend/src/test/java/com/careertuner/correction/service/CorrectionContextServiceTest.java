package com.careertuner.correction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.correction.ai.SelfCorrectionInput;
import com.careertuner.fitanalysis.service.FitAnalysisService;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.service.JobAnalysisService;
import com.careertuner.profile.dto.UserProfileResponse;
import com.careertuner.profile.service.ProfileService;

import tools.jackson.databind.ObjectMapper;

class CorrectionContextServiceTest {

    @Test
    @DisplayName("builds the trained input contract from profile and job analysis reads")
    void build_collectsGroundedContext() {
        ProfileService profileService = mock(ProfileService.class);
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        FitAnalysisService fitAnalysisService = mock(FitAnalysisService.class);
        when(profileService.me(any())).thenReturn(profile());
        when(jobAnalysisService.getJobAnalysis(7L, 11L)).thenReturn(analysis());
        FitAnalysisDetailResponse fit = mock(FitAnalysisDetailResponse.class);
        when(fit.status()).thenReturn("SUCCESS");
        when(fit.id()).thenReturn(501L);
        when(fit.fitScore()).thenReturn(68);
        when(fit.missingSkills()).thenReturn("[\"대규모 트래픽\",\"Redis\"]");
        when(fit.strategy()).thenReturn("API 성능 개선 근거를 우선 강조");
        when(fit.model()).thenReturn("fit-model");
        when(fit.promptVersion()).thenReturn("fit-r3");
        when(fit.sourceSnapshot()).thenReturn("{\"profileVersionId\":9001}");
        when(fitAnalysisService.getByApplicationCase(7L, 11L)).thenReturn(fit);
        CorrectionContextService service = new CorrectionContextService(
                profileService, jobAnalysisService, fitAnalysisService, new ObjectMapper());
        ApplicationCase applicationCase = ApplicationCase.builder()
                .id(11L)
                .userId(7L)
                .companyName("테스트회사")
                .jobTitle("백엔드 개발자")
                .build();

        SelfCorrectionInput input = service.build(
                7L, "SELF_INTRO", applicationCase, "원문", "지원 동기를 설명해 주세요");

        assertThat(input.taskType()).isEqualTo("SELF_INTRO_CORRECTION");
        assertThat(input.targetRole()).isEqualTo("백엔드 개발자");
        assertThat(input.jobContext())
                .containsEntry("company", "테스트회사")
                .containsEntry("required_skills", "Java, Spring")
                .containsEntry("fit_analysis_id", 501L)
                .containsEntry("missing_skills", List.of("대규모 트래픽", "Redis"))
                .containsEntry("fit_strategy", "API 성능 개선 근거를 우선 강조");
        assertThat(input.userProfileFacts())
                .anyMatch(value -> value.contains("프로젝트") && value.contains("게시판"))
                .anyMatch(value -> value.contains("보유 기술") && value.contains("Java"));
        assertThat(input.constraints())
                .containsEntry("min_chars", 2)
                .containsEntry("target_chars", 2)
                .containsEntry("preserve_paragraphs", true)
                .containsEntry("preserve_facts_only", true);
        assertThat(input.toRequestMap()).containsKeys("id", "task_type", "input");
        assertThat(input.sourceProvenance())
                .containsKey("profile")
                .containsKey("jobAnalysis")
                .containsKey("fitAnalysis");
        Map<?, ?> fitProvenance = (Map<?, ?>) input.sourceProvenance().get("fitAnalysis");
        assertThat(fitProvenance.get("fitAnalysisId")).isEqualTo(501L);
        assertThat(fitProvenance.get("missingSkills")).isEqualTo(List.of("대규모 트래픽", "Redis"));
    }

    @Test
    void missingFitAnalysisDegradesWithoutBreakingCorrectionContext() {
        ProfileService profileService = mock(ProfileService.class);
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        FitAnalysisService fitAnalysisService = mock(FitAnalysisService.class);
        when(profileService.me(any())).thenReturn(profile());
        when(jobAnalysisService.getJobAnalysis(7L, 11L)).thenReturn(analysis());
        when(fitAnalysisService.getByApplicationCase(7L, 11L)).thenThrow(
                new com.careertuner.common.exception.BusinessException(
                        com.careertuner.common.exception.ErrorCode.NOT_FOUND, "none"));
        CorrectionContextService service = new CorrectionContextService(
                profileService, jobAnalysisService, fitAnalysisService, new ObjectMapper());

        SelfCorrectionInput input = service.build(
                7L,
                "RESUME",
                ApplicationCase.builder().id(11L).userId(7L).jobTitle("백엔드").build(),
                "원문",
                null);

        assertThat(input.jobContext()).doesNotContainKeys("fit_analysis_id", "missing_skills", "fit_strategy");
        assertThat(input.sourceProvenance()).doesNotContainKey("fitAnalysis");
    }

    private UserProfileResponse profile() {
        return new UserProfileResponse(
                1L,
                7L,
                "백엔드 개발자",
                "IT",
                null,
                null,
                List.of(Map.of("name", "게시판", "role", "API 구현")),
                List.of("Java", "Spring"),
                null,
                null,
                null,
                "이력서 내용",
                "자기소개 내용",
                null,
                1,
                LocalDateTime.now());
    }

    private JobAnalysisResponse analysis() {
        return new JobAnalysisResponse(
                2L,
                11L,
                3L,
                1,
                "정규직",
                "신입",
                "Java, Spring",
                "MySQL",
                "REST API 개발",
                "협업 경험",
                "MEDIUM",
                "백엔드 채용",
                null,
                null,
                null,
                null,
                // provenance 6 (requested/actual provider·model·fallbackUsed·attemptPath·runMode)
                null, null, null, null, null, null,
                LocalDateTime.now());
    }
}
