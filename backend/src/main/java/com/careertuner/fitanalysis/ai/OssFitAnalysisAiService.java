package com.careertuner.fitanalysis.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiProviderProperties;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOssClient;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog;

import tools.jackson.databind.JsonNode;

/**
 * C 자체 파인튜닝 모델 기반 적합도 분석(뉴로-심볼릭 조립).
 *
 * <p>핵심: 점수/매칭/부족/지원판단/조건매트릭스/자격증 같은 <b>판단값은 서버 규칙엔진({@link MockFitAnalysisAiService})</b>이
 * 결정론적으로 계산하고, 자체모델({@code careertuner-c-career-strategy-3b})은 그 값을 입력으로 받아
 * <b>한국어 설명 텍스트만</b> 생성한다. 두 결과를 병합해 {@link FitAnalysisAiResult} 를 만든다.
 *
 * <p>모델 호출/검증 실패 시 {@link BusinessException} 을 던져 상위 {@link FallbackFitAnalysisAiService}
 * 가 OpenAI → Mock 으로 폴백하게 한다(화면은 깨지지 않는다).
 *
 * <p>모델이 fitScore/score/applyDecision/decision 같은 금지키를 출력해도 이 서비스는 그 값을 읽지 않고
 * 규칙엔진 값만 사용하므로(화이트리스트: fitSummary/strategyActions/learningTaskReasons), 금지키는 결과에 반영되지 않는다.
 */
@Service
public class OssFitAnalysisAiService implements FitAnalysisAiService {

    private final CareerAnalysisOssClient ossClient;
    private final MockFitAnalysisAiService ruleEngine;
    private final CareerAnalysisAiProviderProperties properties;

    public OssFitAnalysisAiService(CareerAnalysisOssClient ossClient,
                                   MockFitAnalysisAiService ruleEngine,
                                   CareerAnalysisAiProviderProperties properties) {
        this.ossClient = ossClient;
        this.ruleEngine = ruleEngine;
        this.properties = properties;
    }

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        // 1. 규칙엔진 골격(점수·매칭·부족·지원판단·조건매트릭스·로드맵·자격증 — 서버 권위, 결정론)
        FitAnalysisAiResult skeleton = ruleEngine.generate(command);

        // 2. 자체모델 입력 구성(점수/판단/매칭/부족을 '입력'으로 제공 — build_fit_user 동등)
        List<String> matched = nullSafe(skeleton.matchedSkills());
        List<String> missingRequired = subtract(command.requiredSkills(), matched);
        List<String> missingPreferred = subtract(command.preferredSkills(), matched);
        String userPrompt = FitAnalysisPromptCatalog.fitExplainUserPrompt(
                command.companyName(), command.jobTitle(), command.desiredJob(),
                join(command.requiredSkills()), join(command.preferredSkills()), command.duties(),
                join(command.profileSkills()), join(command.profileCertificates()),
                skeleton.fitScore(), trainingDecision(skeleton.applyDecision()),
                join(matched), join(missingRequired), join(missingPreferred));

        // 3. 자체모델 호출(설명만). 실패/검증실패 → throw → 상위 폴백.
        JsonNode explain = ossClient.requestFitExplain(FitAnalysisPromptCatalog.FIT_EXPLAIN_SYSTEM_PROMPT, userPrompt);
        String fitSummary = explain.path("fitSummary").asText("").trim();
        if (fitSummary.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델 설명(fitSummary)이 비어 있습니다.");
        }

        // 4. 병합: 골격(symbolic) + 모델 텍스트(neural). 금지키는 읽지 않음(구조적 제거).
        List<String> modelActions = strings(explain.path("strategyActions"));
        List<String> strategyActions = modelActions.isEmpty() ? skeleton.strategyActions() : modelActions;
        List<FitGapRecommendation> gaps = enrichGapReasons(skeleton.gapRecommendations(), explain.path("learningTaskReasons"));
        CareerAnalysisAiUsage usage = new CareerAnalysisAiUsage(properties.getOss().getModel(), 0, 0, 0, false);

        return new FitAnalysisAiResult(
                skeleton.fitScore(),
                skeleton.matchedSkills(),
                skeleton.missingSkills(),
                skeleton.recommendedStudy(),
                skeleton.recommendedCertificates(),
                fitSummary,                       // strategy ← 자체모델 설명(fitSummary)
                skeleton.scoreBasis(),
                gaps,                             // gap reason ← 자체모델 learningTaskReasons(매칭 시)
                skeleton.learningRoadmap(),
                skeleton.certificateRecommendations(),
                strategyActions,                  // strategyActions ← 자체모델
                skeleton.conditionMatrix(),
                skeleton.applyDecision(),         // 지원판단 ← 규칙엔진(서버 권위)
                usage,
                "SUCCESS",
                null,
                false);
    }

    /** Mock 의 COMPLEMENT 를 학습 라벨 COMPLEMENT_BEFORE_APPLY 로 맞춘다(train/serve 정합). */
    private String trainingDecision(FitApplyDecision decision) {
        if (decision == null) {
            return "COMPLEMENT_BEFORE_APPLY";
        }
        return "COMPLEMENT".equals(decision.decision()) ? "COMPLEMENT_BEFORE_APPLY" : decision.decision();
    }

    private List<String> subtract(List<String> from, List<String> matched) {
        if (from == null || from.isEmpty()) {
            return List.of();
        }
        Set<String> matchedLower = new HashSet<>();
        for (String m : matched) {
            if (m != null) {
                matchedLower.add(m.toLowerCase(Locale.ROOT));
            }
        }
        List<String> out = new ArrayList<>();
        for (String s : from) {
            if (s != null && !s.isBlank() && !matchedLower.contains(s.toLowerCase(Locale.ROOT))) {
                out.add(s);
            }
        }
        return out;
    }

    private List<String> nullSafe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "(없음)";
        }
        return String.join(", ", values);
    }

    private List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    /** 자체모델의 learningTaskReasons(skill→why)로 규칙엔진 gap 의 reason 만 교체한다(category/priority 는 규칙엔진 유지). */
    private List<FitGapRecommendation> enrichGapReasons(List<FitGapRecommendation> gaps, JsonNode learningTaskReasons) {
        if (gaps == null || gaps.isEmpty() || learningTaskReasons == null || !learningTaskReasons.isArray()) {
            return gaps;
        }
        Map<String, String> reasonBySkill = new HashMap<>();
        for (JsonNode item : learningTaskReasons) {
            String skill = item.path("skill").asText("").trim();
            String why = item.path("why").asText("").trim();
            if (!skill.isBlank() && !why.isBlank()) {
                reasonBySkill.put(skill.toLowerCase(Locale.ROOT), why);
            }
        }
        if (reasonBySkill.isEmpty()) {
            return gaps;
        }
        List<FitGapRecommendation> out = new ArrayList<>();
        for (FitGapRecommendation gap : gaps) {
            String key = gap.skill() == null ? "" : gap.skill().toLowerCase(Locale.ROOT);
            String why = reasonBySkill.get(key);
            out.add(why == null ? gap : new FitGapRecommendation(gap.skill(), gap.category(), gap.priority(), why));
        }
        return out;
    }
}
