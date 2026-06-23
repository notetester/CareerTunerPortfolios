package com.careertuner.ai.autoprep;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * AI 오케스트레이터 두뇌. 한 줄 요청을 받아 슬롯(회사·직무·모드)과 실행할 파트를 결정한다.
 *
 * <p>파트 선택(동적 plan): LLM 이 요청을 보고 어떤 단계가 필요한지 고른다("통째로"면 전체,
 * "면접만"이면 INTERVIEW 만). 단계 간 의존(FIT·INTERVIEW ← JOB)은 코드가 클로저로 보강하고,
 * 실행 순서는 defaultSteps 기준으로 정렬한다. 슬롯 파싱은 {@link InterviewLlmGateway}(자체→Claude→OpenAI 폴백)를 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoPrepPlanner {

    private static final List<String> ALL_STEPS = PrepPlan.defaultSteps();

    // 파트 의존: FIT·INTERVIEW 는 JOB 결과가 있어야 의미가 있다 → 선택 시 JOB 을 함께 끌어온다.
    private static final Map<String, List<String>> DEPS = Map.of(
            "FIT", List.of("JOB"),
            "INTERVIEW", List.of("JOB"));

    private final InterviewLlmGateway llmGateway;
    private final ApplicationCaseService applicationCaseService;

    @Value("${careertuner.interview.model.generation:gpt-5.4-mini}")
    private String generationModel;

    private static final String INTENT_SYSTEM = """
        너는 취업 준비 요청을 분석하는 분류기다. 사용자의 한 줄 요청에서 다음을 추출해 JSON 으로만 답하라.
        - company: 지원 회사명(모르면 빈 문자열)
        - jobTitle: 직무(모르면 빈 문자열)
        - mode: 면접 모드. 정확히 BASIC, JOB, PERSONALITY, PRESSURE, RESUME, COMPANY 중 하나.
          압박/꼬리/반박 → PRESSURE, 인성/가치관/협업 → PERSONALITY, 자소서 → RESUME, 기업/컬처 → COMPANY,
          기술/직무/개발 → JOB, 그 외 → BASIC.
        - parts: 이 요청에 필요한 준비 단계 배열. 후보:
          PROFILE(프로필·역량), JOB(공고분석), FIT(적합도), WRITE(자소서교정), INTERVIEW(면접질문), COMMUNITY(커뮤니티).
          규칙: "통째로/전체/다/싹/전부" 또는 모호하면 빈 배열([]) — 전체로 처리한다.
          특정 단계만 콕 집으면("면접만","자소서만","적합도만") 그 단계만 넣는다.
        """;

    public PrepPlan plan(Long userId, AutoPrepRequest request) {
        ParsedIntent parsed = parseIntent(request.query());
        String mode = firstNonBlank(request.mode(), parsed.mode(), "BASIC");
        // 지원 건이 확정되면 그 건의 실제 회사/직무로 보정한다(query 파싱값보다 정확).
        ApplicationCaseResponse matched = resolveCase(userId, parsed.company(), request.applicationCaseId());
        Long caseId = matched != null ? matched.id() : null;
        String company = matched != null ? matched.companyName() : parsed.company();
        String jobTitle = matched != null ? matched.jobTitle() : parsed.jobTitle();
        PrepSlots slots = new PrepSlots(company, jobTitle, mode, caseId);
        List<String> steps = resolveSteps(parsed.parts());
        String intent = steps.size() == ALL_STEPS.size() ? "FULL_PREP" : "CUSTOM_PREP";
        return new PrepPlan(intent, slots, steps);
    }

    private ParsedIntent parseIntent(String query) {
        if (query == null || query.isBlank()) {
            return new ParsedIntent(null, null, null, List.of());
        }
        try {
            InterviewLlmGateway.Result result = llmGateway.complete(new InterviewLlmGateway.Request(
                    "autoprep_intent", intentSchema(), INTENT_SYSTEM, query, generationModel));
            JsonNode p = result.payload();
            return new ParsedIntent(text(p, "company"), text(p, "jobTitle"), text(p, "mode"), stringList(p, "parts"));
        } catch (RuntimeException ex) {
            log.warn("AutoPrep 의도 파싱 실패 → 빈 슬롯·전체 파트로 진행: {}", ex.getMessage());
            return new ParsedIntent(null, null, null, List.of());
        }
    }

    /** 선택 파트 + 의존 클로저를 defaultSteps 순서로 정렬. 비었으면 전체. */
    private List<String> resolveSteps(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return ALL_STEPS;
        }
        Set<String> selected = new LinkedHashSet<>();
        for (String raw : requested) {
            if (raw == null) {
                continue;
            }
            String part = raw.trim().toUpperCase();
            if (ALL_STEPS.contains(part)) {
                addWithDeps(part, selected);
            }
        }
        if (selected.isEmpty()) {
            return ALL_STEPS;
        }
        return ALL_STEPS.stream().filter(selected::contains).toList();
    }

    private void addWithDeps(String part, Set<String> acc) {
        for (String dep : DEPS.getOrDefault(part, List.of())) {
            addWithDeps(dep, acc);
        }
        acc.add(part);
    }

    /** 지원 건 결정: 명시 caseId 우선 → 회사명 매칭 → 회사 모호 시 최근 건 → 없으면 null. */
    private ApplicationCaseResponse resolveCase(Long userId, String company, Long explicit) {
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
        if (explicit != null) {
            return cases.stream().filter(c -> explicit.equals(c.id())).findFirst().orElse(null);
        }
        if (company != null && !company.isBlank()) {
            String key = company.trim();
            for (ApplicationCaseResponse c : cases) {
                if (c.companyName() != null && c.companyName().contains(key)) {
                    return c;
                }
            }
            // 회사를 콕 집었는데 매칭 실패 → 최근 건으로 엉뚱하게 폴백하지 말고 null(되묻기 유도).
            return null;
        }
        // 회사가 모호하면 가장 최근 지원 건을 기본값으로.
        return cases.get(0);
    }

    private Map<String, Object> intentSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "company", Map.of("type", "string"),
                        "jobTitle", Map.of("type", "string"),
                        "mode", Map.of("type", "string"),
                        "parts", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string", "enum", ALL_STEPS))),
                "required", List.of("company", "jobTitle", "mode", "parts"),
                "additionalProperties", false);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static List<String> stringList(JsonNode node, String field) {
        JsonNode arr = node == null ? null : node.get(field);
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        arr.forEach(n -> {
            if (n != null && !n.isNull()) {
                out.add(n.asText());
            }
        });
        return out;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private record ParsedIntent(String company, String jobTitle, String mode, List<String> parts) {
    }
}
