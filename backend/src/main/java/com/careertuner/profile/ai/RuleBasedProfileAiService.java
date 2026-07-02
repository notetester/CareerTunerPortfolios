package com.careertuner.profile.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.profile.domain.UserProfile;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class RuleBasedProfileAiService implements ProfileAiService {

    private static final Pattern SKILL_SPLIT = Pattern.compile("[,\\n/|]");
    private static final Pattern NUMBER_EVIDENCE = Pattern.compile("\\d+|%|명|건|회|만원|원|시간|개월|년");
    private static final List<String> KNOWN_SKILLS = List.of(
            "커뮤니케이션", "문제 해결", "문서 작성", "자료 조사", "데이터 분석", "고객 응대",
            "상담", "영업", "마케팅", "브랜딩", "콘텐츠 기획", "SNS 운영", "광고 운영",
            "회계", "총무", "재무", "인사", "채용", "교육", "강의", "간호", "의료",
            "재고 관리", "생산 관리", "물류", "구매", "서비스 운영", "기획", "디자인",
            "Figma", "Photoshop", "Excel", "PowerPoint", "Notion", "GA4",
            "Java", "Spring", "Spring Boot", "MyBatis", "MySQL", "React", "TypeScript",
            "JavaScript", "HTML", "CSS", "Git", "Docker", "AWS", "REST API", "JWT",
            "Node.js", "Python", "SQL", "Linux", "Vite", "Tailwind"
    );

    private final JobFamilyWeightPolicy weightPolicy;
    private final ProfileScoreCalculator scoreCalculator;
    private final ProfileQualityGuard qualityGuard;
    private final ObjectMapper objectMapper;

    @Override
    public ProfileAiResult evaluate(UserProfile profile, String featureType) {
        JobFamily jobFamily = JobFamily.classify(profile);
        Map<ScoreCriterion, Integer> weights = weightPolicy.weightsFor(jobFamily);
        List<String> skills = extractSkillNames(profile);
        List<ProfileCriterionScore> criteria = scoreCalculator.applyWeights(
                weights,
                rawScores(profile, skills),
                evidence(profile, skills),
                improvements(profile));
        int score = scoreCalculator.totalScore(criteria);
        List<String> gaps = criteria.stream()
                .filter(row -> row.rawScore() < 70)
                .map(row -> row.criterion().label())
                .toList();
        List<String> recommendations = criteria.stream()
                .filter(row -> !row.improvement().isBlank())
                .sorted((left, right) -> Integer.compare(right.weight(), left.weight()))
                .limit(4)
                .map(ProfileCriterionScore::improvement)
                .toList();

        ProfileAiResult result = new ProfileAiResult(
                featureType,
                summary(profile, jobFamily, score, skills, gaps),
                skills,
                strengths(profile, skills),
                gaps,
                recommendations,
                score,
                jobFamily,
                criteria,
                new CareerAnalysisAiUsage("profile-rule-v2", 0, 0, 0, true),
                "SUCCESS",
                null);
        return qualityGuard.apply(profile, result);
    }

    public List<String> extractSkillNames(UserProfile profile) {
        Set<String> result = new LinkedHashSet<>();
        collectJsonValues(result, profile == null ? null : profile.getSkills());
        String text = profileText(profile);
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

    private Map<ScoreCriterion, Integer> rawScores(UserProfile profile, List<String> skills) {
        Map<ScoreCriterion, Integer> scores = new EnumMap<>(ScoreCriterion.class);
        scores.put(ScoreCriterion.GOAL_CLARITY, goalScore(profile));
        scores.put(ScoreCriterion.EXPERIENCE_SPECIFICITY, experienceScore(profile));
        scores.put(ScoreCriterion.ACHIEVEMENT_EVIDENCE, achievementScore(profile));
        scores.put(ScoreCriterion.JOB_SKILL_ALIGNMENT, skillScore(profile, skills));
        scores.put(ScoreCriterion.DOCUMENT_CONSISTENCY, documentScore(profile));
        scores.put(ScoreCriterion.IMPROVEMENT_READINESS, improvementScore(profile));
        return scores;
    }

    private Map<ScoreCriterion, String> evidence(UserProfile profile, List<String> skills) {
        Map<ScoreCriterion, String> evidence = new EnumMap<>(ScoreCriterion.class);
        evidence.put(ScoreCriterion.GOAL_CLARITY, hasText(profile.getDesiredJob())
                ? "희망 직무가 입력되어 있습니다."
                : "희망 직무가 비어 있습니다.");
        evidence.put(ScoreCriterion.EXPERIENCE_SPECIFICITY, hasText(profile.getCareer()) || hasText(profile.getProjects())
                ? "경력 또는 활동 기록이 입력되어 있습니다."
                : "경력/활동 기록이 부족합니다.");
        evidence.put(ScoreCriterion.ACHIEVEMENT_EVIDENCE, NUMBER_EVIDENCE.matcher(profileText(profile)).find()
                ? "수치 또는 기간처럼 확인 가능한 근거가 포함되어 있습니다."
                : "성과를 증명할 수치와 결과 표현이 부족합니다.");
        evidence.put(ScoreCriterion.JOB_SKILL_ALIGNMENT, skills.isEmpty()
                ? "추출 가능한 직무 역량 키워드가 부족합니다."
                : "추출된 직무 역량: " + String.join(", ", skills.subList(0, Math.min(5, skills.size()))));
        evidence.put(ScoreCriterion.DOCUMENT_CONSISTENCY, hasText(profile.getResumeText()) && hasText(profile.getSelfIntro())
                ? "이력서 본문과 자기소개가 함께 입력되어 있습니다."
                : "이력서 본문 또는 자기소개가 부족합니다.");
        evidence.put(ScoreCriterion.IMPROVEMENT_READINESS, "입력된 항목을 기준으로 보완 우선순위를 계산했습니다.");
        return evidence;
    }

    private Map<ScoreCriterion, String> improvements(UserProfile profile) {
        Map<ScoreCriterion, String> improvements = new EnumMap<>(ScoreCriterion.class);
        improvements.put(ScoreCriterion.GOAL_CLARITY, "희망 직무, 산업, 근무 조건을 한 문장으로 연결해 작성하세요.");
        improvements.put(ScoreCriterion.EXPERIENCE_SPECIFICITY, "경험마다 역할, 수행 업무, 사용 역량을 분리해서 작성하세요.");
        improvements.put(ScoreCriterion.ACHIEVEMENT_EVIDENCE, "성과에는 숫자, 기간, 개선 전후 상태를 함께 남기세요.");
        improvements.put(ScoreCriterion.JOB_SKILL_ALIGNMENT, "희망 직무에서 자주 요구되는 역량을 스킬 목록과 경험 문장에 반복해서 연결하세요.");
        improvements.put(ScoreCriterion.DOCUMENT_CONSISTENCY, "이력서, 자기소개, 포트폴리오 링크의 핵심 경험이 같은 방향을 가리키게 정리하세요.");
        improvements.put(ScoreCriterion.IMPROVEMENT_READINESS, "부족한 항목을 먼저 채우고 다시 AI 진단을 실행하세요.");
        return improvements;
    }

    private int goalScore(UserProfile profile) {
        int score = 0;
        if (hasText(profile.getDesiredJob())) score += 55;
        if (hasText(profile.getDesiredIndustry())) score += 25;
        if (hasText(profile.getPreferences())) score += 20;
        return score;
    }

    private int experienceScore(UserProfile profile) {
        int score = 0;
        if (hasText(profile.getCareer())) score += 35;
        if (hasText(profile.getProjects())) score += 35;
        if (hasText(profile.getEducation())) score += 15;
        if (hasText(profile.getResumeText())) score += 15;
        return score;
    }

    private int achievementScore(UserProfile profile) {
        String text = profileText(profile);
        int score = NUMBER_EVIDENCE.matcher(text).find() ? 50 : 15;
        if (containsAny(text, "개선", "증가", "감소", "달성", "성과", "수상", "합격", "매출", "만족도")) score += 35;
        if (hasText(profile.getPortfolioLinks())) score += 15;
        return Math.min(score, 100);
    }

    private int skillScore(UserProfile profile, List<String> skills) {
        int score = Math.min(70, skills.size() * 12);
        if (hasText(profile.getCertificates())) score += 15;
        if (hasText(profile.getLanguages())) score += 10;
        if (hasText(profile.getProjects()) || hasText(profile.getCareer())) score += 5;
        return Math.min(score, 100);
    }

    private int documentScore(UserProfile profile) {
        int score = 0;
        if (hasText(profile.getResumeText())) score += 30;
        if (hasText(profile.getSelfIntro())) score += 30;
        if (hasText(profile.getPortfolioLinks())) score += 20;
        if (hasText(profile.getCertificates()) || hasText(profile.getLanguages())) score += 20;
        return score;
    }

    private int improvementScore(UserProfile profile) {
        int score = 40;
        if (hasText(profile.getDesiredJob())) score += 15;
        if (hasText(profile.getSkills())) score += 15;
        if (hasText(profile.getCareer()) || hasText(profile.getProjects())) score += 15;
        if (hasText(profile.getSelfIntro())) score += 15;
        return Math.min(score, 100);
    }

    private String summary(UserProfile profile, JobFamily jobFamily, int score, List<String> skills, List<String> gaps) {
        String desiredJob = hasText(profile.getDesiredJob()) ? profile.getDesiredJob() : "희망 직무 미입력";
        String skillText = skills.isEmpty() ? "추출된 역량이 아직 부족함" : String.join(", ", skills.subList(0, Math.min(5, skills.size())));
        String gapText = gaps.isEmpty() ? "큰 결측 항목은 없습니다." : "우선 보완 항목은 " + String.join(", ", gaps) + "입니다.";
        return "%s 기준으로 %s 직무 준비도를 평가했습니다. 핵심 역량 후보는 %s이며, 가중치 기반 점수는 %d점입니다. %s"
                .formatted(jobFamily.label(), desiredJob, skillText, score, gapText);
    }

    private List<String> strengths(UserProfile profile, List<String> skills) {
        List<String> strengths = new ArrayList<>();
        if (!skills.isEmpty()) strengths.add("직무 역량 키워드가 " + skills.size() + "개 정리되어 있습니다.");
        if (hasText(profile.getProjects())) strengths.add("경험/프로젝트/활동 기록이 입력되어 있습니다.");
        if (hasText(profile.getSelfIntro())) strengths.add("자기소개 문장이 있어 지원 방향을 해석할 수 있습니다.");
        if (hasText(profile.getPortfolioLinks())) strengths.add("포트폴리오 또는 활동 링크가 연결되어 있습니다.");
        return strengths;
    }

    private void collectJsonValues(Set<String> result, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            Queue<JsonNode> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                JsonNode node = queue.remove();
                if (node.isTextual() && !node.asText().isBlank()) {
                    result.add(node.asText().trim());
                } else if (node.isArray() || node.isObject()) {
                    node.forEach(queue::add);
                }
            }
        } catch (Exception ignored) {
            // Free-form legacy profile fields can be ignored for skill extraction.
        }
    }

    private String profileText(UserProfile profile) {
        if (profile == null) {
            return "";
        }
        return String.join("\n",
                value(profile.getDesiredJob()),
                value(profile.getDesiredIndustry()),
                value(profile.getEducation()),
                value(profile.getCareer()),
                value(profile.getProjects()),
                value(profile.getSkills()),
                value(profile.getCertificates()),
                value(profile.getLanguages()),
                value(profile.getPortfolioLinks()),
                value(profile.getResumeText()),
                value(profile.getSelfIntro()),
                value(profile.getPreferences()));
    }

    private boolean containsAny(String text, String... tokens) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"[]".equals(value) && !"{}".equals(value) && !"null".equals(value);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
