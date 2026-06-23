package com.careertuner.ai.autoprep;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.interview.service.InterviewLlmGateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * AI 오케스트레이터 두뇌. 한 줄 요청을 받아 슬롯(회사·직무·모드)을 파싱하고 실행 계획(PrepPlan)을 만든다.
 *
 * <p>슬롯 파싱은 {@link InterviewLlmGateway}(자체 LLM → Claude Haiku → OpenAI 폴백)를 재활용한다.
 * 자체 모델 미학습/실패 시에도 Claude 폴백으로 동작하므로 사용에 문제없다. 폴백조차 실패하면 빈 슬롯으로
 * 진행하고 오케가 단계별로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoPrepPlanner {

    private final InterviewLlmGateway llmGateway;
    private final ApplicationCaseService applicationCaseService;

    @Value("${careertuner.interview.model.generation:gpt-5.4-mini}")
    private String generationModel;

    private static final String INTENT_SYSTEM = """
        너는 취업 준비 요청을 분석하는 분류기다. 사용자의 한 줄 요청에서 지원 회사명(company), 직무(jobTitle),
        면접 모드(mode)를 추출해 JSON 으로만 답하라. 모르면 빈 문자열로 둔다.
        mode 는 정확히 다음 중 하나: BASIC, JOB, PERSONALITY, PRESSURE, RESUME, COMPANY.
        규칙: 압박/꼬리/반박/몰아 → PRESSURE, 인성/가치관/협업/갈등 → PERSONALITY, 자소서/자기소개서 → RESUME,
        기업/컬처/회사맞춤 → COMPANY, 기술/직무/개발/백엔드/프론트 → JOB, 그 외 → BASIC.
        """;

    public PrepPlan plan(Long userId, AutoPrepRequest request) {
        PrepSlots parsed = parseSlots(request.query());
        String mode = firstNonBlank(request.mode(), parsed.mode(), "BASIC");
        Long caseId = resolveCaseId(userId, parsed, request.applicationCaseId());
        PrepSlots slots = new PrepSlots(parsed.company(), parsed.jobTitle(), mode, caseId);
        return new PrepPlan("FULL_PREP", slots, PrepPlan.defaultSteps());
    }

    private PrepSlots parseSlots(String query) {
        if (query == null || query.isBlank()) {
            return new PrepSlots(null, null, null, null);
        }
        try {
            InterviewLlmGateway.Result result = llmGateway.complete(new InterviewLlmGateway.Request(
                    "autoprep_intent", intentSchema(), INTENT_SYSTEM, query, generationModel));
            JsonNode p = result.payload();
            return new PrepSlots(text(p, "company"), text(p, "jobTitle"), text(p, "mode"), null);
        } catch (RuntimeException ex) {
            log.warn("AutoPrep 슬롯 파싱 실패 → 빈 슬롯으로 진행: {}", ex.getMessage());
            return new PrepSlots(null, null, null, null);
        }
    }

    /**
     * 지원 건 결정: 명시 caseId 우선 → 슬롯의 회사명으로 사용자 지원 건 매칭 → 가장 최근 건 → 없으면 null.
     */
    private Long resolveCaseId(Long userId, PrepSlots parsed, Long explicit) {
        if (explicit != null) {
            return explicit;
        }
        List<ApplicationCaseResponse> cases;
        try {
            cases = applicationCaseService.list(userId, null, false);
        } catch (RuntimeException ex) {
            log.warn("AutoPrep 지원 건 목록 조회 실패: {}", ex.getMessage());
            return null;
        }
        if (cases == null || cases.isEmpty()) {
            return null;
        }
        String company = parsed.company();
        if (company != null && !company.isBlank()) {
            String key = company.trim();
            for (ApplicationCaseResponse c : cases) {
                if (c.companyName() != null && c.companyName().contains(key)) {
                    return c.id();
                }
            }
        }
        return cases.get(0).id();
    }

    private Map<String, Object> intentSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "company", Map.of("type", "string"),
                        "jobTitle", Map.of("type", "string"),
                        "mode", Map.of("type", "string")),
                "required", List.of("company", "jobTitle", "mode"),
                "additionalProperties", false);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
