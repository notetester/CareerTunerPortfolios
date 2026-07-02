package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * 기업분석 canonical contract 의 provider 별 표현 계약 테스트(6단계).
 * 로컬/Claude 스키마와 OpenAI strict 스키마는 표현(required 최소화 vs required+nullable)은
 * 달라도 필드 집합은 동일해야 하며, 서버 canonicalizer 보정 정책이 provider 와 무관하게
 * 같은 payload 구조 위에서 동작할 수 있어야 한다.
 */
class BCompanyAnalysisSchemaContractTest {

    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "companySummary", "recentIssues", "industry", "competitors", "interviewPoints",
            "sources", "verifiedFacts", "aiInferences", "unknowns");
    private static final Set<String> LOCAL_TOP_LEVEL_REQUIRED = Set.of(
            "companySummary", "recentIssues", "industry", "competitors", "interviewPoints",
            "sources", "verifiedFacts", "aiInferences");
    private static final Set<String> FACT_FIELDS =
            Set.of("fact", "source", "evidence", "factId", "sourceKind", "sourceRef");
    private static final Set<String> INFERENCE_FIELDS =
            Set.of("inference", "basis", "inferenceId", "basedOn", "confidence");
    private static final Set<String> UNKNOWN_FIELDS = Set.of("topic", "reason", "neededSource");

    private final Map<String, Object> localSchema = localService().companyAnalysisSchema();
    private final Map<String, Object> openAiSchema =
            new OpenAiResponsesClient(new OpenAiProperties(), new ObjectMapper()).companyAnalysisSchema();

    @Test
    void topLevelFieldSetsMatchAcrossProviders() {
        assertThat(properties(localSchema).keySet()).isEqualTo(TOP_LEVEL_FIELDS);
        assertThat(properties(openAiSchema).keySet()).isEqualTo(TOP_LEVEL_FIELDS);
        assertThat(Set.copyOf(required(localSchema))).isEqualTo(LOCAL_TOP_LEVEL_REQUIRED);
        assertThat(Set.copyOf(required(openAiSchema))).isEqualTo(TOP_LEVEL_FIELDS);
    }

    @Test
    void localSchemaUsesArrayCapsWithoutChangingOpenAiStrictSchema() {
        assertThat(arraySchema(localSchema, "verifiedFacts").get("maxItems")).isEqualTo(8);
        assertThat(arraySchema(localSchema, "aiInferences").get("maxItems")).isEqualTo(4);
        assertThat(arraySchema(localSchema, "unknowns").get("maxItems")).isEqualTo(5);
        assertThat(arraySchema(openAiSchema, "verifiedFacts")).doesNotContainKey("maxItems");
        assertThat(arraySchema(openAiSchema, "aiInferences")).doesNotContainKey("maxItems");
        assertThat(arraySchema(openAiSchema, "unknowns")).doesNotContainKey("maxItems");
    }

    @Test
    void verifiedFactItemFieldSetsMatchAcrossProviders() {
        assertThat(itemProperties(localSchema, "verifiedFacts").keySet()).isEqualTo(FACT_FIELDS);
        assertThat(itemProperties(openAiSchema, "verifiedFacts").keySet()).isEqualTo(FACT_FIELDS);
        // 로컬/Claude 는 모델 실패율을 낮추기 위해 required 최소화 — 나머지는 canonicalizer 가 보정.
        assertThat(itemRequired(localSchema, "verifiedFacts"))
                .containsExactlyInAnyOrder("fact", "source", "evidence");
        // OpenAI strict 는 모든 properties 가 required — optional 키는 nullable 타입으로 표현.
        assertThat(Set.copyOf(itemRequired(openAiSchema, "verifiedFacts"))).isEqualTo(FACT_FIELDS);
        assertThat(itemProperties(openAiSchema, "verifiedFacts").get("factId"))
                .isEqualTo(Map.of("type", List.of("string", "null")));
    }

    @Test
    void aiInferenceItemFieldSetsMatchAcrossProviders() {
        assertThat(itemProperties(localSchema, "aiInferences").keySet()).isEqualTo(INFERENCE_FIELDS);
        assertThat(itemProperties(openAiSchema, "aiInferences").keySet()).isEqualTo(INFERENCE_FIELDS);
        assertThat(itemRequired(localSchema, "aiInferences")).containsExactlyInAnyOrder("inference", "basis");
        assertThat(Set.copyOf(itemRequired(openAiSchema, "aiInferences"))).isEqualTo(INFERENCE_FIELDS);
        // 배열형 optional(basedOn)도 OpenAI strict 가 허용하는 nullable 표현이어야 한다.
        assertThat(itemProperties(openAiSchema, "aiInferences").get("basedOn"))
                .isEqualTo(Map.of("type", List.of("array", "null"), "items", Map.of("type", "string")));
    }

    @Test
    void unknownsItemFieldSetsMatchAcrossProviders() {
        assertThat(itemProperties(localSchema, "unknowns").keySet()).isEqualTo(UNKNOWN_FIELDS);
        assertThat(itemProperties(openAiSchema, "unknowns").keySet()).isEqualTo(UNKNOWN_FIELDS);
        assertThat(itemRequired(localSchema, "unknowns")).containsExactlyInAnyOrder("topic", "reason");
    }

    @Test
    void sourcesAreTypeLabelObjectArraysOnBothProviders() {
        // OpenAI 의 기존 string[] sources 를 로컬/Claude 와 같은 {type,label} 객체 배열로 통일.
        for (Map<String, Object> schema : List.of(localSchema, openAiSchema)) {
            Map<String, Object> itemProperties = itemProperties(schema, "sources");
            assertThat(itemProperties.keySet()).containsExactlyInAnyOrder("type", "label");
        }
    }

    @Test
    void additionalPropertiesRemainClosedWithNewKeysDeclared() {
        // additionalProperties=false 유지 시 optional 보정 대상 키도 properties 에 명시돼야
        // 모델이 새 키를 출력할 수 있다(231 문서 5-2).
        for (Map<String, Object> schema : List.of(localSchema, openAiSchema)) {
            assertThat(schema.get("additionalProperties")).isEqualTo(false);
            assertThat(items(schema, "verifiedFacts").get("additionalProperties")).isEqualTo(false);
            assertThat(items(schema, "aiInferences").get("additionalProperties")).isEqualTo(false);
        }
    }

    // ── 헬퍼 ──

    private static BAnalysisGenerationService localService() {
        return new BAnalysisGenerationService(
                new BAnalysisProperties(),
                mock(BLocalLlmClient.class),
                new BJobSentenceClassifier(),
                new ObjectMapper(),
                mock(BAnthropicClient.class),
                mock(OpenAiResponsesClient.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> schema) {
        return (Map<String, Object>) schema.get("properties");
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(Map<String, Object> schema) {
        return (List<String>) schema.get("required");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> items(Map<String, Object> schema, String field) {
        Map<String, Object> array = (Map<String, Object>) properties(schema).get(field);
        return (Map<String, Object>) array.get("items");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> arraySchema(Map<String, Object> schema, String field) {
        return (Map<String, Object>) properties(schema).get(field);
    }

    private static Map<String, Object> itemProperties(Map<String, Object> schema, String field) {
        return properties(items(schema, field));
    }

    private static List<String> itemRequired(Map<String, Object> schema, String field) {
        return required(items(schema, field));
    }
}
