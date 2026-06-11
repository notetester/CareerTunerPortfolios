package com.careertuner.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.interview.domain.InterviewAgentStep;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.rag.InterviewKnowledgeService;
import com.careertuner.interview.training.InterviewTrainingMapper;

import tools.jackson.databind.ObjectMapper;

/**
 * 자율 루프(동적 오케스트레이터)의 분기 동작 검증.
 * 평가기/지식베이스/매퍼는 mock 으로 두고, 정책이 상태에 따라 어떤 단계를 밟는지 trace 로 확인한다.
 */
class InterviewAgentOrchestratorTest {

    private final InterviewOpenAiClient aiClient = mock(InterviewOpenAiClient.class);
    private final InterviewAnswerEvaluator evaluator = mock(InterviewAnswerEvaluator.class);
    private final InterviewEvaluatorProvider evaluatorProvider = mock(InterviewEvaluatorProvider.class);
    private final InterviewAiUsageLogService usageLog = mock(InterviewAiUsageLogService.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final InterviewKnowledgeService knowledgeService = mock(InterviewKnowledgeService.class);
    private final InterviewTrainingMapper trainingMapper = mock(InterviewTrainingMapper.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final InterviewAgentProperties properties = new InterviewAgentProperties();

    private InterviewAgentOrchestrator orchestrator;
    private List<InterviewAgentStep> recorded;

    private static final InterviewOpenAiClient.Usage USAGE =
            new InterviewOpenAiClient.Usage("test-model", 0, 0, 0);

    @BeforeEach
    void setUp() {
        when(evaluatorProvider.get()).thenReturn(evaluator);
        when(knowledgeService.retrieveContext(any())).thenReturn(""); // RAG 비활성(근거 없음)
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        recorded = new ArrayList<>();
        doAnswer(inv -> {
            recorded.add(inv.getArgument(0));
            return null;
        }).when(interviewMapper).insertAgentStep(any());

        orchestrator = new InterviewAgentOrchestrator(aiClient, usageLog, interviewMapper,
                knowledgeService, trainingMapper, objectMapper, properties, evaluatorProvider);
    }

    @Test
    void 동의하면_평가와_검증만_거친다() {
        stubEval(80);
        stubCritic(82, "유지");

        InterviewAgentOrchestrator.OrchestratedEvaluation result =
                orchestrator.evaluateAnswer(1L, session(), applicationCase(), question(), longGoodAnswer());

        assertThat(result.score()).isEqualTo(82);
        assertThat(agents()).containsExactly("EVALUATOR", "CRITIC");
        verify(evaluator, times(1)).evaluateAnswer(any(), any(), any(), any());
    }

    @Test
    void 채점과_검증이_크게_어긋나면_재평가한다() {
        stubEval(80);
        stubCritic(50, "조정"); // |80-50| = 30 >= 20 → 재평가

        InterviewAgentOrchestrator.OrchestratedEvaluation result =
                orchestrator.evaluateAnswer(1L, session(), applicationCase(), question(), longGoodAnswer());

        // 재평가(80)와 Critic 조정값(50)의 중간 = 65
        assertThat(result.score()).isEqualTo(65);
        assertThat(agents()).containsExactly("EVALUATOR", "CRITIC", "EVALUATOR");
        verify(evaluator, times(2)).evaluateAnswer(any(), any(), any(), any());
    }

    @Test
    void 약한_답변이면_추가탐색을_권장한다() {
        stubEval(40);
        stubCritic(40, "유지"); // 동의(차 0), 최종 40점 < 50 → PROBE

        orchestrator.evaluateAnswer(1L, session(), applicationCase(), question(), "짧음");

        assertThat(agents()).contains("PROBER");
        assertThat(agents().get(agents().size() - 1)).isEqualTo("PROBER");
    }

    // ───── 헬퍼 ─────

    private void stubEval(int score) {
        when(evaluator.evaluateAnswer(any(), any(), any(), any()))
                .thenReturn(new InterviewOpenAiClient.AnswerEvaluation(score, "피드백", "개선답변", USAGE));
    }

    private void stubCritic(int adjusted, String verdict) {
        when(evaluator.critiqueEvaluation(any(), any(), anyInt(), any()))
                .thenReturn(new InterviewOpenAiClient.CritiqueResult(adjusted, verdict, "근거", USAGE));
    }

    private List<String> agents() {
        return recorded.stream().map(InterviewAgentStep::getAgent).toList();
    }

    private InterviewSession session() {
        return InterviewSession.builder().id(1L).applicationCaseId(2L).build();
    }

    private InterviewQuestion question() {
        return InterviewQuestion.builder().id(3L).question("자기소개를 해주세요.").build();
    }

    private ApplicationCase applicationCase() {
        return mock(ApplicationCase.class);
    }

    private String longGoodAnswer() {
        return "저는 3년차 백엔드 개발자로 결제 시스템을 설계하고 운영하며 장애 대응 경험을 쌓았습니다.";
    }
}
