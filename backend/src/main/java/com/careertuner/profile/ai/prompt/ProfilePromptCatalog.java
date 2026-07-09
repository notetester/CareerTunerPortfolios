package com.careertuner.profile.ai.prompt;

import java.util.Map;
import java.util.stream.Collectors;

import com.careertuner.admin.prompt.dto.AdminPromptView;
import com.careertuner.profile.ai.JobFamily;
import com.careertuner.profile.ai.JobFamilyWeightPolicy;
import com.careertuner.profile.ai.ScoreCriterion;

public final class ProfilePromptCatalog {

    public static final String FEATURE = "profile";
    public static final String VERSION = "a-profile-v2";
    public static final String SYSTEM_PROMPT = """
            당신은 CareerTuner의 프로필 평가 보조 AI입니다.
            응답은 반드시 JSON schema에 맞춰 작성합니다.
            사용자의 희망 직무군과 서버 가중치 정책을 기준으로 이력서, 자기소개, 경력, 활동, 역량을 평가합니다.
            개발 직무에만 치우치지 말고 영업, 마케팅, 디자인, 사무, 의료, 교육, 생산, 물류 등 다양한 직무 맥락을 반영합니다.
            최종 점수는 서버가 다시 계산하므로 criterionScores.rawScore에는 각 기준의 원점수만 0~100 사이 정수로 작성합니다.
            개인정보, 민감정보, 확인 불가능한 경력은 단정하지 말고 보완 필요 항목으로 분리합니다.
            """;
    public static final String SCHEMA_SUMMARY =
            "{ summary, extractedSkills[], strengths[], gaps[], recommendations[], criterionScores[{ criterion, rawScore, evidence, improvement }] }";

    private ProfilePromptCatalog() {
    }

    public static AdminPromptView view() {
        JobFamilyWeightPolicy policy = new JobFamilyWeightPolicy();
        return new AdminPromptView(
                FEATURE,
                "프로필 AI 평가 프롬프트",
                VERSION,
                "사용자 프로필을 직무군별 평가 기준과 가중치로 분석해 요약, 역량 추출, 보완 제안을 생성합니다.",
                SYSTEM_PROMPT,
                SCHEMA_SUMMARY,
                policy.adminCriteria(),
                policy.adminWeightProfiles()
        );
    }

    public static String userPrompt(String featureType,
                                    JobFamily jobFamily,
                                    Map<ScoreCriterion, Integer> weights,
                                    String profileJson) {
        String weightText = weights.entrySet().stream()
                .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        return """
                featureType: %s
                jobFamily: %s (%s)
                serverWeights: %s

                profileJson:
                %s
                """.formatted(featureType, jobFamily.name(), jobFamily.label(), weightText, profileJson);
    }
}
