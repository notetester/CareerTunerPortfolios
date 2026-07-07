package com.careertuner.applicationcase.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.GeneratedCompanyAnalysis;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.GeneratedJobAnalysis;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer;
import com.careertuner.companyanalysis.service.CompanyAnalysisService;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;
import com.careertuner.fitanalysis.ai.FitAnalysisAiCommand;
import com.careertuner.fitanalysis.ai.FitAnalysisAiResult;
import com.careertuner.fitanalysis.ai.FitAnalysisConfidence;
import com.careertuner.fitanalysis.ai.FitConditionMatch;
import com.careertuner.fitanalysis.ai.FitLearningRoadmapItem;
import com.careertuner.fitanalysis.ai.MockFitAnalysisAiService;
import com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog;
import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisLearningTask;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationCaseAutoPipelineService {

    private static final String MODEL = "self-rules-v1";
    private static final String FEATURE_PIPELINE = "APPLICATION_CASE_PIPELINE";
    private static final String FEATURE_JOB_ANALYSIS = "JOB_ANALYSIS";
    private static final String FEATURE_COMPANY_RESEARCH = "COMPANY_RESEARCH";
    private static final String FEATURE_FIT_ANALYSIS = "FIT_ANALYSIS";
    private static final String FEATURE_INTERVIEW_QUESTION = "INTERVIEW_QUESTION_GEN";
    private static final Pattern YEARS_PATTERN = Pattern.compile("(?i)(\\d+)\\+?\\s*(?:years|yrs|년)");
    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Spring", "Spring Boot", "MyBatis", "JPA", "SQL", "MySQL", "PostgreSQL",
            "Redis", "Kafka", "RabbitMQ", "Docker", "Kubernetes", "AWS", "Azure", "GCP",
            "Linux", "React", "TypeScript", "JavaScript", "Vue", "Node.js", "Python",
            "Django", "FastAPI", "REST", "GraphQL", "Git", "CI/CD", "Jenkins", "GitHub Actions",
            "JUnit", "Mockito", "Testing", "Monitoring", "Prometheus", "Grafana", "Elasticsearch",
            "Security", "OAuth", "JWT", "Agile", "Scrum");

    private final ApplicationCaseMapper applicationCaseMapper;
    private final JobAnalysisMapper jobAnalysisMapper;
    private final CompanyAnalysisMapper companyAnalysisMapper;
    private final FitAnalysisMapper fitAnalysisMapper;
    private final InterviewMapper interviewMapper;
    private final MockFitAnalysisAiService mockFitAnalysisAiService;
    private final ObjectMapper objectMapper;
    private final BAnalysisGenerationService bAnalysisGenerationService;
    private final BCompanyAnalysisCanonicalizer companyAnalysisCanonicalizer;
    private final CompanyAnalysisService companyAnalysisService;
    /** 런타임 설정 오버라이드용(application-case.auto-pipeline.enabled). 미설정 시 @Value 기본값을 쓴다. */
    private final com.careertuner.runtimesetting.service.RuntimeSettingService runtimeSettingService;
    /** 활동 리워드 적립(지원 건 분석 완료 시 APPLICATION_CASE_READY). 규칙 off 면 미적립. */
    private final com.careertuner.reward.service.RewardService rewardService;

    @Value("${careertuner.application-case.auto-pipeline.enabled:true}")
    private boolean enabled = true;

    /**
     * 자동 파이프라인 활성 여부 — 런타임 설정 콘솔의 key {@code application-case.auto-pipeline.enabled} 를
     * 우선 참조하고, 없으면 @Value 기본값을 쓴다(운영 중 재배포 없이 on/off). runtime_setting 실소비처.
     */
    private boolean autoPipelineEnabled() {
        return runtimeSettingService.getBoolean("application-case.auto-pipeline.enabled", enabled);
    }

    public void runAfterExtractionPass(Long userId,
                                       Long applicationCaseId,
                                       Long jobPostingId,
                                       Integer jobPostingRevision,
                                       String postingText) {
        if (!autoPipelineEnabled() || isBlank(postingText)) {
            return;
        }

        ApplicationCase applicationCase = applicationCaseMapper.findApplicationCaseByIdAndUserId(
                applicationCaseId,
                userId);
        if (applicationCase == null) {
            recordFailure(userId, applicationCaseId, FEATURE_PIPELINE, "Application case not found.");
            return;
        }

        String previousStatus = applicationCase.getStatus();
        boolean statusStarted = markAnalyzingIfRunnable(userId, applicationCaseId, previousStatus);
        try {
            GeneratedJobAnalysis generatedJob = bAnalysisGenerationService.generateJobAnalysis(applicationCase, postingText);
            JobAnalysis jobAnalysis = createJobAnalysis(applicationCase, jobPostingId, jobPostingRevision, generatedJob);
            // flag ON 이면 사용자 직접 경로(CompanyAnalysisService)와 동일한 웹검색 로직으로 WEB evidence 를 모아
            // R1 생성(공고+웹)과 저장 gate(2소스)에 넘긴다. flag OFF·키 미설정·검색 실패면 빈 목록 → 공고-only(D-4c).
            List<CompanyWebEvidence> companyWebEvidence = companyAnalysisService.collectWebEvidence(applicationCase);
            GeneratedCompanyAnalysis generatedCompany =
                    bAnalysisGenerationService.generateCompanyAnalysis(applicationCase, postingText, companyWebEvidence);
            createCompanyAnalysis(applicationCase, jobPostingId, jobPostingRevision, generatedCompany, postingText, companyWebEvidence);
            createFitAnalysis(userId, applicationCaseId);
            createInterviewPrep(applicationCase, jobAnalysis);
            if (statusStarted) {
                applicationCaseMapper.markReadyAfterAnalysis(applicationCaseId, userId, previousStatus);
                // 지원 건 분석 완료 리워드(규칙 on 일 때만). 예외를 흡수해 파이프라인 상태에 영향 없게 한다.
                grantRewardSafely(userId, "APPLICATION_CASE_READY", "APPLICATION_CASE", applicationCaseId);
            }
        } catch (RuntimeException ex) {
            if (statusStarted) {
                applicationCaseMapper.restoreAnalysisStatus(applicationCaseId, userId, previousStatus);
            }
            recordFailure(userId, applicationCaseId, FEATURE_PIPELINE, safeMessage(ex));
            log.warn("Self AI application-case pipeline failed. applicationCaseId={}", applicationCaseId, ex);
        }
    }

    /** 리워드 적립은 본 파이프라인 실패로 이어지지 않도록 예외를 흡수한다. */
    private void grantRewardSafely(Long userId, String eventCode, String refType, Long refId) {
        try {
            rewardService.grant(userId, eventCode, refType, refId);
        } catch (RuntimeException e) {
            log.warn("리워드 적립 실패 event={} userId={} : {}", eventCode, userId, e.getMessage());
        }
    }

    private boolean markAnalyzingIfRunnable(Long userId, Long applicationCaseId, String previousStatus) {
        if (!"DRAFT".equals(previousStatus) && !"READY".equals(previousStatus)) {
            return false;
        }
        return applicationCaseMapper.markAnalysisStarted(applicationCaseId, userId, previousStatus) == 1;
    }

    private JobAnalysis createJobAnalysis(ApplicationCase applicationCase,
                                          Long jobPostingId,
                                          Integer jobPostingRevision,
                                          GeneratedJobAnalysis generated) {
        JobAnalysisPayload payload = generated.payload();
        JobAnalysis jobAnalysis = JobAnalysis.builder()
                .applicationCaseId(applicationCase.getId())
                .jobPostingId(jobPostingId)
                .jobPostingRevision(jobPostingRevision)
                .employmentType(payload.employmentType())
                .experienceLevel(payload.experienceLevel())
                .requiredSkills(payload.requiredSkills())
                .preferredSkills(payload.preferredSkills())
                .duties(payload.duties())
                .qualifications(payload.qualifications())
                .difficulty(payload.difficulty())
                .summary(payload.summary())
                .evidence(payload.evidence())
                .ambiguousConditions(payload.ambiguousConditions())
                .build();
        jobAnalysisMapper.insertJobAnalysis(jobAnalysis);
        recordFallbackIfNeeded(applicationCase.getUserId(), applicationCase.getId(), FEATURE_JOB_ANALYSIS, generated);
        recordSuccess(applicationCase.getUserId(), applicationCase.getId(), FEATURE_JOB_ANALYSIS, payload.usage());
        return jobAnalysis;
    }

    private void createCompanyAnalysis(ApplicationCase applicationCase,
                                       Long jobPostingId,
                                       Integer jobPostingRevision,
                                       GeneratedCompanyAnalysis generated,
                                       String postingText,
                                       List<CompanyWebEvidence> webEvidence) {
        // 사용자 직접 생성 경로(CompanyAnalysisService)와 동일한 canonicalizer 를 공유한다
        // (evidence gate 2소스[공고+WEB], ID/sourceKind/sourceRef 보정, unknowns 접기, sources 통일).
        // webEvidence 가 빈 목록이면 7-param 은 기존 공고-only 6-param 과 동일 결과다(D-2 계약).
        CompanyAnalysisPayload payload = companyAnalysisCanonicalizer.canonicalizeForStorage(
                generated.payload(),
                jobPostingId,
                jobPostingRevision,
                postingText,
                applicationCase.getCompanyName(),
                applicationCase.getJobTitle(),
                webEvidence).payload();
        LocalDateTime checkedAt = LocalDateTime.now();
        CompanyAnalysis companyAnalysis = CompanyAnalysis.builder()
                .applicationCaseId(applicationCase.getId())
                .jobPostingId(jobPostingId)
                .jobPostingRevision(jobPostingRevision)
                .companySummary(payload.companySummary())
                .recentIssues(payload.recentIssues())
                .industry(compactColumnText(payload.industry(), 100))
                .competitors(payload.competitors())
                .interviewPoints(payload.interviewPoints())
                .sources(payload.sources())
                .verifiedFacts(payload.verifiedFacts())
                .aiInferences(payload.aiInferences())
                .sourceType("JOB_POSTING")
                .checkedAt(checkedAt)
                .refreshRecommendedAt(checkedAt.plusDays(30))
                .build();
        companyAnalysisMapper.insertCompanyAnalysis(companyAnalysis);
        recordFallbackIfNeeded(applicationCase.getUserId(), applicationCase.getId(), FEATURE_COMPANY_RESEARCH, generated);
        recordSuccess(applicationCase.getUserId(), applicationCase.getId(), FEATURE_COMPANY_RESEARCH, payload.usage());
    }

    private void createFitAnalysis(Long userId, Long applicationCaseId) {
        FitAnalysisGenerationSource source = fitAnalysisMapper.findGenerationSource(userId, applicationCaseId);
        if (source == null) {
            recordFailure(userId, applicationCaseId, FEATURE_FIT_ANALYSIS, "Fit analysis source not found.");
            return;
        }

        FitAnalysisAiCommand command = new FitAnalysisAiCommand(
                source.getCompanyName(),
                source.getJobTitle(),
                parseList(source.getRequiredSkills()),
                parseList(source.getPreferredSkills()),
                source.getDuties(),
                parseList(source.getProfileSkills()),
                parseList(source.getProfileCertificates()),
                source.getDesiredJob());
        FitAnalysisResult previous = fitAnalysisMapper.findLatestByUserIdAndApplicationCaseId(userId, applicationCaseId);
        FitAnalysisAiResult ai = mockFitAnalysisAiService.generate(command);
        FitAnalysisConfidence confidence = FitAnalysisConfidence.evaluate(command);

        FitAnalysisResult row = FitAnalysisResult.builder()
                .applicationCaseId(applicationCaseId)
                .fitScore(ai.fitScore())
                .matchedSkills(toJson(ai.matchedSkills()))
                .missingSkills(toJson(ai.missingSkills()))
                .recommendedStudy(toJson(ai.recommendedStudy()))
                .recommendedCertificates(toJson(ai.recommendedCertificates()))
                .strategy(ai.strategy())
                .sourceSnapshot(toJson(sourceSnapshot(source)))
                .scoreBasis(toJson(ai.scoreBasis()))
                .gapRecommendations(toJson(ai.gapRecommendations()))
                .certificateRecommendations(toJson(ai.certificateRecommendations()))
                .strategyActions(toJson(ai.strategyActions()))
                .conditionMatrix(toJson(ai.conditionMatrix()))
                .analysisConfidence(toJson(confidence))
                .applyDecision(toJson(ai.applyDecision()))
                .model(MODEL)
                .promptVersion(FitAnalysisPromptCatalog.VERSION)
                .status(ai.status())
                .errorMessage(ai.errorMessage())
                .build();
        fitAnalysisMapper.insertFitAnalysis(row);
        fitAnalysisMapper.insertHistory(
                row.getId(),
                applicationCaseId,
                previous == null ? null : previous.getFitScore(),
                row.getFitScore(),
                toJson(historyDiff(previous, row)));

        int conditionOrder = 1;
        for (FitConditionMatch condition : ai.conditionMatrix()) {
            fitAnalysisMapper.insertConditionMatch(
                    row.getId(),
                    condition,
                    severity(condition),
                    conditionOrder++);
        }
        for (FitLearningRoadmapItem item : ai.learningRoadmap()) {
            fitAnalysisMapper.insertLearningTask(FitAnalysisLearningTask.builder()
                    .fitAnalysisId(row.getId())
                    .skill(item.skill())
                    .title(item.title())
                    .practiceTask(item.practiceTask())
                    .expectedDuration(item.expectedDuration())
                    .priority(item.priority())
                    .sortOrder(item.sortOrder())
                    .build());
        }
        recordSuccess(userId, applicationCaseId, FEATURE_FIT_ANALYSIS, estimateTokens(command), 300);
    }

    private void createInterviewPrep(ApplicationCase applicationCase, JobAnalysis jobAnalysis) {
        InterviewSession session = InterviewSession.builder()
                .applicationCaseId(applicationCase.getId())
                .mode("JOB")
                .startedAt(LocalDateTime.now())
                .build();
        interviewMapper.insertSession(session);

        List<String> requiredSkills = parseList(jobAnalysis.getRequiredSkills());
        List<String> preferredSkills = parseList(jobAnalysis.getPreferredSkills());
        List<String> questions = interviewQuestions(applicationCase, requiredSkills, preferredSkills);
        int order = 0;
        for (String question : questions) {
            interviewMapper.insertQuestion(InterviewQuestion.builder()
                    .interviewSessionId(session.getId())
                    .question(question)
                    .questionType(questionType(order))
                    .sortOrder(order++)
                    .build());
        }
        recordSuccess(
                applicationCase.getUserId(),
                applicationCase.getId(),
                FEATURE_INTERVIEW_QUESTION,
                estimateLength(requiredSkills) + estimateLength(preferredSkills),
                estimateLength(questions));
    }

    private List<String> interviewQuestions(ApplicationCase applicationCase,
                                            List<String> requiredSkills,
                                            List<String> preferredSkills) {
        String role = defaultText(applicationCase.getJobTitle(), "this role");
        String company = defaultText(applicationCase.getCompanyName(), "the company");
        String primarySkill = requiredSkills.isEmpty() ? "the core requirement" : requiredSkills.get(0);
        String secondarySkill = requiredSkills.size() > 1 ? requiredSkills.get(1)
                : preferredSkills.isEmpty() ? "collaboration" : preferredSkills.get(0);
        return List.of(
                "Why are you applying for " + role + " at " + company + "?",
                "Which project best proves your experience with " + primarySkill + "?",
                "How would you approach a production issue related to " + secondarySkill + "?",
                "What tradeoff would you make if delivery speed conflicted with maintainability?",
                "Which requirement in this posting is your biggest gap, and how are you closing it?",
                "What questions would you ask the interviewer to validate team expectations?");
    }

    private String questionType(int order) {
        return switch (order) {
            case 1, 2 -> "TECH";
            case 3, 4 -> "SITUATION";
            default -> "EXPECTED";
        };
    }

    private List<String> extractRequiredSkills(String text) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase(Locale.ROOT))) {
                found.add(skill);
            }
        }
        if (found.isEmpty()) {
            found.add("Job posting analysis");
        }
        return found.stream().limit(10).toList();
    }

    private List<String> extractPreferredSkills(String text, List<String> requiredSkills) {
        String preferredText = extractSection(text, List.of("preferred", "nice to have", "plus", "우대"));
        LinkedHashSet<String> found = new LinkedHashSet<>();
        String lower = preferredText.toLowerCase(Locale.ROOT);
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase(Locale.ROOT)) && !containsIgnoreCase(requiredSkills, skill)) {
                found.add(skill);
            }
        }
        return found.stream().limit(6).toList();
    }

    private String extractSection(String text, List<String> markers) {
        if (isBlank(text)) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty() && markers.stream().anyMatch(marker -> lower.contains(marker.toLowerCase(Locale.ROOT)))) {
                lines.add(trimmed);
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines.subList(0, Math.min(6, lines.size())));
    }

    private String employmentType(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("intern")) {
            return "INTERN";
        }
        if (lower.contains("contract")) {
            return "CONTRACT";
        }
        if (lower.contains("part-time") || lower.contains("part time")) {
            return "PART_TIME";
        }
        return "FULL_TIME";
    }

    private String experienceLevel(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher matcher = YEARS_PATTERN.matcher(text);
        if (lower.contains("senior") || lower.contains("lead") || (matcher.find() && Integer.parseInt(matcher.group(1)) >= 5)) {
            return "SENIOR";
        }
        if (lower.contains("junior") || lower.contains("entry") || lower.contains("new grad")) {
            return "JUNIOR";
        }
        return "MID";
    }

    private String difficulty(String text, List<String> requiredSkills) {
        String level = experienceLevel(text);
        if ("SENIOR".equals(level) || requiredSkills.size() >= 8) {
            return "HARD";
        }
        if ("JUNIOR".equals(level) && requiredSkills.size() <= 4) {
            return "EASY";
        }
        return "NORMAL";
    }

    private String industry(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("fintech") || lower.contains("payment") || lower.contains("bank")) {
            return "FINTECH";
        }
        if (lower.contains("commerce") || lower.contains("retail") || lower.contains("marketplace")) {
            return "COMMERCE";
        }
        if (lower.contains("health") || lower.contains("medical")) {
            return "HEALTHCARE";
        }
        if (lower.contains("education") || lower.contains("learning")) {
            return "EDTECH";
        }
        return "TECH";
    }

    private List<Map<String, String>> evidence(List<String> requiredSkills, String postingText) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String skill : requiredSkills.stream().limit(5).toList()) {
            rows.add(Map.of(
                    "field", "requiredSkills",
                    "quote", quoteFor(skill, postingText)));
        }
        return rows;
    }

    private List<Map<String, String>> verifiedFacts(ApplicationCase applicationCase, String postingText) {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of(
                "fact", "Job title: " + defaultText(applicationCase.getJobTitle(), "unknown"),
                "source", "application_case"));
        rows.add(Map.of(
                "fact", "Company: " + defaultText(applicationCase.getCompanyName(), "unknown"),
                "source", "application_case"));
        rows.add(Map.of(
                "fact", "Posting text was extracted and quality-gated before analysis.",
                "source", quoteFor(defaultText(applicationCase.getJobTitle(), ""), postingText)));
        return rows;
    }

    private List<Map<String, String>> ambiguousConditions(String text) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (!text.toLowerCase(Locale.ROOT).contains("salary")) {
            rows.add(Map.of("condition", "salary", "assumption", "not specified in posting"));
        }
        if (!text.toLowerCase(Locale.ROOT).contains("remote") && !text.contains("재택")) {
            rows.add(Map.of("condition", "work location", "assumption", "confirm during interview"));
        }
        return rows;
    }

    private String quoteFor(String target, String text) {
        if (isBlank(target) || isBlank(text)) {
            return firstSentences(text, 1);
        }
        String lowerTarget = target.toLowerCase(Locale.ROOT);
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).contains(lowerTarget)) {
                return truncate(trimmed, 240);
            }
        }
        return firstSentences(text, 1);
    }

    private Map<String, Object> sourceSnapshot(FitAnalysisGenerationSource source) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("jobAnalysisId", source.getJobAnalysisId());
        snapshot.put("jobPostingId", source.getJobPostingId());
        snapshot.put("jobPostingRevision", source.getJobPostingRevision());
        snapshot.put("jobAnalysisCreatedAt", source.getJobAnalysisCreatedAt());
        snapshot.put("userProfileId", source.getUserProfileId());
        snapshot.put("profileUpdatedAt", source.getProfileUpdatedAt());
        snapshot.put("requiredSkills", parseList(source.getRequiredSkills()));
        snapshot.put("preferredSkills", parseList(source.getPreferredSkills()));
        snapshot.put("profileSkills", parseList(source.getProfileSkills()));
        snapshot.put("profileCertificates", parseList(source.getProfileCertificates()));
        snapshot.put("pipelineModel", MODEL);
        return snapshot;
    }

    private Map<String, Object> historyDiff(FitAnalysisResult previous, FitAnalysisResult current) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("scoreDelta", previous == null || previous.getFitScore() == null || current.getFitScore() == null
                ? null
                : current.getFitScore() - previous.getFitScore());
        diff.put("pipelineModel", MODEL);
        return diff;
    }

    private String severity(FitConditionMatch condition) {
        if ("REQUIRED".equals(condition.conditionType()) && "UNMET".equals(condition.matchStatus())) {
            return "HIGH";
        }
        if ("UNMET".equals(condition.matchStatus())) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private void recordFallbackIfNeeded(Long userId,
                                        Long applicationCaseId,
                                        String featureType,
                                        GeneratedJobAnalysis generated) {
        if (generated.fellBack()) {
            recordFailure(userId, applicationCaseId, featureType, generated.fallbackAttemptedModel(), generated.fallbackReason());
        }
    }

    private void recordFallbackIfNeeded(Long userId,
                                        Long applicationCaseId,
                                        String featureType,
                                        GeneratedCompanyAnalysis generated) {
        if (generated.fellBack()) {
            recordFailure(userId, applicationCaseId, featureType, generated.fallbackAttemptedModel(), generated.fallbackReason());
        }
    }

    private void recordSuccess(Long userId, Long applicationCaseId, String featureType, Usage usage) {
        if (usage == null) {
            return;
        }
        applicationCaseMapper.insertAiUsageLog(AiUsageLog.builder()
                .userId(userId)
                .applicationCaseId(applicationCaseId)
                .featureType(featureType)
                .status("SUCCESS")
                .model(usage.model())
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .tokenUsage(usage.totalTokens())
                .creditUsed(0)
                .build());
    }

    private void recordSuccess(Long userId, Long applicationCaseId, String featureType, int inputSize, int outputSize) {
        applicationCaseMapper.insertAiUsageLog(AiUsageLog.builder()
                .userId(userId)
                .applicationCaseId(applicationCaseId)
                .featureType(featureType)
                .status("SUCCESS")
                .model(MODEL)
                .inputTokens(estimateTokens(inputSize))
                .outputTokens(estimateTokens(outputSize))
                .tokenUsage(estimateTokens(inputSize + outputSize))
                .creditUsed(0)
                .build());
    }

    private void recordFailure(Long userId, Long applicationCaseId, String featureType, String message) {
        recordFailure(userId, applicationCaseId, featureType, MODEL, message);
    }

    private void recordFailure(Long userId, Long applicationCaseId, String featureType, String model, String message) {
        try {
            applicationCaseMapper.insertAiUsageLog(AiUsageLog.builder()
                    .userId(userId)
                    .applicationCaseId(applicationCaseId)
                    .featureType(featureType)
                    .status("FAILED")
                    .model(model)
                    .creditUsed(0)
                    .errorMessage(truncate(message, 1000))
                    .build());
        } catch (RuntimeException logException) {
            log.warn("Failed to record self AI pipeline failure. applicationCaseId={}", applicationCaseId, logException);
        }
    }

    private List<String> parseList(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values.stream()
                    .filter(value -> !isBlank(value))
                    .map(String::trim)
                    .toList();
        } catch (JacksonException ex) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JacksonException ex) {
            return "[]";
        }
    }

    private String firstSentences(String text, int maxSentences) {
        if (isBlank(text)) {
            return "";
        }
        String compact = text.trim().replaceAll("\\s+", " ");
        String[] parts = compact.split("(?<=[.!?])\\s+");
        List<String> selected = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                selected.add(part.trim());
            }
            if (selected.size() >= maxSentences) {
                break;
            }
        }
        String result = selected.isEmpty() ? compact : String.join(" ", selected);
        return truncate(result, 500);
    }

    private String joinPreview(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return String.join(", ", values.subList(0, Math.min(5, values.size())));
    }

    private int estimateLength(List<String> values) {
        return values == null ? 0 : values.stream().mapToInt(value -> value == null ? 0 : value.length()).sum();
    }

    private int estimateTokens(FitAnalysisAiCommand command) {
        return Math.max(800, Math.min(4000,
                600
                        + command.requiredSkills().size() * 40
                        + command.preferredSkills().size() * 30
                        + command.profileSkills().size() * 20
                        + (command.duties() == null ? 0 : command.duties().length())));
    }

    private int estimateTokens(int chars) {
        return Math.max(0, (int) Math.ceil(Math.max(0, chars) / 4.0));
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(target));
    }

    private static String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static String compactColumnText(String value, int maxLength) {
        if (isBlank(value)) {
            return null;
        }
        String compact = value.trim().replaceAll("\\s+", " ");
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength);
    }

    private static String safeMessage(Throwable ex) {
        return isBlank(ex.getMessage()) ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
