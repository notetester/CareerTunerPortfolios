package com.careertuner.interview.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 면접 LLM 최종 폴백(목업) 게이트웨이.
 *
 * <p>자체 모델·Claude·OpenAI 가 모두 미설정이거나 실패했을 때 {@link FallbackInterviewLlmGateway} 가
 * 마지막으로 호출한다. 외부 호출 없이 요청의 JSON 스키마({@link Request#jsonSchema()})를 그대로 채워
 * "형식상 유효하고 비어 있지 않은" 응답을 만들어, 어떤 상황에서도 면접 화면이 깨지지 않게 한다.
 *
 * <p>실제 평가가 아니라 시연용 임시 결과다(점수·피드백은 고정 더미). 그래서 항상 {@link #available()}
 * 가 true 이며, 사용량은 0 토큰·model "mock" 으로 기록해 과금/통계와 구분한다.
 *
 * <p>스키마를 재귀적으로 해석하므로 면접 도메인에 새 task(schemaName)가 추가돼도 별도 수정 없이
 * 안전망이 따라간다. enum(예: agent_plan 의 action, critique 의 verdict)은 첫 후보를 골라 제약을 지킨다.
 */
@Component
public class MockInterviewLlmGateway implements InterviewLlmGateway {

    /** 배열형 응답에 채울 더미 아이템 개수(화면이 비지 않을 최소 수). */
    private static final int ARRAY_ITEMS = 2;
    private static final int DUMMY_SCORE = 75;

    private final ObjectMapper objectMapper;

    public MockInterviewLlmGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 최종 안전망이므로 항상 사용 가능하다. */
    public boolean available() {
        return true;
    }

    @Override
    public Result complete(Request request) {
        Object payload = generate(request.jsonSchema(), "", 0);
        try {
            JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(payload));
            return new Result(node, new InterviewOpenAiClient.Usage("mock", 0, 0, 0));
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "목업 응답 생성에 실패했습니다.");
        }
    }

    /**
     * JSON 스키마 노드를 더미 값으로 재귀 변환한다.
     *
     * @param key   부모가 이 값에 붙인 필드명(문구·점수 휴리스틱에 사용)
     * @param index 배열 내 순번(0-base) — number 필드와 문구 구분에 사용
     */
    @SuppressWarnings("unchecked")
    private Object generate(Object schemaObj, String key, int index) {
        if (!(schemaObj instanceof Map<?, ?> schema)) {
            return textFor(key, index);
        }
        Object typeNode = schema.get("type");
        String type = typeNode == null ? "string" : String.valueOf(typeNode);
        switch (type) {
            case "object" -> {
                Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
                Map<String, Object> result = new LinkedHashMap<>();
                if (properties != null) {
                    properties.forEach((name, sub) -> result.put(name, generate(sub, name, index)));
                }
                return result;
            }
            case "array" -> {
                Object items = schema.get("items");
                List<Object> result = new ArrayList<>();
                for (int i = 0; i < ARRAY_ITEMS; i++) {
                    result.add(generate(items, key, i));
                }
                return result;
            }
            case "integer", "number" -> {
                return intFor(key, index);
            }
            case "boolean" -> {
                return Boolean.TRUE;
            }
            default -> {
                Object enumValues = schema.get("enum");
                if (enumValues instanceof List<?> list && !list.isEmpty()) {
                    return String.valueOf(list.get(0));
                }
                return textFor(key, index);
            }
        }
    }

    private String textFor(String key, int index) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        int n = index + 1;
        if (k.contains("question")) {
            return "면접 데모용 예시 질문 " + n + " (외부 AI 미연결 상태의 임시 결과입니다).";
        }
        if (k.equals("answer")) {
            // 음성 트랜스크립트의 지원자 답변 칸 — 비워 두는 것이 자연스럽다.
            return "";
        }
        if (k.contains("answer")) {
            return "데모 목업 모범답안입니다. 외부 AI 미연결 상태라 실제 생성 결과가 아닙니다.";
        }
        if (k.contains("feedback") || k.contains("summary")) {
            return "데모 목업 피드백입니다. 외부 AI 미연결 상태의 임시 결과예요.";
        }
        if (k.contains("reason")) {
            return "외부 AI 미연결 상태라 원 판정을 그대로 유지했습니다(목업).";
        }
        if (k.contains("label")) {
            return "종합";
        }
        return "데모 목업 응답입니다.";
    }

    private int intFor(String key, int index) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (k.contains("score")) {
            return DUMMY_SCORE;
        }
        if (k.contains("number")) {
            return index + 1;
        }
        return 0;
    }
}
