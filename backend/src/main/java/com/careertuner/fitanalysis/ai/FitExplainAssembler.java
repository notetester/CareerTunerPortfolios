package com.careertuner.fitanalysis.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog;

import tools.jackson.databind.JsonNode;

/**
 * 외부 provider(Claude/OpenAI) 폴백 경로용 <b>뉴로-심볼릭 설명 조립기</b>.
 *
 * <p>판단값(fitScore/matchedSkills/missingSkills/applyDecision/조건매트릭스/로드맵/자격증)은 서버 규칙엔진
 * ({@link MockFitAnalysisAiService})의 skeleton 을 그대로 쓰고, LLM 은 설명 JSON
 * (fitSummary/strengths/risks/strategyActions/learningTaskReasons)만 생성한다 — OSS 경로
 * ({@link OssFitAnalysisAiService})와 동일 계약({@code FIT_EXPLAIN_SYSTEM_PROMPT}). 이로써 어느 provider 로
 * 폴백해도 점수·판단의 소유자는 항상 규칙엔진이다(provider 간 판단값 소유 비대칭 제거).
 *
 * <p>grounding 검사는 {@link OssFitAnalysisAiService#groundingViolation}(E1과 동일 휴리스틱, static)을
 * 재사용한다. 위반 시 재시도 없이 예외를 던진다 — 외부 provider 는 이미 폴백 단계라, 상위
 * {@link FallbackFitAnalysisAiService} 체인(다음 provider → 최종 mock)이 안전망이다.
 *
 * <p>입력 전처리·병합 로직은 {@link OssFitAnalysisAiService} 의 §2~§4 와 의도적으로 동일하다(둘을 함께 고칠 것).
 * OSS 쪽을 이 조립기로 리팩터링하지 않은 이유: OSS 는 E1 hard guard(재호출 루프)를 내장한 검증된 경로라
 * 이번 변경에서 손대지 않는다(약화 금지 원칙).
 */
@Component
public class FitExplainAssembler {

    /** 구조화 출력 스키마 이름(OpenAI json_schema 용, Anthropic 은 미사용). */
    public static final String EXPLAIN_SCHEMA_NAME = "fit_explain";

    /** 설명 전용 출력 스키마 — FIT_EXPLAIN_SYSTEM_PROMPT 의 JSON 계약과 동일. 판단값 필드는 없다. */
    public Map<String, Object> explainSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("fitSummary", string());
        properties.put("strengths", stringArray());
        properties.put("risks", stringArray());
        properties.put("strategyActions", stringArray());
        properties.put("learningTaskReasons", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of("skill", string(), "why", string()),
                        "required", List.of("skill", "why"))));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.copyOf(properties.keySet()));
    }

    /** 자체모델과 동일한 C_FIT_EXPLAIN 입력 본문(규칙엔진 사전계산값 포함)을 만든다. */
    public String explainUserPrompt(FitAnalysisAiCommand command, FitAnalysisAiResult skeleton) {
        List<String> matched = nullSafe(skeleton.matchedSkills());
        List<String> missingRequired = subtract(command.requiredSkills(), matched);
        List<String> missingPreferred = subtract(command.preferredSkills(), matched);
        return FitAnalysisPromptCatalog.fitExplainUserPrompt(
                command.companyName(), command.jobTitle(), command.desiredJob(),
                join(command.requiredSkills()), join(command.preferredSkills()), command.duties(),
                join(command.profileSkills()), join(command.profileCertificates()),
                skeleton.fitScore(), trainingDecision(skeleton.applyDecision()),
                join(matched), join(missingRequired), join(missingPreferred),
                command.companyContext(), command.profileInsight());
    }

    /**
     * 설명 payload 를 검증(fitSummary 존재 + grounding)하고 skeleton 과 병합한다.
     * 판단값은 전부 skeleton(규칙엔진) 소유 — payload 에 점수/판단 유사 필드가 있어도 읽지 않는다(구조적 제거).
     */
    public FitAnalysisAiResult assemble(FitAnalysisAiCommand command,
                                        FitAnalysisAiResult skeleton,
                                        JsonNode explain,
                                        CareerAnalysisAiUsage usage) {
        String fitSummary = explain.path("fitSummary").asText("").trim();
        if (fitSummary.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "provider 설명(fitSummary)이 비어 있습니다.");
        }
        // E1 과 동일 휴리스틱으로 '부족 역량을 보유로 서술' 검사(스킬명만 노출, 원문 미포함).
        String violation = OssFitAnalysisAiService.groundingViolation(
                groundingMissing(command, skeleton), fitSummary, strings(explain.path("strengths")));
        if (violation != null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "provider 설명이 부족 역량을 보유로 서술(grounding 위반): " + violation);
        }

        List<String> modelActions = strings(explain.path("strategyActions"));
        List<String> strategyActions = modelActions.isEmpty() ? skeleton.strategyActions() : modelActions;
        List<FitGapRecommendation> gaps =
                enrichGapReasons(skeleton.gapRecommendations(), explain.path("learningTaskReasons"));
        return new FitAnalysisAiResult(
                skeleton.fitScore(),
                skeleton.matchedSkills(),
                skeleton.missingSkills(),
                skeleton.recommendedStudy(),
                skeleton.recommendedCertificates(),
                fitSummary,                      // strategy ← 모델 설명(fitSummary)
                skeleton.scoreBasis(),
                gaps,                            // gap reason ← 모델 learningTaskReasons(매칭 시)
                skeleton.learningRoadmap(),
                skeleton.certificateRecommendations(),
                strategyActions,                 // strategyActions ← 모델(비면 skeleton)
                skeleton.conditionMatrix(),
                skeleton.applyDecision(),        // 지원판단 ← 규칙엔진(서버 권위)
                usage,
                "SUCCESS",
                null,
                false);
    }

    /** grounding 검사용 부족 역량: 필수+우대 미매칭에서 보유 자격증 제외(OSS §3 과 동일 — 오탐 방지). */
    private List<String> groundingMissing(FitAnalysisAiCommand command, FitAnalysisAiResult skeleton) {
        List<String> matched = nullSafe(skeleton.matchedSkills());
        List<String> missing = new ArrayList<>(subtract(command.requiredSkills(), matched));
        missing.addAll(subtract(command.preferredSkills(), matched));
        List<String> heldCerts = command.profileCertificates();
        if (heldCerts != null && !heldCerts.isEmpty()) {
            Set<String> held = new HashSet<>();
            for (String cert : heldCerts) {
                if (cert != null) {
                    held.add(cert.toLowerCase(Locale.ROOT));
                }
            }
            missing.removeIf(skill -> skill != null && held.contains(skill.toLowerCase(Locale.ROOT)));
        }
        return missing;
    }

    /** Mock 의 COMPLEMENT 를 학습 라벨 COMPLEMENT_BEFORE_APPLY 로 맞춘다(train/serve 정합, OSS 와 동일). */
    private static String trainingDecision(FitApplyDecision decision) {
        if (decision == null) {
            return "COMPLEMENT_BEFORE_APPLY";
        }
        return "COMPLEMENT".equals(decision.decision()) ? "COMPLEMENT_BEFORE_APPLY" : decision.decision();
    }

    /** 모델의 learningTaskReasons(skill→why)로 규칙엔진 gap 의 reason 만 교체(category/priority 는 유지). */
    private static List<FitGapRecommendation> enrichGapReasons(List<FitGapRecommendation> gaps, JsonNode reasons) {
        if (gaps == null || gaps.isEmpty() || reasons == null || !reasons.isArray()) {
            return gaps;
        }
        Map<String, String> reasonBySkill = new HashMap<>();
        for (JsonNode item : reasons) {
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

    private static List<String> subtract(List<String> from, List<String> matched) {
        if (from == null || from.isEmpty()) {
            return List.of();
        }
        Set<String> matchedLower = new HashSet<>();
        for (String value : matched) {
            if (value != null) {
                matchedLower.add(value.toLowerCase(Locale.ROOT));
            }
        }
        List<String> out = new ArrayList<>();
        for (String value : from) {
            if (value != null && !value.isBlank() && !matchedLower.contains(value.toLowerCase(Locale.ROOT))) {
                out.add(value);
            }
        }
        return out;
    }

    private static List<String> strings(JsonNode node) {
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

    private static List<String> nullSafe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "(없음)";
        }
        return String.join(", ", values);
    }

    private static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> stringArray() {
        return Map.of("type", "array", "items", string());
    }
}
