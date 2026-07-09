package com.careertuner.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.careertuner.profile.dto.ProfileAnalyzeDraft;

/**
 * 결정적 후처리 + skills 스캔 게이트 (Ollama 불필요).
 */
class ProfileResumePostProcessorTest {

    @Test
    void rejectsHallucinatedSchoolAndCompany() {
        String source = "학력: Seoul National University CS\n경력: Kakao 백엔드";

        List<Map<String, String>> education = ProfileResumePostProcessor.processEducation(
                List.of(
                        Map.of("school", "Seoul National University", "major", "CS",
                                "startDate", "2018-03", "endDate", "2022-02", "status", "졸업"),
                        Map.of("school", "Fake University", "major", "X",
                                "startDate", "2010-01", "endDate", "2014-01", "status", "졸업")),
                source);
        List<Map<String, String>> career = ProfileResumePostProcessor.processCareer(
                List.of(
                        Map.of("company", "Kakao", "role", "백엔드",
                                "startDate", "2022-03", "endDate", "", "tasks", "API", "achievements", ""),
                        Map.of("company", "NotInSource Corp", "role", "Dev",
                                "startDate", "2020-01", "endDate", "2021-01", "tasks", "", "achievements", "")),
                source);

        assertThat(education).hasSize(1);
        assertThat(education.get(0).get("school")).isEqualTo("Seoul National University");
        assertThat(education.get(0).get("period")).isEqualTo("2018-03 - 2022-02");
        assertThat(career).hasSize(1);
        assertThat(career.get(0).get("company")).isEqualTo("Kakao");
        assertThat(career.get(0).get("period")).isEqualTo("2022-03 - 현재");
    }

    @Test
    void datesOnlyYearMonth() {
        assertThat(ProfileResumePostProcessor.normalizeDate("2022-03")).isEqualTo("2022-03");
        assertThat(ProfileResumePostProcessor.normalizeDate("2022.03")).isEmpty();
        assertThat(ProfileResumePostProcessor.normalizeDate("March 2022")).isEmpty();
        assertThat(ProfileResumePostProcessor.normalizeDate(null)).isEmpty();
    }

    @Test
    void formatPeriodMatchesProfileTsx() {
        assertThat(ProfileResumePostProcessor.formatPeriod("2020-01", "2021-12"))
                .isEqualTo("2020-01 - 2021-12");
        assertThat(ProfileResumePostProcessor.formatPeriod("2020-01", ""))
                .isEqualTo("2020-01 - 현재");
        assertThat(ProfileResumePostProcessor.formatPeriod("", "2021-12"))
                .isEqualTo("2021-12");
        assertThat(ProfileResumePostProcessor.formatPeriod("", "")).isEmpty();
    }

    @Test
    void skillsScanNoSentinelAndFindsKnownTokens() {
        String text = "Backend with Java, Spring Boot, React and MySQL. Also Docker.";
        List<String> skills = ProfileKnownSkillsScanner.scan(text);
        assertThat(skills).contains("Java", "Spring Boot", "React", "MySQL", "Docker");
        assertThat(skills).doesNotContain("Job posting analysis");
    }

    @Test
    void skillsScanEmptyWhenNoMatch() {
        assertThat(ProfileKnownSkillsScanner.scan("요리 자격증 보유")).isEmpty();
    }

    @Test
    void structurerDeterministicPath() {
        ProfileResumeStructurer structurer = new ProfileResumeStructurer(
                new tools.jackson.databind.ObjectMapper(),
                false,
                "http://localhost:11434",
                "qwen3:8b",
                8192,
                2048,
                java.time.Duration.ofSeconds(5));
        String source = "Project Alpha with Java at https://github.com/me/alpha";
        ProfileAnalyzeDraft draft = structurer.structureDeterministic(
                source,
                List.of(Map.of("school", "Missing School", "major", "CS",
                        "startDate", "2018-03", "endDate", "2022-02", "status", "졸업")),
                List.of(),
                List.of(Map.of("title", "Project Alpha", "type", "개인", "role", "Dev",
                        "startDate", "2023-01", "endDate", "2023-06",
                        "description", "x", "result", "y")));

        assertThat(draft.education()).asList().isEmpty(); // school not in source
        assertThat(draft.projects()).asList().hasSize(1);
        assertThat(draft.skills()).contains("Java");
        assertThat(draft.portfolioLinks()).contains("https://github.com/me/alpha");
    }
}
