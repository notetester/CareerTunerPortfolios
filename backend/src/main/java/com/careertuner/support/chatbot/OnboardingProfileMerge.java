package com.careertuner.support.chatbot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.dto.UserProfileRequest;

import tools.jackson.databind.ObjectMapper;

/**
 * 온보딩 프로필 저장용 read-modify-write 병합.
 * 기존 컬럼을 보존하고, skills 는 대소문자 무시 합집합으로 합친다.
 * JSON 문자열 컬럼은 파싱 후 넘겨 이중 인코딩을 막는다.
 */
public final class OnboardingProfileMerge {

    private OnboardingProfileMerge() {
    }

    /**
     * 기존 프로필 + 온보딩 job/skills 로 저장 요청을 만든다.
     * {@code cur} 가 null 이면 온보딩 값만 채운다.
     */
    public static UserProfileRequest merge(
            UserProfile cur,
            String desiredJob,
            List<String> onboardingSkills,
            ObjectMapper objectMapper) {
        String job = firstNonBlank(desiredJob, cur != null ? cur.getDesiredJob() : null);

        List<String> existingSkills = parseStringList(cur != null ? cur.getSkills() : null, objectMapper);
        List<String> mergedSkills = unionSkillsCaseInsensitive(existingSkills, onboardingSkills);

        if (cur == null) {
            return new UserProfileRequest(
                    job, null, null, null, null,
                    mergedSkills.isEmpty() ? null : mergedSkills,
                    null, null, null, null, null, null);
        }

        return new UserProfileRequest(
                job,
                blankToNull(cur.getDesiredIndustry()),
                parseJsonObject(cur.getEducation(), objectMapper),
                parseJsonObject(cur.getCareer(), objectMapper),
                parseJsonObject(cur.getProjects(), objectMapper),
                mergedSkills.isEmpty() ? null : mergedSkills,
                parseJsonObject(cur.getCertificates(), objectMapper),
                parseJsonObject(cur.getLanguages(), objectMapper),
                parseJsonObject(cur.getPortfolioLinks(), objectMapper),
                blankToNull(cur.getResumeText()),
                blankToNull(cur.getSelfIntro()),
                parseJsonObject(cur.getPreferences(), objectMapper));
    }

    /**
     * 기존 스킬을 먼저, 온보딩 스킬을 뒤에 두고 대소문자 무시 dedup(첫 등장 원문·순서 보존).
     */
    public static List<String> unionSkillsCaseInsensitive(
            Collection<String> existing, Collection<String> onboarding) {
        Map<String, String> ordered = new LinkedHashMap<>();
        if (existing != null) {
            for (String s : existing) {
                putSkill(ordered, s);
            }
        }
        if (onboarding != null) {
            for (String s : onboarding) {
                putSkill(ordered, s);
            }
        }
        return List.copyOf(ordered.values());
    }

    private static void putSkill(Map<String, String> ordered, String raw) {
        if (raw == null) {
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        ordered.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }

    /** ProfileServiceImpl.object 와 동일: JSON 파싱, 실패 시 원문 문자열. */
    public static Object parseJsonObject(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> parseStringList(String json, ObjectMapper objectMapper) {
        Object parsed = parseJsonObject(json, objectMapper);
        if (parsed == null) {
            return List.of();
        }
        if (parsed instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String s = String.valueOf(item).trim();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
            return out;
        }
        if (parsed instanceof String s && !s.isBlank()) {
            return List.of(s.trim());
        }
        return List.of();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return blankToNull(fallback);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
