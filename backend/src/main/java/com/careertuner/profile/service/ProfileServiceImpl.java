package com.careertuner.profile.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.text.DocumentTextExtractor;
import com.careertuner.common.text.DocumentTextExtractor.Extraction;
import com.careertuner.common.text.DocumentTextExtractor.Failure;
import com.careertuner.consent.service.ConsentService;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.service.FileService;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.profile.ai.ProfileAiResult;
import com.careertuner.profile.ai.ProfileAiService;
import com.careertuner.profile.ai.ProfileCriterionScore;
import com.careertuner.profile.ai.ProfileResumeStructurer;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.dto.ProfileAnalyzeDraft;
import com.careertuner.profile.dto.ProfileAnalyzeResponse;
import com.careertuner.profile.dto.ProfileCompletenessResponse;
import com.careertuner.profile.dto.ProfileCriterionScoreResponse;
import com.careertuner.profile.dto.ProfileDocumentAnalyzeRequest;
import com.careertuner.profile.dto.ProfileDocumentImportRequest;
import com.careertuner.profile.dto.ProfileImportResponse;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.dto.UserProfileResponse;
import com.careertuner.profile.mapper.ProfileMapper;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    /** import 텍스트 상한 — 프로필 폼 resume 한도(20000)보다 낮게 잡아 파이프라인·AI 토큰 부담 완화. */
    static final int MAX_IMPORT_CHARS = 12000;

    /** 프로필 AI 분석 기능별 알림 문구 라벨. */
    private static final java.util.Map<String, String> PROFILE_FEATURE_LABELS = java.util.Map.of(
            "PROFILE_SUMMARY", "프로필 요약 분석",
            "PROFILE_SKILL_EXTRACT", "보유 역량 추출",
            "PROFILE_COMPLETENESS", "프로필 완성도 진단");

    private final ProfileMapper profileMapper;
    private final com.careertuner.profile.mapper.ProfileAiAnalysisMapper profileAiAnalysisMapper;
    private final ApplicationCaseMapper applicationCaseMapper;
    private final ConsentService consentService;
    private final ProfileAiService profileAiService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final FileService fileService;
    private final ProfilePortfolioService profilePortfolioService;
    private final DocumentTextExtractor documentTextExtractor;
    private final ProfileResumeStructurer profileResumeStructurer;
    private final TransactionTemplate transactionTemplate;

    /** 구조화 분석 비동기 작업 저장(인메모리·재시작 시 휘발). key=jobId. TTL 경과분은 접근 시 청소. */
    private final Map<String, AnalyzeJob> analyzeJobs = new ConcurrentHashMap<>();
    /** 초안에 이력서 전문(PII)이 들어가므로 폴링 종료 후 힙에 남지 않게 제한. 프론트 폴링은 최대 4분. */
    private static final long ANALYZE_JOB_TTL_MILLIS = 30 * 60 * 1000L;
    private final ExecutorService analyzeExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "profile-resume-analyze");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    void shutdownAnalyzeExecutor() {
        analyzeExecutor.shutdownNow();
    }

    @Override
    public UserProfileResponse me(AuthUser authUser) {
        return toResponse(findOrEmpty(requireUser(authUser)));
    }

    @Override
    @Transactional
    public UserProfileResponse save(AuthUser authUser, UserProfileRequest request) {
        Long userId = requireUser(authUser);
        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .desiredJob(blankToNull(request.desiredJob()))
                .desiredIndustry(blankToNull(request.desiredIndustry()))
                .education(json(request.education()))
                .career(json(request.career()))
                .projects(json(request.projects()))
                .skills(json(request.skills()))
                .certificates(json(request.certificates()))
                .languages(json(request.languages()))
                .portfolioLinks(json(request.portfolioLinks()))
                .resumeText(blankToNull(request.resumeText()))
                .selfIntro(blankToNull(request.selfIntro()))
                .preferences(json(request.preferences()))
                .build();
        profileMapper.upsert(profile);
        return toResponse(profileMapper.findByUserId(userId));
    }

    @Override
    public ProfileImportResponse importDocument(AuthUser authUser, ProfileDocumentImportRequest request) {
        Long userId = requireUser(authUser);
        requireResumeAnalysisConsent(userId);
        if (request == null || request.fileId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "가져올 파일을 선택해 주세요.");
        }
        String target = request.target() == null ? "" : request.target().trim().toUpperCase(Locale.ROOT);
        if (!"RESUME_TEXT".equals(target) && !"SELF_INTRO".equals(target)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 요청입니다.");
        }

        // download + extract 는 트랜잭션 밖 (Cloudinary 네트워크 페치 가능)
        FileService.Download download = fileService.download(userId, request.fileId());
        FileAsset asset = download.asset();
        Extraction extraction = documentTextExtractor.extract(
                download.bytes(), asset.getContentType(), asset.getOriginalName());
        if (!extraction.isSuccess()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, messageFor(extraction.reason()));
        }

        String text = extraction.text();
        boolean truncated = text.length() > MAX_IMPORT_CHARS;
        if (truncated) {
            text = text.substring(0, MAX_IMPORT_CHARS);
        }
        final String finalText = text;

        UserProfileResponse saved = transactionTemplate.execute(status -> {
            UserProfile cur = findOrEmpty(userId);
            String resumeText = "RESUME_TEXT".equals(target) ? finalText : blankToNull(cur.getResumeText());
            String selfIntro = "SELF_INTRO".equals(target) ? finalText : blankToNull(cur.getSelfIntro());
            UserProfile profile = UserProfile.builder()
                    .userId(userId)
                    .desiredJob(blankToNull(cur.getDesiredJob()))
                    .desiredIndustry(blankToNull(cur.getDesiredIndustry()))
                    .education(cur.getEducation())
                    .career(cur.getCareer())
                    .projects(cur.getProjects())
                    .skills(cur.getSkills())
                    .certificates(cur.getCertificates())
                    .languages(cur.getLanguages())
                    .portfolioLinks(cur.getPortfolioLinks())
                    .resumeText(resumeText)
                    .selfIntro(selfIntro)
                    .preferences(cur.getPreferences())
                    .build();
            profileMapper.upsert(profile);
            return toResponse(profileMapper.findByUserId(userId));
        });
        return new ProfileImportResponse(saved, truncated);
    }

    @Override
    public ProfileAnalyzeResponse startAnalyze(AuthUser authUser, ProfileDocumentAnalyzeRequest request) {
        Long userId = requireUser(authUser);
        // 이력서 원문이 LLM으로 전송되는 경로 — 다른 프로필 AI 기능과 동일하게 AI_DATA 동의 필수.
        requireAiConsent(userId);
        requireResumeAnalysisConsent(userId);
        if (request == null || request.fileId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "가져올 파일을 선택해 주세요.");
        }

        // download+extract 트랜잭션 밖
        FileService.Download download = fileService.download(userId, request.fileId());
        FileAsset asset = download.asset();
        Extraction extraction = documentTextExtractor.extract(
                download.bytes(), asset.getContentType(), asset.getOriginalName());
        if (!extraction.isSuccess()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, messageFor(extraction.reason()));
        }

        evictExpiredAnalyzeJobs();
        String jobId = UUID.randomUUID().toString();
        AnalyzeJob job = new AnalyzeJob(userId, "PENDING", null, null, System.currentTimeMillis());
        analyzeJobs.put(jobId, job);

        final String sourceText = extraction.text();
        analyzeExecutor.execute(() -> {
            try {
                ProfileAnalyzeDraft draft = profileResumeStructurer.structure(sourceText);
                analyzeJobs.put(jobId, new AnalyzeJob(userId, "DONE", draft, null, System.currentTimeMillis()));
            } catch (RuntimeException ex) {
                analyzeJobs.put(jobId, new AnalyzeJob(userId, "FAILED", null,
                        "구조화 분석은 실패했어요. 폼을 직접 채워주세요.", System.currentTimeMillis()));
            }
        });
        return ProfileAnalyzeResponse.pending(jobId);
    }

    @Override
    public ProfileAnalyzeResponse getAnalyze(AuthUser authUser, String jobId) {
        Long userId = requireUser(authUser);
        if (jobId == null || jobId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 요청입니다.");
        }
        evictExpiredAnalyzeJobs();
        AnalyzeJob job = analyzeJobs.get(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "분석 작업을 찾을 수 없습니다.");
        }
        if (!job.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "분석 작업에 접근할 권한이 없습니다.");
        }
        return switch (job.status()) {
            case "DONE" -> ProfileAnalyzeResponse.done(jobId, job.draft());
            case "FAILED" -> ProfileAnalyzeResponse.failed(jobId, job.errorMessage());
            default -> ProfileAnalyzeResponse.pending(jobId);
        };
    }

    /** 작업지시서 §6 예외 표 6~11. */
    static String messageFor(Failure reason) {
        if (reason == null) {
            return "파일에서 글자를 찾지 못했습니다.";
        }
        return switch (reason) {
            case NO_TEXT_LAYER -> "스캔한 PDF라서 글자를 읽지 못했습니다. 본문을 직접 붙여넣어 주세요.";
            case CORRUPTED -> "파일이 손상되었거나 암호가 걸려 있습니다.";
            case UNSUPPORTED_FORMAT -> "지원하지 않는 형식입니다. `.docx`, `.pdf`, `.txt` 로 올려주세요.";
            case EMPTY -> "파일에서 글자를 찾지 못했습니다.";
        };
    }

    @Override
    @Transactional
    public ProfileAiResponse summarize(AuthUser authUser, RequestedAiModel requestedModel) {
        ProfileAiResult result = evaluateWithConsent(authUser, "PROFILE_SUMMARY", requestedModel);
        return toAiResponse(result);
    }

    @Override
    @Transactional
    public ProfileAiResponse extractSkills(AuthUser authUser, RequestedAiModel requestedModel) {
        ProfileAiResult result = evaluateWithConsent(authUser, "PROFILE_SKILL_EXTRACT", requestedModel);
        return toAiResponse(result);
    }

    @Override
    @Transactional
    public ProfileCompletenessResponse diagnoseCompleteness(AuthUser authUser, RequestedAiModel requestedModel) {
        ProfileAiResult result = evaluateWithConsent(authUser, "PROFILE_COMPLETENESS", requestedModel);
        return toCompletenessResponse(result);
    }

    @Override
    public List<UserProfileResponse> adminProfiles(AuthUser authUser, String keyword, int limit) {
        requireAdmin(authUser);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return profileMapper.findAdminProfiles(blankToNull(keyword), safeLimit).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public UserProfileResponse adminProfile(AuthUser authUser, Long userId) {
        requireAdmin(authUser);
        return toResponse(findOrEmpty(userId));
    }

    private ProfileAiResult evaluateWithConsent(AuthUser authUser, String featureType, RequestedAiModel requestedModel) {
        Long userId = requireUser(authUser);
        requireAiConsent(userId);
        requireResumeAnalysisConsent(userId);
        UserProfile profile = findOrEmpty(userId);
        profile.setPortfolioEvidence(profilePortfolioService.evidenceText(userId));
        // 모델 선택은 어느 provider 가 요약·강점·보완점 텍스트를 쓰는가만 바꾼다(점수 계산 로직은 provider 공통).
        ProfileAiResult result = profileAiService.evaluate(profile, featureType, requestedModel);
        recordAi(userId, result);
        // 성공 산출물을 영속한다(feature 별 최신 1행) — 사용자 조회 + C 적합도 입력 창구.
        // 실패해도 분석 응답 자체는 막지 않는다(best-effort persist).
        if ("SUCCESS".equals(result.status())) {
            persistAiAnalysis(userId, featureType, result);
        }
        // 스펙(프로필) 분석이 성공하면 사용자에게 완료 알림을 남긴다.
        if ("SUCCESS".equals(result.status())) {
            notificationService.notify(Notification.builder()
                    .userId(userId)
                    .type("PROFILE_ANALYZED")
                    .targetType("USER_PROFILE")
                    .targetId(userId)
                    .title("스펙 분석이 완료되었습니다")
                    .message("%s 완료 · 완성도 %d점".formatted(
                            PROFILE_FEATURE_LABELS.getOrDefault(featureType, "프로필 분석"),
                            result.completenessScore()))
                    .link("/profile")
                    .build());
        }
        return result;
    }

    private UserProfile findOrEmpty(Long userId) {
        UserProfile profile = profileMapper.findByUserId(userId);
        return profile != null ? profile : UserProfile.builder().userId(userId).build();
    }

    private ProfileAiResponse toAiResponse(ProfileAiResult result) {
        return new ProfileAiResponse(
                result.featureType(),
                result.summary(),
                result.extractedSkills(),
                result.strengths(),
                result.gaps(),
                result.recommendations(),
                result.completenessScore(),
                result.jobFamily().name(),
                result.jobFamily().label(),
                criteria(result.criteria()),
                result.usage().model(),
                result.status(),
                result.aiScore(),
                result.qualityPenalty(),
                result.qualityWarnings(),
                result.qualityRecommendations());
    }

    private ProfileCompletenessResponse toCompletenessResponse(ProfileAiResult result) {
        List<String> completed = result.criteria().stream()
                .filter(row -> row.rawScore() >= 70)
                .map(row -> row.criterion().label())
                .toList();
        List<String> missing = result.criteria().stream()
                .filter(row -> row.rawScore() < 70)
                .map(row -> row.criterion().label())
                .toList();
        return new ProfileCompletenessResponse(
                result.completenessScore(),
                completed,
                missing,
                result.recommendations(),
                result.jobFamily().name(),
                result.jobFamily().label(),
                criteria(result.criteria()),
                result.usage().model(),
                result.status(),
                result.aiScore(),
                result.qualityPenalty(),
                result.qualityWarnings(),
                result.qualityRecommendations());
    }

    private List<ProfileCriterionScoreResponse> criteria(List<ProfileCriterionScore> criteria) {
        return criteria.stream()
                .map(row -> new ProfileCriterionScoreResponse(
                        row.criterion().name(),
                        row.criterion().label(),
                        row.rawScore(),
                        row.weight(),
                        row.weightedScore(),
                        row.evidence(),
                        row.improvement()))
                .toList();
    }

    /** 성공 산출물을 feature 별 최신 1행으로 저장(JSON 컬럼 직렬화). 직렬화 실패는 무시(분석 자체는 성공). */
    private void persistAiAnalysis(Long userId, String featureType, ProfileAiResult result) {
        try {
            profileAiAnalysisMapper.upsert(com.careertuner.profile.domain.ProfileAiAnalysis.builder()
                    .userId(userId)
                    .featureType(featureType)
                    .summary(result.summary())
                    .strengths(toJson(result.strengths()))
                    .gaps(toJson(result.gaps()))
                    .recommendations(toJson(result.recommendations()))
                    .extractedSkills(toJson(result.extractedSkills()))
                    .criteria(toJson(result.criteria()))
                    .jobFamily(result.jobFamily() == null ? null : result.jobFamily().name())
                    .completenessScore(result.completenessScore())
                    .aiScore(result.aiScore())
                    .qualityWarnings(toJson(result.qualityWarnings()))
                    .model(result.usage() == null ? null : result.usage().model())
                    .status(result.status())
                    .build());
        } catch (RuntimeException e) {
            log.debug("profile ai analysis persist failed: userId={} feature={} err={}",
                    userId, featureType, e.getClass().getSimpleName());
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public com.careertuner.profile.dto.ProfileAiAnalysisResponse aiAnalysis(AuthUser authUser) {
        Long userId = requireUser(authUser);
        List<com.careertuner.profile.domain.ProfileAiAnalysis> rows = profileAiAnalysisMapper.findByUserId(userId);
        if (rows == null || rows.isEmpty()) {
            return com.careertuner.profile.dto.ProfileAiAnalysisResponse.empty();
        }
        // 요약(강점/약점/summary)과 완성도(점수/항목)를 feature 별로 합쳐 하나의 뷰로 제공.
        // 각 필드는 그 값을 가진 feature 우선 + summary 폴백(요약만 실행한 사용자도 점수/경고가 보이도록).
        com.careertuner.profile.domain.ProfileAiAnalysis summaryRow = pick(rows, "PROFILE_SUMMARY");
        com.careertuner.profile.domain.ProfileAiAnalysis completenessRow = pick(rows, "PROFILE_COMPLETENESS");
        com.careertuner.profile.domain.ProfileAiAnalysis skillRow = pick(rows, "PROFILE_SKILL_EXTRACT");
        com.careertuner.profile.domain.ProfileAiAnalysis primary = summaryRow != null ? summaryRow : rows.get(0);
        com.careertuner.profile.domain.ProfileAiAnalysis criteriaSource = completenessRow != null ? completenessRow : summaryRow;
        com.careertuner.profile.domain.ProfileAiAnalysis scoreSource = completenessRow != null ? completenessRow : summaryRow;
        String jobFamily = primary.getJobFamily() != null ? primary.getJobFamily()
                : (completenessRow == null ? null : completenessRow.getJobFamily());
        return new com.careertuner.profile.dto.ProfileAiAnalysisResponse(
                true,
                summaryRow == null ? null : summaryRow.getSummary(),
                jsonToList(summaryRow, com.careertuner.profile.domain.ProfileAiAnalysis::getStrengths),
                jsonToList(summaryRow, com.careertuner.profile.domain.ProfileAiAnalysis::getGaps),
                jsonToList(summaryRow, com.careertuner.profile.domain.ProfileAiAnalysis::getRecommendations),
                jsonToList(skillRow != null ? skillRow : summaryRow,
                        com.careertuner.profile.domain.ProfileAiAnalysis::getExtractedSkills),
                jobFamily,
                jobFamilyLabel(jobFamily),
                scoreSource == null ? null : scoreSource.getCompletenessScore(),
                summaryRow == null ? null : summaryRow.getAiScore(),
                criteriaFromJson(criteriaSource),
                // 품질 경고는 요약 분석 우선(요약 탭에 표시되므로) — 없으면 최신 행.
                jsonToList(summaryRow != null ? summaryRow : primary,
                        com.careertuner.profile.domain.ProfileAiAnalysis::getQualityWarnings),
                primary.getUpdatedAt() == null ? null : primary.getUpdatedAt().toString());
    }

    /** enum 이름 → 표시 라벨(잘못된/미상 값이면 null, valueOf 예외 삼킴). */
    private static String jobFamilyLabel(String jobFamilyName) {
        if (jobFamilyName == null || jobFamilyName.isBlank()) {
            return null;
        }
        try {
            return com.careertuner.profile.ai.JobFamily.valueOf(jobFamilyName).label();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static com.careertuner.profile.domain.ProfileAiAnalysis pick(
            List<com.careertuner.profile.domain.ProfileAiAnalysis> rows, String featureType) {
        return rows.stream().filter(row -> featureType.equals(row.getFeatureType())).findFirst().orElse(null);
    }

    private List<String> jsonToList(com.careertuner.profile.domain.ProfileAiAnalysis row,
                                    java.util.function.Function<com.careertuner.profile.domain.ProfileAiAnalysis, String> getter) {
        if (row == null) {
            return List.of();
        }
        String json = getter.apply(row);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json,
                    new tools.jackson.core.type.TypeReference<List<String>>() { });
            return list == null ? List.of() : list;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<ProfileCriterionScoreResponse> criteriaFromJson(com.careertuner.profile.domain.ProfileAiAnalysis row) {
        if (row == null || row.getCriteria() == null || row.getCriteria().isBlank()) {
            return List.of();
        }
        try {
            List<ProfileCriterionScore> criteria = objectMapper.readValue(row.getCriteria(),
                    new tools.jackson.core.type.TypeReference<List<ProfileCriterionScore>>() { });
            return criteria == null ? List.of() : criteria(criteria);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private void recordAi(Long userId, ProfileAiResult result) {
        applicationCaseMapper.insertAiUsageLog(AiUsageLog.builder()
                .userId(userId)
                .featureType(result.featureType())
                .status(result.status())
                .model(result.usage().model())
                .inputTokens(result.usage().inputTokens())
                .outputTokens(result.usage().outputTokens())
                .tokenUsage(result.usage().totalTokens())
                .creditUsed(0)
                .errorMessage(truncate(result.errorMessage(), 500))
                .build());
    }

    private void requireAiConsent(Long userId) {
        if (!consentService.hasCurrentConsent(userId, "AI_DATA")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "AI 데이터 사용 동의가 필요합니다.");
        }
    }

    private void requireResumeAnalysisConsent(Long userId) {
        if (!consentService.hasCurrentConsent(userId, "RESUME_ANALYSIS")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이력서 분석 개인정보 수집·이용 동의가 필요합니다.");
        }
    }

    private UserProfileResponse toResponse(UserProfile p) {
        return new UserProfileResponse(p.getId(), p.getUserId(), p.getDesiredJob(), p.getDesiredIndustry(),
                object(p.getEducation()), object(p.getCareer()), object(p.getProjects()), object(p.getSkills()),
                object(p.getCertificates()), object(p.getLanguages()), object(p.getPortfolioLinks()),
                p.getResumeText(), p.getSelfIntro(), object(p.getPreferences()), p.getUpdatedAt());
    }

    private Object object(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "프로필 JSON 형식이 올바르지 않습니다.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Long requireUser(AuthUser authUser) {
        if (authUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authUser.id();
    }

    private void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }

    private void evictExpiredAnalyzeJobs() {
        long cutoff = System.currentTimeMillis() - ANALYZE_JOB_TTL_MILLIS;
        analyzeJobs.values().removeIf(job -> job.createdAtMillis() < cutoff);
    }

    private record AnalyzeJob(
            Long userId,
            String status,
            ProfileAnalyzeDraft draft,
            String errorMessage,
            long createdAtMillis
    ) {
    }
}
