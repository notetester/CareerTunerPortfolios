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
        when(profileService.me(any())).thenReturn(profile());
        when(jobAnalysisService.getJobAnalysis(7L, 11L)).thenReturn(analysis());
        CorrectionContextService service = new CorrectionContextService(
                profileService, jobAnalysisService, new ObjectMapper());
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
                .containsEntry("required_skills", "Java, Spring");
        assertThat(input.userProfileFacts())
                .anyMatch(value -> value.contains("프로젝트") && value.contains("게시판"))
                .anyMatch(value -> value.contains("보유 기술") && value.contains("Java"));
        assertThat(input.toRequestMap()).containsKeys("id", "task_type", "input");
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
                LocalDateTime.now());
    }
}
