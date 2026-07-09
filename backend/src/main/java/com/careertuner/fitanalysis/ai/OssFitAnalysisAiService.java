package com.careertuner.fitanalysis.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OssFitAnalysisAiService.class);

    /** '보유'를 뜻하는 표현(이게 있을 때만 grounding 위반 후보). */
    private static final String[] POSSESSION = {
            "보유", "갖춤", "갖추고", "갖추어", "강점", "경험 있", "경험을 보유",
            "활용 가능", "숙련", "기반이 있", "능숙", "능통"};
    /** 결핍·부정 표현(같은 문장에 있으면 위반 아님 — false-positive 방지). */
    private static final String[] LACK = {
            "부족", "없", "미보유", "부재", "않", "못", "결여", "갖추지", "보유하지", "미흡", "전무"};

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
                join(matched), join(missingRequired), join(missingPreferred), command.companyContext());

        // 3. 자체모델 호출(설명만) + grounding guard. '부족 역량을 보유로 서술'(reports/24 E1)하면 재호출, 소진 시 throw → 폴백.
        List<String> missing = new ArrayList<>(missingRequired);
        missing.addAll(missingPreferred);
        // ★보유 자격증 제외: 규칙엔진 missing 은 cert 를 스킬로 안 쳐서 보유 cert 가 missing 에 남는다.
        // 안 빼면 모델이 '정보처리기사 보유'(사실)를 말해도 오탐 → 과도 폴백(라이브 회귀 case 2 100% 폴백, reports/29).
        List<String> heldCerts = command.profileCertificates();
        if (heldCerts != null && !heldCerts.isEmpty()) {
            Set<String> held = new HashSet<>();
            for (String c : heldCerts) {
                if (c != null) {
                    held.add(c.toLowerCase(Locale.ROOT));
                }
            }
            missing.removeIf(s -> s != null && held.contains(s.toLowerCase(Locale.ROOT)));
        }
        int groundingRetries = Math.max(0, properties.getOss().getGroundingRetries());
        JsonNode explain;
        String fitSummary;
        int attempt = 0;
        while (true) {
            explain = ossClient.requestFitExplain(FitAnalysisPromptCatalog.FIT_EXPLAIN_SYSTEM_PROMPT, userPrompt);
            fitSummary = explain.path("fitSummary").asText("").trim();
            if (fitSummary.isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델 설명(fitSummary)이 비어 있습니다.");
            }
            String violation = groundingViolation(missing, fitSummary, strings(explain.path("strengths")));
            if (violation == null) {
                break;
            }
            log.warn("C 자체모델 grounding 위반(부족 역량을 보유로 서술): {} model={} attempt={}/{} → {}",
                    violation, properties.getOss().getModel(), attempt, groundingRetries,
                    attempt < groundingRetries ? "재호출" : "폴백");
            if (attempt >= groundingRetries) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "C 자체모델 설명이 부족 역량을 보유로 서술(grounding 위반)");
            }
            attempt++;
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

    /**
     * 부족 역량(missing)을 {@code fitSummary}/{@code strengths} 에서 '보유한 강점'처럼 서술하면 위반 문자열을 반환(없으면 null).
     *
     * <p>보수적: 한 문장에 '보유' 류 표현이 있고, '부족/없/않' 같은 결핍·부정 표현이 <b>없을 때만</b> 위반으로 본다
     * (예: "Kubernetes 경험이 부족" 정상, "즉시 지원하기보다는" 정상 — false-positive 회피).
     * risks/strategyActions/learningTaskReasons 는 부족 역량이 나오는 게 정상이라 검사하지 않는다.
     * 점수/판단은 규칙엔진이 소유하며 이 검사는 그 값을 만들거나 바꾸지 않는다.
     */
    static String groundingViolation(List<String> missing, String fitSummary, List<String> strengths) {
        if (missing == null || missing.isEmpty()) {
            return null;
        }
        if (fitSummary != null) {
            for (String sentence : fitSummary.split("[.!?。\\n]")) {
                String v = violationInSentence(sentence, missing, "fitSummary");
                if (v != null) {
                    return v;
                }
            }
        }
        if (strengths != null) {
            for (String item : strengths) {
                String v = violationInSentence(item, missing, "strengths");
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private static String violationInSentence(String sentence, List<String> missing, String field) {
        if (sentence == null || sentence.isBlank()) {
            return null;
        }
        String phrase = firstContaining(sentence, POSSESSION);
        if (phrase == null || firstContaining(sentence, LACK) != null) {
            return null;   // 보유 표현이 없거나, 결핍·부정 문맥이면 위반 아님
        }
        String lower = sentence.toLowerCase(Locale.ROOT);
        for (String skill : missing) {
            if (skill != null && !skill.isBlank() && lower.contains(skill.toLowerCase(Locale.ROOT))) {
                return "field=" + field + " missingSkill=" + skill + " phrase=" + phrase;
            }
        }
        return null;
    }

    private static String firstContaining(String text, String[] needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return needle;
            }
        }
        return null;
    }
}
