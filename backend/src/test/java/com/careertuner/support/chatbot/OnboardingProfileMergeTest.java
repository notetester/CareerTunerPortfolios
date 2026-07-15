package com.careertuner.support.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.dto.UserProfileRequest;

import tools.jackson.databind.ObjectMapper;

/**
 * Day 0 게이트: saveOnboardingProfile RMW — self_intro 보존 + skills 합집합 + JSON 이중 인코딩 방지.
 */
class OnboardingProfileMergeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preservesSelfIntroAndUnionsSkillsCaseInsensitive() {
        UserProfile cur = UserProfile.builder()
                .userId(53L)
                .desiredJob("백엔드")
                .selfIntro("기존 자기소개서 본문")
                .resumeText("이력서 원문")
                .skills("[\"Java\",\"Spring Boot\"]")
                .education("[{\"school\":\"Korea Univ\",\"major\":\"CS\",\"startDate\":\"2018-03\",\"endDate\":\"2022-02\",\"status\":\"졸업\"}]")
                .versionNo(4)
                .build();

        UserProfileRequest req = OnboardingProfileMerge.merge(
                cur,
                "경영·기획·전략",
                List.of("직무 전문성", "java", "데이터 분석"),
                objectMapper);

        assertThat(req.selfIntro()).isEqualTo("기존 자기소개서 본문");
        assertThat(req.resumeText()).isEqualTo("이력서 원문");
        assertThat(req.desiredJob()).isEqualTo("경영·기획·전략");
        assertThat(req.baseVersionNo()).isEqualTo(4);
        assertThat(req.skills()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) req.skills();
        // 기존 Java 원문 유지, java 온보딩 칩은 중복 제거. Spring Boot 유지. 한국어 칩 추가.
        assertThat(skills).containsExactly("Java", "Spring Boot", "직무 전문성", "데이터 분석");
        // education 은 파싱된 Object(List) — 문자열을 그대로 넣으면 json() 이중 인코딩
        assertThat(req.education()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> education = (List<Map<String, Object>>) req.education();
        assertThat(education.get(0).get("school")).isEqualTo("Korea Univ");
    }

    @Test
    void emptyProfileUsesOnboardingOnly() {
        UserProfileRequest req = OnboardingProfileMerge.merge(
                null, "백엔드 개발", List.of("React", "TypeScript"), objectMapper);

        assertThat(req.desiredJob()).isEqualTo("백엔드 개발");
        assertThat(req.skills()).isEqualTo(List.of("React", "TypeScript"));
        assertThat(req.selfIntro()).isNull();
        assertThat(req.education()).isNull();
        assertThat(req.baseVersionNo()).isNull();
    }

    @Test
    void doesNotDoubleEncodeJsonStringFields() throws Exception {
        String careerJson = "[{\"company\":\"Kakao\",\"role\":\"BE\",\"startDate\":\"2022-03\",\"endDate\":\"\",\"tasks\":\"API\"}]";
        UserProfile cur = UserProfile.builder()
                .userId(1L)
                .career(careerJson)
                .skills("[\"Python\"]")
                .build();

        UserProfileRequest req = OnboardingProfileMerge.merge(cur, null, List.of("Docker"), objectMapper);

        // 재직렬화 시 배열이어야 함 — String 이면 writeValueAsString 이 따옴표로 한 번 더 감싼다
        String reencoded = objectMapper.writeValueAsString(req.career());
        assertThat(reencoded).startsWith("[");
        assertThat(reencoded).doesNotStartWith("\"[");
        assertThat(objectMapper.writeValueAsString(req.skills())).isEqualTo("[\"Python\",\"Docker\"]");
    }
}
