package com.careertuner.correction.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.common.security.AuthUser;
import com.careertuner.correction.ai.SelfCorrectionInput;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.service.JobAnalysisService;
import com.careertuner.profile.dto.UserProfileResponse;
import com.careertuner.profile.service.ProfileService;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CorrectionContextService {

    private static final int MAX_FACTS = 30;
    private static final int MAX_FACT_LENGTH = 1200;

    private final ProfileService profileService;
    private final JobAnalysisService jobAnalysisService;
    private final ObjectMapper objectMapper;

    public SelfCorrectionInput build(
            Long userId,
            String correctionType,
            ApplicationCase applicationCase,
            String originalText,
            String questionText
    ) {
        UserProfileResponse profile = profileService.me(new AuthUser(userId, null, "USER"));
        JobAnalysisResponse analysis = applicationCase == null
                ? null
                : jobAnalysisService.getJobAnalysis(userId, applicationCase.getId());

        Map<String, Object> jobContext = jobContext(applicationCase, analysis, questionText);
        String targetRole = firstNonBlank(
                applicationCase == null ? null : applicationCase.getJobTitle(),
                profile.desiredJob(),
                "지원 직무");
        return new SelfCorrectionInput(
                "runtime-" + UUID.randomUUID(),
                SelfCorrectionInput.taskType(correctionType),
                originalText,
                targetRole,
                jobContext,
                profileFacts(profile, originalText),
                SelfCorrectionInput.defaultConstraints(
                        SelfCorrectionInput.taskType(correctionType), originalText));
    }

    private Map<String, Object> jobContext(
            ApplicationCase applicationCase,
            JobAnalysisResponse analysis,
            String questionText
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (applicationCase != null) {
            put(context, "company", applicationCase.getCompanyName());
            put(context, "job_title", applicationCase.getJobTitle());
        }
        if (analysis != null) {
            put(context, "employment_type", analysis.employmentType());
            put(context, "experience_level", analysis.experienceLevel());
            put(context, "required_skills", analysis.requiredSkills());
            put(context, "preferred_skills", analysis.preferredSkills());
            put(context, "duties", analysis.duties());
            put(context, "qualifications", analysis.qualifications());
            put(context, "summary", analysis.summary());
        }
        put(context, "question", questionText);
        return Map.copyOf(context);
    }

    private List<String> profileFacts(UserProfileResponse profile, String originalText) {
        List<String> facts = new ArrayList<>();
        addFact(facts, "희망 직무", profile.desiredJob());
        addFact(facts, "희망 산업", profile.desiredIndustry());
        addFact(facts, "학력", profile.education());
        addFact(facts, "경력", profile.career());
        addFact(facts, "프로젝트", profile.projects());
        addFact(facts, "보유 기술", profile.skills());
        addFact(facts, "자격증", profile.certificates());
        addFact(facts, "언어", profile.languages());
        if (!sameText(profile.resumeText(), originalText)) {
            addFact(facts, "이력서", profile.resumeText());
        }
        if (!sameText(profile.selfIntro(), originalText)) {
            addFact(facts, "자기소개서", profile.selfIntro());
        }
        return List.copyOf(facts);
    }

    private void addFact(List<String> facts, String label, Object value) {
        if (value == null || facts.size() >= MAX_FACTS) {
            return;
        }
        String text = value instanceof String string ? string : json(value);
        if (text == null || text.isBlank()) {
            return;
        }
        facts.add(label + ": " + truncate(text.trim(), MAX_FACT_LENGTH));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            return String.valueOf(value);
        }
    }

    private void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, truncate(value.trim(), MAX_FACT_LENGTH));
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean sameText(String left, String right) {
        return left != null && right != null && left.trim().equals(right.trim());
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
