package com.careertuner.profile.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.service.ConsentService;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.dto.ProfileCompletenessResponse;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.dto.UserProfileResponse;
import com.careertuner.profile.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private static final Pattern SKILL_SPLIT = Pattern.compile("[,\\n/|]");
    private static final List<String> KNOWN_SKILLS = List.of(
            "커뮤니케이션", "문제 해결", "문서 작성", "자료 조사", "데이터 분석", "고객 응대",
            "상담", "영업", "마케팅", "브랜딩", "콘텐츠 기획", "SNS 운영", "광고 운영",
            "회계", "세무", "재무", "인사", "채용", "교육", "강의", "간호", "의료",
            "품질 관리", "생산 관리", "물류", "구매", "서비스 운영", "기획", "디자인",
            "Figma", "Photoshop", "Excel", "PowerPoint", "Notion", "GA4",
            "Java", "Spring", "Spring Boot", "MyBatis", "MySQL", "React", "TypeScript",
            "JavaScript", "HTML", "CSS", "Git", "Docker", "AWS", "REST API", "JWT",
            "Node.js", "Python", "SQL", "Linux", "Vite", "Tailwind"
    );

    private final ProfileMapper profileMapper;
    private final ApplicationCaseMapper applicationCaseMapper;
    private final ConsentService consentService;
    private final ObjectMapper objectMapper;

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
    @Transactional
    public ProfileAiResponse summarize(AuthUser authUser) {
        Long userId = requireUser(authUser);
        requireAiConsent(userId);
        UserProfile profile = findOrEmpty(userId);
        ProfileCompletenessResponse completeness = completeness(profile);
        List<String> skills = extractSkillNames(profile);
        String summary = "%s 직무를 목표로 %s 기반의 경험을 정리 중입니다. %s"
                .formatted(valueOr(profile.getDesiredJob(), "지원 희망"),
                        skills.isEmpty() ? "프로필" : String.join(", ", skills.subList(0, Math.min(5, skills.size()))),
                        completeness.missing().isEmpty()
                                ? "핵심 프로필 항목이 비교적 잘 채워져 있습니다."
                                : "보강 우선 항목은 " + String.join(", ", completeness.missing()) + "입니다.");
        recordAi(userId, "PROFILE_SUMMARY");
        return new ProfileAiResponse("PROFILE_SUMMARY", summary, skills,
                strengths(profile, skills), completeness.missing(), completeness.recommendations(), completeness.score());
    }

    @Override
    @Transactional
    public ProfileAiResponse extractSkills(AuthUser authUser) {
        Long userId = requireUser(authUser);
        requireAiConsent(userId);
        UserProfile profile = findOrEmpty(userId);
        List<String> skills = extractSkillNames(profile);
        recordAi(userId, "PROFILE_SKILL_EXTRACT");
        return new ProfileAiResponse("PROFILE_SKILL_EXTRACT",
                "이력서와 경험 문장에서 직무 역량 후보를 추출했습니다. 확정 전 사용자 검토가 필요합니다.",
                skills, List.of(), List.of(), List.of("중복 역량명을 정리하고 주력 역량과 보조 역량을 나눠 주세요."), completeness(profile).score());
    }

    @Override
    @Transactional
    public ProfileCompletenessResponse diagnoseCompleteness(AuthUser authUser) {
        Long userId = requireUser(authUser);
        requireAiConsent(userId);
        UserProfile profile = findOrEmpty(userId);
        ProfileCompletenessResponse result = completeness(profile);
        recordAi(userId, "PROFILE_COMPLETENESS");
        return result;
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

    private UserProfile findOrEmpty(Long userId) {
        UserProfile profile = profileMapper.findByUserId(userId);
        return profile != null ? profile : UserProfile.builder().userId(userId).build();
    }

    private ProfileCompletenessResponse completeness(UserProfile p) {
        List<String> completed = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        addCheck(completed, missing, "희망 직무", p.getDesiredJob());
        addCheck(completed, missing, "희망 산업", p.getDesiredIndustry());
        addCheck(completed, missing, "이력서 원문", p.getResumeText());
        addCheck(completed, missing, "자기소개서", p.getSelfIntro());
        addCheck(completed, missing, "직무 역량/스킬", p.getSkills());
        addCheck(completed, missing, "경력", p.getCareer());
        addCheck(completed, missing, "경험/프로젝트/활동", p.getProjects());
        addCheck(completed, missing, "학력", p.getEducation());
        addCheck(completed, missing, "자격증", p.getCertificates());
        addCheck(completed, missing, "포트폴리오/활동 링크", p.getPortfolioLinks());
        int score = (int) Math.round(completed.size() * 100.0 / (completed.size() + missing.size()));
        List<String> recommendations = missing.stream().limit(4)
                .map(item -> item + " 정보를 입력하면 공고 매칭과 면접 질문 생성 품질이 좋아집니다.")
                .toList();
        return new ProfileCompletenessResponse(score, completed, missing, recommendations);
    }

    private List<String> extractSkillNames(UserProfile p) {
        Set<String> result = new LinkedHashSet<>();
        collectJsonArrayStrings(result, p.getSkills());
        String text = String.join("\n",
                valueOr(p.getResumeText(), ""), valueOr(p.getSelfIntro(), ""),
                valueOr(p.getProjects(), ""), valueOr(p.getCareer(), ""));
        String lower = text.toLowerCase(Locale.ROOT);
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase(Locale.ROOT))) {
                result.add(skill);
            }
        }
        for (String token : SKILL_SPLIT.split(text)) {
            String trimmed = token.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 30 && trimmed.matches("[A-Za-z0-9+#. -]+")) {
                for (String skill : KNOWN_SKILLS) {
                    if (trimmed.equalsIgnoreCase(skill)) {
                        result.add(skill);
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    private void collectJsonArrayStrings(Set<String> result, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(json).forEach(node -> {
                if (node.isTextual() && !node.asText().isBlank()) {
                    result.add(node.asText().trim());
                }
            });
        } catch (Exception ignored) {
            // 사용자가 임시로 저장한 JSON이 아니면 AI 후보 추출에서만 제외한다.
        }
    }

    private List<String> strengths(UserProfile p, List<String> skills) {
        List<String> strengths = new ArrayList<>();
        if (!skills.isEmpty()) strengths.add("직무 역량 키워드가 " + skills.size() + "개 정리되어 있습니다.");
        if (hasText(p.getProjects())) strengths.add("경험/프로젝트/활동 기록이 입력되어 있습니다.");
        if (hasText(p.getSelfIntro())) strengths.add("자기소개서 원문이 있어 핵심 키워드 추출이 가능합니다.");
        return strengths;
    }

    private void recordAi(Long userId, String featureType) {
        applicationCaseMapper.insertAiUsageLog(AiUsageLog.builder()
                .userId(userId)
                .featureType(featureType)
                .status("SUCCESS")
                .model("profile-rule-v1")
                .inputTokens(0)
                .outputTokens(0)
                .tokenUsage(0)
                .creditUsed(0)
                .build());
    }

    private void requireAiConsent(Long userId) {
        if (!consentService.hasCurrentConsent(userId, "AI_DATA")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "AI 데이터 사용 동의가 필요합니다.");
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

    private void addCheck(List<String> completed, List<String> missing, String label, String value) {
        (hasText(value) ? completed : missing).add(label);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"[]".equals(value) && !"{}".equals(value) && !"null".equals(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Long requireUser(AuthUser authUser) {
        if (authUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authUser.id();
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
