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
import com.careertuner.correction.dto.CorrectionInterviewSourceResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.service.FitAnalysisService;
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
    private final FitAnalysisService fitAnalysisService;
    private final ObjectMapper objectMapper;

    public SelfCorrectionInput build(
            Long userId,
            String correctionType,
            ApplicationCase applicationCase,
            String originalText,
            String questionText
    ) {
        return build(userId, correctionType, applicationCase, originalText, questionText, null);
    }

    public SelfCorrectionInput build(
            Long userId,
            String correctionType,
            ApplicationCase applicationCase,
            String originalText,
            String questionText,
            CorrectionInterviewSourceResponse interviewSource
    ) {
        UserProfileResponse profile = profileService.me(new AuthUser(userId, null, "USER"));
        JobAnalysisResponse analysis = applicationCase == null
                ? null
                : jobAnalysisService.getJobAnalysis(userId, applicationCase.getId());
        FitAnalysisDetailResponse fitAnalysis = latestUsableFitAnalysis(userId, applicationCase);

        Map<String, Object> jobContext = jobContext(
                applicationCase, analysis, fitAnalysis, questionText, interviewSource);
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
                        SelfCorrectionInput.taskType(correctionType), originalText),
                sourceProvenance(profile, analysis, fitAnalysis, interviewSource));
    }

    private Map<String, Object> jobContext(
            ApplicationCase applicationCase,
            JobAnalysisResponse analysis,
            FitAnalysisDetailResponse fitAnalysis,
            String questionText,
            CorrectionInterviewSourceResponse interviewSource
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
        if (fitAnalysis != null) {
            put(context, "fit_analysis_id", fitAnalysis.id());
            put(context, "fit_score", fitAnalysis.fitScore());
            put(context, "missing_skills", parseStringList(fitAnalysis.missingSkills()));
            put(context, "fit_strategy", fitAnalysis.strategy());
            put(context, "apply_decision", fitAnalysis.applyDecision());
        }
        if (interviewSource != null) {
            put(context, "interview_answer_score", interviewSource.score());
            put(context, "interview_feedback", interviewSource.feedback());
        }
        put(context, "question", questionText);
        return Map.copyOf(context);
    }

    private FitAnalysisDetailResponse latestUsableFitAnalysis(Long userId, ApplicationCase applicationCase) {
        if (applicationCase == null) {
            return null;
        }
        try {
            FitAnalysisDetailResponse fit = fitAnalysisService.getByApplicationCase(userId, applicationCase.getId());
            return fit != null && ("SUCCESS".equals(fit.status()) || "FALLBACK".equals(fit.status()))
                    ? fit
                    : null;
        } catch (com.careertuner.common.exception.BusinessException exception) {
            if (exception.getErrorCode() == com.careertuner.common.exception.ErrorCode.NOT_FOUND) {
                return null;
            }
            throw exception;
        }
    }

    private Map<String, Object> sourceProvenance(
            UserProfileResponse profile,
            JobAnalysisResponse analysis,
            FitAnalysisDetailResponse fit,
            CorrectionInterviewSourceResponse interviewSource
    ) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("contextVersion", "e-correction-context-v1");

        Map<String, Object> profileSource = new LinkedHashMap<>();
        put(profileSource, "profileId", profile.id());
        put(profileSource, "profileVersionNo", profile.versionNo());
        put(profileSource, "profileUpdatedAt", profile.updatedAt());
        source.put("profile", Map.copyOf(profileSource));

        if (analysis != null) {
            Map<String, Object> jobSource = new LinkedHashMap<>();
            put(jobSource, "jobAnalysisId", analysis.id());
            put(jobSource, "jobPostingId", analysis.jobPostingId());
            put(jobSource, "jobPostingRevision", analysis.jobPostingRevision());
            put(jobSource, "createdAt", analysis.createdAt());
            source.put("jobAnalysis", Map.copyOf(jobSource));
        }
        if (fit != null) {
            Map<String, Object> fitSource = new LinkedHashMap<>();
            put(fitSource, "fitAnalysisId", fit.id());
            put(fitSource, "status", fit.status());
            put(fitSource, "model", fit.model());
            put(fitSource, "promptVersion", fit.promptVersion());
            put(fitSource, "createdAt", fit.createdAt());
            put(fitSource, "missingSkills", parseStringList(fit.missingSkills()));
            put(fitSource, "strategy", fit.strategy());
            put(fitSource, "sourceSnapshot", parseJsonObject(fit.sourceSnapshot()));
            source.put("fitAnalysis", Map.copyOf(fitSource));
        }
        if (interviewSource != null) {
            Map<String, Object> interview = new LinkedHashMap<>();
            put(interview, "answerId", interviewSource.sourceRefId());
            put(interview, "questionId", interviewSource.questionId());
            put(interview, "sessionId", interviewSource.sessionId());
            put(interview, "answeredAt", interviewSource.answeredAt());
            source.put("interviewAnswer", Map.copyOf(interview));
        }
        return Map.copyOf(source);
    }

    private Object parseJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, Map.class);
        } catch (JacksonException ignored) {
            return Map.of("raw", truncate(value, MAX_FACT_LENGTH));
        }
    }

    private List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            var node = objectMapper.readTree(value);
            if (node.isArray()) {
                List<String> values = new ArrayList<>();
                for (var item : node) {
                    String text = item.asText("").trim();
                    if (!text.isBlank()) values.add(text);
                }
                return List.copyOf(values);
            }
        } catch (JacksonException ignored) {
            // 구버전 CSV 저장값은 아래에서 처리한다.
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
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

    private void put(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String string) {
            put(target, key, string);
            return;
        }
        if (value instanceof java.time.temporal.TemporalAccessor) {
            target.put(key, value.toString());
            return;
        }
        if (value instanceof java.util.Collection<?> collection && collection.isEmpty()) {
            return;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return;
        }
        target.put(key, value);
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
