package com.careertuner.fitanalysis.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;

/**
 * 적합도 분석 AI의 mock 구현 (API 키 미발급 단계 기본 동작).
 *
 * <p>외부 호출 없이 공고 필수/우대 역량과 프로필 보유 기술을 비교해 결정적(deterministic) 결과를 만든다.
 * 같은 입력은 항상 같은 결과를 주므로 화면/관리자 통계 흐름을 그대로 검증할 수 있다.
 * 실제 LLM 연동은 {@link FitAnalysisAiService} 문서의 교체 절차를 따른다.
 */
@Service
public class MockFitAnalysisAiService implements FitAnalysisAiService {

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        List<String> required = clean(command.requiredSkills());
        List<String> preferred = clean(command.preferredSkills());
        List<String> profile = clean(command.profileSkills());
        Set<String> profileLower = lower(profile);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        if (required.isEmpty()) {
            // 공고 분석 전이라면 기본 가이드 역량으로 안내한다.
            missing.addAll(List.of("공고 분석 먼저 실행", "필수 역량 확인 필요"));
        } else if (profile.isEmpty()) {
            // 프로필 미입력 상태에서 보유를 추정하면 점수가 과대평가되므로 필수 역량을 모두 미충족으로 둔다.
            missing.addAll(required);
        } else {
            for (String skill : required) {
                (profileLower.contains(skill.toLowerCase(Locale.ROOT)) ? matched : missing).add(skill);
            }
        }

        // 우대 역량 중 미보유는 장기 보완 항목으로 분류한다.
        for (String skill : preferred) {
            if (!profileLower.contains(skill.toLowerCase(Locale.ROOT)) && !missing.contains(skill)) {
                missing.add(skill);
            }
        }

        int fitScore = score(required, preferred, profileLower, profile.isEmpty());
        List<String> study = missing.stream().limit(4).map(skill -> skill + " 집중 학습").toList();
        List<String> certificates = recommendCertificates(command.desiredJob());
        String strategy = strategy(command, matched, missing, fitScore);
        List<String> scoreBasis = scoreBasis(required, matched, missing, fitScore);
        List<FitGapRecommendation> gapRecommendations = gapRecommendations(required, preferred, missing);
        List<FitLearningRoadmapItem> learningRoadmap = learningRoadmap(gapRecommendations);
        List<FitCertificateRecommendation> certificateRecommendations = certificateRecommendations(certificates, command.desiredJob());
        List<String> strategyActions = strategyActions(matched, gapRecommendations, fitScore);
        List<FitConditionMatch> conditionMatrix = conditionMatrix(required, preferred, profileLower);
        FitApplyDecision applyDecision = applyDecision(fitScore, matched, gapRecommendations);

        return new FitAnalysisAiResult(
                fitScore,
                matched,
                missing,
                study,
                certificates,
                strategy,
                scoreBasis,
                gapRecommendations,
                learningRoadmap,
                certificateRecommendations,
                strategyActions,
                conditionMatrix,
                applyDecision,
                CareerAnalysisAiUsage.mockUsage(),
                "SUCCESS",
                null,
                false);
    }

    /**
     * 요구조건-스펙 비교 매트릭스. 필수/우대 역량을 행으로 두고 보유 여부와 근거를 판정한다.
     * 부분 일치(예: "AWS EC2" 보유, 조건 "AWS")는 PARTIAL로 분류한다.
     */
    private List<FitConditionMatch> conditionMatrix(List<String> required, List<String> preferred, Set<String> profileLower) {
        List<FitConditionMatch> rows = new ArrayList<>();
        for (String skill : required) {
            rows.add(conditionRow(skill, "REQUIRED", profileLower));
        }
        for (String skill : preferred) {
            rows.add(conditionRow(skill, "PREFERRED", profileLower));
        }
        return rows;
    }

    private FitConditionMatch conditionRow(String skill, String conditionType, Set<String> profileLower) {
        String lower = skill.toLowerCase(Locale.ROOT);
        if (profileLower.contains(lower)) {
            return new FitConditionMatch(skill, conditionType, "MET", "프로필 보유 기술에서 동일 항목이 확인됩니다.");
        }
        boolean partial = profileLower.stream()
                .anyMatch(owned -> !owned.isBlank() && (owned.contains(lower) || lower.contains(owned)));
        if (partial) {
            return new FitConditionMatch(skill, conditionType, "PARTIAL", "프로필에 유사/연관 기술이 있어 부분 충족으로 판정합니다.");
        }
        return new FitConditionMatch(skill, conditionType, "UNMET", "프로필 보유 기술에서 확인되지 않습니다.");
    }

    /** 지원 판단 카드. 점수와 필수 미충족 개수로 지원 가능/보완 후 지원/지원 보류를 결정한다. */
    private FitApplyDecision applyDecision(int fitScore, List<String> matched, List<FitGapRecommendation> gaps) {
        long requiredMissing = gaps.stream().filter(gap -> "REQUIRED_MISSING".equals(gap.category())).count();

        String decision;
        List<String> reasons = new ArrayList<>();
        if (fitScore >= 70 && requiredMissing <= 1) {
            decision = "APPLY";
            reasons.add("적합도 %d점으로 현재 스펙 기준 지원 가능성이 높습니다.".formatted(fitScore));
            if (!matched.isEmpty()) {
                reasons.add("핵심 요구 역량 %s 이(가) 프로필과 매칭됩니다.".formatted(
                        String.join(", ", matched.subList(0, Math.min(3, matched.size())))));
            }
        } else if (fitScore >= 50 || (requiredMissing >= 2 && fitScore >= 40)) {
            decision = "COMPLEMENT";
            reasons.add("적합도 %d점이며 필수 미충족 항목이 %d개라 보완 후 지원을 권장합니다.".formatted(fitScore, requiredMissing));
            gaps.stream().filter(gap -> "HIGH".equals(gap.priority())).limit(2)
                    .forEach(gap -> reasons.add("%s 은(는) 공고 필수 역량이지만 프로필에서 확인되지 않습니다.".formatted(gap.skill())));
        } else {
            decision = "HOLD";
            reasons.add("적합도 %d점으로 기본 요구 역량 충족도가 낮아 지원 보류를 권장합니다.".formatted(fitScore));
            if (requiredMissing > 0) {
                reasons.add("필수 요구 역량 %d개가 미충족 상태입니다.".formatted(requiredMissing));
            }
        }

        List<String> actions = new ArrayList<>();
        switch (decision) {
            case "APPLY" -> {
                actions.add("지원서에 매칭 역량 경험을 수치·역할 중심으로 정리합니다.");
                actions.add("마감 전 지원을 진행하고 면접 답변 준비를 병행합니다.");
            }
            case "COMPLEMENT" -> {
                gaps.stream().filter(gap -> "HIGH".equals(gap.priority())).findFirst()
                        .ifPresent(gap -> actions.add("%s 보완 결과물(작은 프로젝트/실습 기록)을 먼저 준비합니다.".formatted(gap.skill())));
                actions.add("학습 로드맵의 상위 과제를 완료한 뒤 적합도 재분석을 실행합니다.");
            }
            default -> {
                actions.add("핵심 부족 역량부터 학습 로드맵 순서대로 보완합니다.");
                actions.add("현재 스펙과 더 맞는 공고를 우선 탐색하고, 보완 후 재분석합니다.");
            }
        }
        return new FitApplyDecision(decision, reasons, actions);
    }

    private List<String> scoreBasis(List<String> required, List<String> matched, List<String> missing, int fitScore) {
        return List.of(
                "필수 역량 %d개 중 %d개가 현재 프로필과 매칭됩니다.".formatted(required.size(), matched.size()),
                "필수·우대 조건 기준 보완 항목은 %d개입니다.".formatted(missing.size()),
                "현재 입력 기준 직무 적합도는 %d점이며, 프로필과 공고 분석이 갱신되면 점수가 달라질 수 있습니다.".formatted(fitScore));
    }

    private List<FitGapRecommendation> gapRecommendations(List<String> required,
                                                          List<String> preferred,
                                                          List<String> missing) {
        List<FitGapRecommendation> result = new ArrayList<>();
        for (String skill : missing) {
            if (required.contains(skill)) {
                result.add(new FitGapRecommendation(
                        skill,
                        "REQUIRED_MISSING",
                        "HIGH",
                        "공고 필수 역량이지만 현재 프로필에서 확인되지 않습니다."));
            } else if (preferred.contains(skill)) {
                result.add(new FitGapRecommendation(
                        skill,
                        "PREFERRED_GAP",
                        "MEDIUM",
                        "우대 조건 경쟁력을 높이기 위해 보완을 권장합니다."));
            } else {
                result.add(new FitGapRecommendation(
                        skill,
                        "LONG_TERM_GROWTH",
                        "LOW",
                        "희망 직무의 장기 경쟁력을 위해 학습할 가치가 있습니다."));
            }
        }
        return result;
    }

    private List<FitLearningRoadmapItem> learningRoadmap(List<FitGapRecommendation> gaps) {
        List<FitLearningRoadmapItem> result = new ArrayList<>();
        int order = 1;
        for (FitGapRecommendation gap : gaps.stream().limit(3).toList()) {
            result.add(new FitLearningRoadmapItem(
                    gap.skill(),
                    "%s 1단계 · 핵심 개념 정리".formatted(gap.skill()),
                    "%s의 핵심 개념과 자주 쓰는 실무 패턴을 예제 코드와 함께 정리합니다.".formatted(gap.skill()),
                    "3~5일",
                    gap.priority(),
                    order++));
            result.add(new FitLearningRoadmapItem(
                    gap.skill(),
                    "%s 2단계 · 적용 실습".formatted(gap.skill()),
                    "%s을(를) 활용한 작은 기능을 구현하고 동작을 테스트합니다.".formatted(gap.skill()),
                    "1주",
                    gap.priority(),
                    order++));
            result.add(new FitLearningRoadmapItem(
                    gap.skill(),
                    "%s 3단계 · 포트폴리오 근거화".formatted(gap.skill()),
                    "README에 선택 이유, 문제 해결 과정, 검증 결과를 정리해 지원서와 면접에서 설명할 근거를 만듭니다.",
                    "2~3일",
                    gap.priority(),
                    order++));
        }
        return result;
    }

    private List<FitCertificateRecommendation> certificateRecommendations(List<String> certificates, String desiredJob) {
        List<FitCertificateRecommendation> result = new ArrayList<>();
        for (int index = 0; index < certificates.size(); index++) {
            String name = certificates.get(index);
            result.add(new FitCertificateRecommendation(
                    name,
                    index == 0 ? "HIGH" : index == 1 ? "MEDIUM" : "LOW",
                    "%s 직무 준비를 객관적으로 보완하는 데 활용할 수 있습니다.".formatted(
                            desiredJob == null || desiredJob.isBlank() ? "희망" : desiredJob)));
        }
        return result;
    }

    private List<String> strategyActions(List<String> matched, List<FitGapRecommendation> gaps, int fitScore) {
        List<String> actions = new ArrayList<>();
        if (!matched.isEmpty()) {
            actions.add("지원서에서 %s 경험을 수치와 역할 중심으로 강조합니다.".formatted(matched.get(0)));
        }
        gaps.stream()
                .filter(gap -> "HIGH".equals(gap.priority()))
                .findFirst()
                .ifPresent(gap -> actions.add("지원 전 %s 보완 결과물을 준비합니다.".formatted(gap.skill())));
        actions.add(fitScore >= 70
                ? "현재 지원을 진행하면서 면접 답변을 병행 준비합니다."
                : "핵심 부족 역량을 보완한 뒤 적합도 분석을 다시 실행합니다.");
        return actions;
    }

    private int score(List<String> required,
                      List<String> preferred,
                      Set<String> profileLower,
                      boolean profileEmpty) {
        if (required.isEmpty()) {
            return 0;
        }
        if (profileEmpty) {
            return 10;
        }
        long matchedRequired = required.stream()
                .filter(skill -> profileLower.contains(skill.toLowerCase(Locale.ROOT)))
                .count();
        long matchedPreferred = preferred.stream()
                .filter(skill -> profileLower.contains(skill.toLowerCase(Locale.ROOT)))
                .count();
        double requiredRatio = (double) matchedRequired / required.size();
        double preferredRatio = preferred.isEmpty() ? 0 : (double) matchedPreferred / preferred.size();
        int score = (int) Math.round(10 + requiredRatio * 70 + preferredRatio * 20);
        return Math.max(0, Math.min(100, score));
    }

    private List<String> recommendCertificates(String desiredJob) {
        String job = desiredJob == null ? "" : desiredJob.toLowerCase(Locale.ROOT);
        if (job.contains("데이터") || job.contains("data") || job.contains("ml") || job.contains("ai")) {
            return List.of("SQLD", "ADsP", "빅데이터분석기사");
        }
        if (job.contains("클라우드") || job.contains("cloud") || job.contains("devops") || job.contains("인프라")) {
            return List.of("AWS Solutions Architect Associate", "정보처리기사", "리눅스마스터");
        }
        if (job.contains("보안") || job.contains("security")) {
            return List.of("정보보안기사", "정보처리기사", "CPPG");
        }
        // 일반 개발 직무 기본 추천.
        return List.of("정보처리기사", "SQLD");
    }

    private String strategy(FitAnalysisAiCommand command, List<String> matched, List<String> missing, int fitScore) {
        String company = command.companyName() == null ? "지원 기업" : command.companyName();
        String job = command.jobTitle() == null ? "해당 직무" : command.jobTitle();
        StringBuilder sb = new StringBuilder();
        sb.append("%s %s 지원 적합도는 %d점입니다. ".formatted(company, job, fitScore));
        if (!matched.isEmpty()) {
            sb.append("강점인 %s 경험을 지원서와 면접에서 우선 강조하세요. "
                    .formatted(String.join(", ", matched.subList(0, Math.min(3, matched.size())))));
        }
        if (!missing.isEmpty()) {
            sb.append("부족한 %s 은(는) 단기 프로젝트나 학습으로 빠르게 보완하는 것이 좋습니다. "
                    .formatted(String.join(", ", missing.subList(0, Math.min(3, missing.size())))));
        }
        if (fitScore >= 70) {
            sb.append("현재 수준으로도 지원 가능성이 높으니 마감 전 지원을 권장합니다.");
        } else if (fitScore >= 50) {
            sb.append("핵심 부족 역량을 1~2개 보완하면 합격 가능성이 올라갑니다.");
        } else {
            sb.append("기본 요구 역량을 먼저 채운 뒤 재분석하는 전략을 추천합니다.");
        }
        return sb.toString();
    }

    private List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return new ArrayList<>(result);
    }

    private Set<String> lower(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
