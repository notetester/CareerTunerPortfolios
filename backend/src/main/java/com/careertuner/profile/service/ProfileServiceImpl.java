package com.careertuner.profile.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.service.ConsentService;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.profile.ai.ProfileAiResult;
import com.careertuner.profile.ai.ProfileAiService;
import com.careertuner.profile.ai.ProfileCriterionScore;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.dto.ProfileCompletenessResponse;
import com.careertuner.profile.dto.ProfileCriterionScoreResponse;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.dto.UserProfileResponse;
import com.careertuner.profile.mapper.ProfileMapper;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    /** 프로필 AI 분석 기능별 알림 문구 라벨. */
    private static final java.util.Map<String, String> PROFILE_FEATURE_LABELS = java.util.Map.of(
            "PROFILE_SUMMARY", "프로필 요약 분석",
            "PROFILE_SKILL_EXTRACT", "보유 역량 추출",
            "PROFILE_COMPLETENESS", "프로필 완성도 진단");

    private final ProfileMapper profileMapper;
    private final ApplicationCaseMapper applicationCaseMapper;
    private final ConsentService consentService;
    private final ProfileAiService profileAiService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public UserProfileResponse me(AuthUser authUser) {
        return toResponse(findOrEmpty(requireUser(authUser)));
    }

    @Override
    @Transactional
    public UserProfileResponse save(AuthUser authUser, UserProfileRequest request) {
        Long userId = requireUser(authUser);
        String loginId = normalizeLoginId(request.loginId());
        if (loginId != null && profileMapper.countLoginId(loginId, userId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        profileMapper.updateAccountBasics(userId, loginId, normalizePhone(request.phoneNumber()));
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
                .jobPreferences(json(request.jobPreferences()))
                .personalInfo(json(request.personalInfo()))
                .activities(json(request.activities()))
                .accountLinks(json(request.accountLinks()))
                .chatProfiles(json(request.chatProfiles()))
                .resumeText(blankToNull(request.resumeText()))
                .selfIntro(blankToNull(request.selfIntro()))
                .preferences(json(request.preferences()))
                .build();
        profileMapper.upsert(profile);
        return toResponse(profileMapper.findByUserId(userId));
    }

    @Override
    @Transactional
    public ProfileAiResponse summarize(AuthUser authUser) {
        ProfileAiResult result = evaluateWithConsent(authUser, "PROFILE_SUMMARY");
        return toAiResponse(result);
    }

    @Override
    @Transactional
    public ProfileAiResponse extractSkills(AuthUser authUser) {
        ProfileAiResult result = evaluateWithConsent(authUser, "PROFILE_SKILL_EXTRACT");
        return toAiResponse(result);
    }

    @Override
    @Transactional
    public ProfileCompletenessResponse diagnoseCompleteness(AuthUser authUser) {
        ProfileAiResult result = evaluateWithConsent(authUser, "PROFILE_COMPLETENESS");
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

    private ProfileAiResult evaluateWithConsent(AuthUser authUser, String featureType) {
        Long userId = requireUser(authUser);
        requireAiConsent(userId);
        ProfileAiResult result = profileAiService.evaluate(findOrEmpty(userId), featureType);
        recordAi(userId, result);
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

    private UserProfileResponse toResponse(UserProfile p) {
        return new UserProfileResponse(p.getId(), p.getUserId(),
                p.getLoginId(), p.getPhoneNumber(), p.isPhoneVerified(),
                p.getDesiredJob(), p.getDesiredIndustry(),
                object(p.getEducation()), object(p.getCareer()), object(p.getProjects()), object(p.getSkills()),
                object(p.getCertificates()), object(p.getLanguages()), object(p.getPortfolioLinks()),
                object(p.getJobPreferences()), object(p.getPersonalInfo()), object(p.getActivities()),
                object(p.getAccountLinks()), object(p.getChatProfiles()),
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

    private String normalizeLoginId(String value) {
        String loginId = blankToNull(value);
        if (loginId == null) {
            return null;
        }
        if (!loginId.matches("^[A-Za-z0-9._-]{4,60}$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디는 영문, 숫자, 점, 밑줄, 하이픈 조합 4~60자로 입력해 주세요.");
        }
        return loginId.toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizePhone(String value) {
        String phone = blankToNull(value);
        if (phone == null) {
            return null;
        }
        String normalized = phone.replaceAll("[^0-9+]", "");
        if (normalized.length() < 9 || normalized.length() > 20) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "전화번호 형식이 올바르지 않습니다.");
        }
        return normalized;
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
}
