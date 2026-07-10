package com.careertuner.profile.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.careertuner.profile.domain.UserProfile;

@Component
public class ProfileQualityGuard {

    private static final Pattern NUMBER_OR_METRIC = Pattern.compile("\\d+|%|명|건|회|만원|원|시간|개월|년");
    private static final Pattern KOREAN_OR_ALPHA = Pattern.compile("[가-힣A-Za-z]");
    private static final Pattern REPEATED_CHAR = Pattern.compile("(.)\\1{4,}");
    private static final List<String> MEANINGLESS_TOKENS = List.of(
            "ㅋㅋ", "ㅎㅎ", "ㅠㅠ", "ㅜㅜ", "테스트", "test", "asdf", "qwer", "없음", "몰라", "아무거나", "내용없음"
    );

    private final ProfileScoreCalculator scoreCalculator;

    public ProfileQualityGuard(ProfileScoreCalculator scoreCalculator) {
        this.scoreCalculator = scoreCalculator;
    }

    public ProfileQualityReport inspect(UserProfile profile, JobFamily jobFamily) {
        EnumMap<ScoreCriterion, Integer> penalties = new EnumMap<>(ScoreCriterion.class);
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        int filledSections = filledSections(profile);
        if (filledSections < 3) {
            add(penalties, ScoreCriterion.IMPROVEMENT_READINESS, 15);
            warnings.add("핵심 프로필 항목이 충분히 채워지지 않았습니다.");
            recommendations.add("희망 직무, 주요 경험, 스킬, 자기소개를 우선 채워 주세요.");
        }

        if (!isMeaningful(profile == null ? null : profile.getDesiredJob(), 2, 80)) {
            add(penalties, ScoreCriterion.GOAL_CLARITY, 35);
            warnings.add("희망 직무가 비어 있거나 의미 있는 직무명으로 보기 어렵습니다.");
            recommendations.add("희망 직무는 '간호사', '마케팅 AE', '설비 엔지니어'처럼 실제 직무명으로 입력해 주세요.");
        }

        int meaninglessCount = meaninglessFieldCount(profile);
        if (meaninglessCount > 0) {
            int penalty = Math.min(30, meaninglessCount * 8);
            add(penalties, ScoreCriterion.EXPERIENCE_SPECIFICITY, penalty);
            add(penalties, ScoreCriterion.DOCUMENT_CONSISTENCY, Math.min(25, penalty));
            warnings.add("반복 문자, 테스트 문구, 너무 짧은 입력처럼 분석하기 어려운 내용이 포함되어 있습니다.");
            recommendations.add("각 경험은 맡은 역할, 수행한 일, 결과를 한 문장 이상으로 구체화해 주세요.");
        }

        String experienceText = join(
                value(profile == null ? null : profile.getCareer()),
                value(profile == null ? null : profile.getProjects()),
                value(profile == null ? null : profile.getPortfolioEvidence()),
                value(profile == null ? null : profile.getResumeText()),
                value(profile == null ? null : profile.getSelfIntro()));
        if (!isMeaningful(experienceText, 30, 4000)) {
            add(penalties, ScoreCriterion.EXPERIENCE_SPECIFICITY, 30);
            add(penalties, ScoreCriterion.DOCUMENT_CONSISTENCY, 20);
            warnings.add("경력, 활동, 이력서 또는 포트폴리오에서 의미 있는 경험 설명이 부족합니다.");
            recommendations.add("경험에는 문제 상황, 맡은 역할, 사용한 역량, 결과를 함께 작성해 주세요.");
        }

        if (isMeaningful(experienceText, 30, 4000) && !NUMBER_OR_METRIC.matcher(experienceText).find()) {
            add(penalties, ScoreCriterion.ACHIEVEMENT_EVIDENCE, 25);
            warnings.add("성과를 뒷받침할 수치, 기간, 규모, 결과 표현이 부족합니다.");
            recommendations.add("성과에는 기간, 건수, 비율, 개선 전후 상태처럼 확인 가능한 근거를 추가해 주세요.");
        }

        Relevance relevance = relevance(profile, jobFamily);
        if (relevance.low()) {
            add(penalties, ScoreCriterion.JOB_SKILL_ALIGNMENT, relevance.penalty());
            warnings.add("희망 직무와 입력한 스킬/경험의 관련성이 낮게 감지되었습니다.");
            recommendations.add("%s 직무군과 연결되는 핵심 키워드와 경험 근거를 더 추가해 주세요.".formatted(jobFamily.label()));
        }

        int totalPenalty = penalties.values().stream().mapToInt(Integer::intValue).sum();
        return new ProfileQualityReport(
                Math.min(45, totalPenalty / 2),
                penalties,
                distinct(warnings),
                distinct(recommendations));
    }

    public ProfileAiResult apply(UserProfile profile, ProfileAiResult result) {
        ProfileQualityReport report = inspect(profile, result.jobFamily());
        if (report.criterionPenalties().isEmpty()) {
            return result;
        }

        List<ProfileCriterionScore> adjustedCriteria = result.criteria().stream()
                .map(row -> adjust(row, report))
                .toList();
        int adjustedScore = Math.max(0, scoreCalculator.totalScore(adjustedCriteria) - report.penaltyScore());
        int qualityPenalty = Math.max(0, result.aiScore() - adjustedScore);
        List<String> gaps = distinct(concat(result.gaps(), report.warnings()));
        List<String> recommendations = distinct(concat(result.recommendations(), report.recommendations()));

        return new ProfileAiResult(
                result.featureType(),
                qualitySummary(result.summary(), report),
                result.extractedSkills(),
                result.strengths(),
                gaps,
                recommendations,
                adjustedScore,
                result.jobFamily(),
                adjustedCriteria,
                result.usage(),
                result.status(),
                result.errorMessage(),
                result.aiScore(),
                qualityPenalty,
                report.warnings(),
                report.recommendations());
    }

    private ProfileCriterionScore adjust(ProfileCriterionScore row, ProfileQualityReport report) {
        int penalty = report.criterionPenalties().getOrDefault(row.criterion(), 0);
        if (penalty <= 0) {
            return row;
        }
        int rawScore = Math.max(0, row.rawScore() - penalty);
        double weightedScore = Math.round(rawScore * row.weight()) / 100.0;
        String evidence = append(row.evidence(), "서버 품질 검증에서 입력 품질 보정이 적용되었습니다.");
        return new ProfileCriterionScore(
                row.criterion(),
                rawScore,
                row.weight(),
                weightedScore,
                evidence,
                row.improvement());
    }

    private Relevance relevance(UserProfile profile, JobFamily jobFamily) {
        if (jobFamily == JobFamily.GENERAL) {
            return new Relevance(false, 0);
        }
        String desiredText = join(value(profile == null ? null : profile.getDesiredJob()), value(profile == null ? null : profile.getDesiredIndustry()));
        String evidenceText = join(
                value(profile == null ? null : profile.getSkills()),
                value(profile == null ? null : profile.getCareer()),
                value(profile == null ? null : profile.getProjects()),
                value(profile == null ? null : profile.getPortfolioEvidence()),
                value(profile == null ? null : profile.getResumeText()),
                value(profile == null ? null : profile.getSelfIntro()));
        int desiredMatches = keywordMatches(jobFamily, desiredText);
        int evidenceMatches = keywordMatches(jobFamily, evidenceText);
        int otherFamilyBest = otherFamilyBestMatches(jobFamily, evidenceText);

        if (desiredMatches > 0 && evidenceMatches == 0) {
            return new Relevance(true, 35);
        }
        if (desiredMatches > 0 && evidenceMatches <= 1 && otherFamilyBest >= evidenceMatches + 2) {
            return new Relevance(true, 25);
        }
        if (desiredMatches > 0 && evidenceMatches <= 1) {
            return new Relevance(true, 15);
        }
        return new Relevance(false, 0);
    }

    private int filledSections(UserProfile profile) {
        int count = 0;
        if (hasText(profile == null ? null : profile.getDesiredJob())) count++;
        if (hasText(profile == null ? null : profile.getSkills())) count++;
        if (hasText(profile == null ? null : profile.getCareer())) count++;
        if (hasText(profile == null ? null : profile.getProjects())) count++;
        if (hasText(profile == null ? null : profile.getPortfolioEvidence())) count++;
        if (hasText(profile == null ? null : profile.getResumeText())) count++;
        if (hasText(profile == null ? null : profile.getSelfIntro())) count++;
        return count;
    }

    private int meaninglessFieldCount(UserProfile profile) {
        return List.of(
                        value(profile == null ? null : profile.getDesiredJob()),
                        value(profile == null ? null : profile.getDesiredIndustry()),
                        value(profile == null ? null : profile.getCareer()),
                        value(profile == null ? null : profile.getProjects()),
                        value(profile == null ? null : profile.getPortfolioEvidence()),
                        value(profile == null ? null : profile.getSkills()),
                        value(profile == null ? null : profile.getResumeText()),
                        value(profile == null ? null : profile.getSelfIntro()))
                .stream()
                .mapToInt(value -> isPresentButNotMeaningful(value) ? 1 : 0)
                .sum();
    }

    private boolean isPresentButNotMeaningful(String value) {
        return hasText(value) && !isMeaningful(value, 3, 4000);
    }

    private boolean isMeaningful(String value, int minLetters, int maxLengthForCheck) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = value.replaceAll("[\\[\\]{}\"':,._\\-\\s]", "").toLowerCase(Locale.ROOT);
        if (normalized.length() > maxLengthForCheck) {
            return true;
        }
        if (REPEATED_CHAR.matcher(normalized).find()) {
            return false;
        }
        for (String token : MEANINGLESS_TOKENS) {
            if (normalized.contains(token.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        long letterCount = normalized.codePoints()
                .filter(codePoint -> KOREAN_OR_ALPHA.matcher(new String(Character.toChars(codePoint))).matches())
                .count();
        return letterCount >= minLetters;
    }

    private int keywordMatches(JobFamily family, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int matches = 0;
        for (String keyword : family.keywords()) {
            if (!keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                matches++;
            }
        }
        return matches;
    }

    private int otherFamilyBestMatches(JobFamily target, String text) {
        int best = 0;
        for (JobFamily family : JobFamily.values()) {
            if (family == target || family == JobFamily.GENERAL) {
                continue;
            }
            best = Math.max(best, keywordMatches(family, text));
        }
        return best;
    }

    private void add(Map<ScoreCriterion, Integer> penalties, ScoreCriterion criterion, int penalty) {
        penalties.merge(criterion, penalty, Integer::sum);
    }

    private List<String> concat(List<String> left, List<String> right) {
        List<String> values = new ArrayList<>();
        values.addAll(left == null ? List.of() : left);
        values.addAll(right == null ? List.of() : right);
        return values;
    }

    private List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private String qualitySummary(String summary, ProfileQualityReport report) {
        if (report.warnings().isEmpty()) {
            return summary;
        }
        return append(summary, "단, 서버 품질 검증 결과 일부 입력은 의미성/직무 관련성 보정이 필요합니다.");
    }

    private String append(String base, String addition) {
        if (base == null || base.isBlank()) {
            return addition;
        }
        return base + " " + addition;
    }

    private String join(String... values) {
        return String.join("\n", values);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"[]".equals(value) && !"{}".equals(value) && !"null".equals(value);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private record Relevance(boolean low, int penalty) {
    }
}
