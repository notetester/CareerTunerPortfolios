package com.careertuner.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.domain.ApplicationCase;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 외부 LLM(자체 모델·Claude·OpenAI)이 전부 미설정/실패했을 때, 면접 도메인이 목업으로 끝까지
 * 응답해 화면이 깨지지 않는지 검증한다. 실제 호출부({@link InterviewOpenAiClient})에 목업 게이트웨이만
 * 주입해, 빈 결과면 예외를 던지던 경로(질문·모범답안·꼬리질문)까지 안전하게 통과하는지 확인한다.
 */
class InterviewMockFallbackTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final MockInterviewLlmGateway mock = new MockInterviewLlmGateway(objectMapper);
    private final InterviewOpenAiClient client =
            new InterviewOpenAiClient(new InterviewModelProperties(), mock);

    private final ApplicationCase appCase = ApplicationCase.builder()
            .companyName("테스트회사").jobTitle("백엔드 개발자").build();

    @Test
    void 목업_게이트웨이는_항상_사용가능하다() {
        assertThat(mock.available()).isTrue();
    }

    @Test
    void 질문생성_예외없이_비어있지않은결과() {
        InterviewOpenAiClient.GeneratedQuestions result =
                client.generateQuestions(appCase, "공고 본문", "일반 면접", 3);
        assertThat(result.questions()).isNotEmpty();
        assertThat(result.questions().get(0).question()).isNotBlank();
        assertThat(result.usage().model()).isEqualTo("mock");
    }

    @Test
    void 모범답안생성_예외없이_비어있지않은결과() {
        InterviewOpenAiClient.ModelAnswer result =
                client.generateModelAnswer("자기소개 해주세요", appCase, "일반 면접");
        assertThat(result.modelAnswer()).isNotBlank();
    }

    @Test
    void 꼬리질문생성_예외없이_비어있지않은결과() {
        InterviewOpenAiClient.GeneratedQuestions result =
                client.generateFollowUps("원 질문", "지원자 답변", appCase, 2, false);
        assertThat(result.questions()).isNotEmpty();
    }

    @Test
    void 답변평가_예외없음() {
        assertThatNoException().isThrownBy(() ->
                client.evaluateAnswer("질문", "답변", appCase, null, null));
    }

    @Test
    void 리포트생성_예외없음() {
        assertThatNoException().isThrownBy(() -> client.generateReport("Q1: 질문\nA1: 답변"));
    }

    @Test
    void 플래너_가용액션중하나를_반환한다() {
        List<String> actions = List.of("ASK_FOLLOWUP", "FINISH");
        InterviewOpenAiClient.PlanDecisionResult result = client.planNextAction("상태 요약", actions);
        assertThat(actions).contains(result.action());
    }
}
