package com.careertuner.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.domain.ApplicationCase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class InterviewFitContextPromptTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final List<InterviewLlmGateway.Request> requests = new ArrayList<>();
    private final InterviewOpenAiClient client = new InterviewOpenAiClient(
            new InterviewModelProperties(), request -> {
                requests.add(request);
                return new InterviewLlmGateway.Result(payload(request.schemaName()),
                        new InterviewOpenAiClient.Usage("test", 1, 1, 2));
            });
    private final ApplicationCase applicationCase = ApplicationCase.builder()
            .companyName("테스트회사").jobTitle("백엔드 개발자").build();

    @Test
    void questionEvaluationAndReportPromptsConsumeTheSameFitSnapshot() {
        String context = "[질문 생성 시점 적합도 분석 스냅샷]\n분석 ID: 77\n부족 역량: [AWS]";

        client.generateQuestions(applicationCase, "공고", "직무 면접", 1, context);
        client.evaluateAnswer("질문", "답변", applicationCase, context, null);
        client.generateReport("Q1. 질문\nA1. 답변", context);

        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).userPrompt()).contains("분석 ID: 77", "부족 역량: [AWS]");
        assertThat(requests.get(1).userPrompt()).contains("분석 ID: 77", "부족 역량: [AWS]");
        assertThat(requests.get(2).userPrompt()).contains("분석 ID: 77", "부족 역량: [AWS]");
    }

    private JsonNode payload(String schemaName) {
        String json = switch (schemaName) {
            case "interview_questions" -> "{\"questions\":[{\"question\":\"질문\",\"type\":\"TECH\"}]}";
            case "interview_answer_evaluation" -> "{\"score\":80,\"feedback\":\"좋음\",\"improvedAnswer\":\"\"}";
            case "interview_report" -> "{\"totalScore\":80,\"categories\":[],\"summaryFeedback\":[]}";
            default -> "{}";
        };
        return objectMapper.readTree(json);
    }
}
