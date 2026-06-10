package com.careertuner.interview.training;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.interview.ai.prompt.InterviewPromptCatalog;
import com.careertuner.interview.domain.InterviewTrainingSample;
import com.careertuner.interview.service.InterviewOpenAiClient;
import com.careertuner.interview.training.dto.EvalHarnessResponse;
import com.careertuner.interview.training.dto.FineTuneResponse;
import com.careertuner.interview.training.dto.TrainingStatsResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 면접 평가 학습 데이터 파이프라인.
 * - 통계 / JSONL 추출 / 평가 하니스(LLM-as-judge) / gpt-4o-mini 파인튜닝 트리거.
 */
@Service
public class InterviewTrainingService {

    private static final int MIN_SAMPLES_FOR_FT = 10;   // OpenAI 파인튜닝 최소 예시 수
    private static final String DEFAULT_BASE_MODEL = "gpt-4o-mini-2024-07-18";
    private static final int AGREEMENT_THRESHOLD = 10;  // |저장점수 - 재채점| <= 10 이면 일치로 간주

    private final InterviewTrainingMapper mapper;
    private final InterviewOpenAiClient aiClient;
    private final FineTuneClient fineTuneClient;
    private final ObjectMapper objectMapper;

    public InterviewTrainingService(InterviewTrainingMapper mapper, InterviewOpenAiClient aiClient,
                                    FineTuneClient fineTuneClient, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.aiClient = aiClient;
        this.fineTuneClient = fineTuneClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TrainingStatsResponse stats(AuthUser authUser) {
        requireAdmin(authUser);
        return new TrainingStatsResponse(mapper.count(), mapper.averageScore());
    }

    /** 저장된 샘플을 OpenAI 챗 파인튜닝 JSONL 포맷으로 추출한다. */
    @Transactional(readOnly = true)
    public String exportJsonl(AuthUser authUser, int limit) {
        requireAdmin(authUser);
        List<InterviewTrainingSample> samples = mapper.findAll(normalizeLimit(limit));
        StringBuilder sb = new StringBuilder();
        for (InterviewTrainingSample s : samples) {
            sb.append(toJsonlLine(s)).append("\n");
        }
        return sb.toString();
    }

    /** LLM-as-judge 로 재채점해 저장 점수와의 일치도를 측정한다. */
    @Transactional(readOnly = true)
    public EvalHarnessResponse runEvalHarness(AuthUser authUser, int sampleSize) {
        requireAdmin(authUser);
        List<InterviewTrainingSample> samples = mapper.findAll(normalizeSampleSize(sampleSize));
        int evaluated = 0;
        int agree = 0;
        long sumAbsDiff = 0;
        for (InterviewTrainingSample s : samples) {
            int judged = aiClient.judgeAnswerScore(s.getQuestion(), s.getAnswerText()).score();
            int diff = Math.abs((s.getScore() == null ? 0 : s.getScore()) - judged);
            sumAbsDiff += diff;
            if (diff <= AGREEMENT_THRESHOLD) {
                agree++;
            }
            evaluated++;
        }
        double meanAbsDiff = evaluated == 0 ? 0 : (double) sumAbsDiff / evaluated;
        double agreementRate = evaluated == 0 ? 0 : (double) agree / evaluated;
        return new EvalHarnessResponse(evaluated, round2(meanAbsDiff), round2(agreementRate));
    }

    /** 전체 샘플로 JSONL 을 만들어 OpenAI 파인튜닝 잡을 생성한다. */
    @Transactional(readOnly = true)
    public FineTuneResponse startFineTune(AuthUser authUser, String baseModel) {
        requireAdmin(authUser);
        long count = mapper.count();
        if (count < MIN_SAMPLES_FOR_FT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "파인튜닝에는 최소 " + MIN_SAMPLES_FOR_FT + "개 샘플이 필요합니다. (현재 " + count + "개)");
        }
        String model = baseModel == null || baseModel.isBlank() ? DEFAULT_BASE_MODEL : baseModel.trim();
        String jsonl = exportAll();
        FineTuneClient.Result result = fineTuneClient.startFineTune(jsonl, model);
        return new FineTuneResponse((int) count, model, result.fileId(), result.jobId(), result.status());
    }

    // ───── 내부 ─────

    private String exportAll() {
        List<InterviewTrainingSample> samples = mapper.findAll(5000);
        StringBuilder sb = new StringBuilder();
        for (InterviewTrainingSample s : samples) {
            sb.append(toJsonlLine(s)).append("\n");
        }
        return sb.toString();
    }

    private String toJsonlLine(InterviewTrainingSample s) {
        String user = "질문:\n" + s.getQuestion() + "\n\n지원자 답변:\n" + s.getAnswerText();
        String assistant = assistantContent(s);
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("messages", List.of(
                    java.util.Map.of("role", "system", "content", InterviewPromptCatalog.EVALUATION_SYSTEM_PROMPT),
                    java.util.Map.of("role", "user", "content", user),
                    java.util.Map.of("role", "assistant", "content", assistant))));
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "학습 데이터를 직렬화하지 못했습니다.");
        }
    }

    private String assistantContent(InterviewTrainingSample s) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "score", s.getScore() == null ? 0 : s.getScore(),
                    "feedback", s.getFeedback() == null ? "" : s.getFeedback(),
                    "improvedAnswer", ""));
        } catch (JacksonException ex) {
            return "{\"score\":0,\"feedback\":\"\",\"improvedAnswer\":\"\"}";
        }
    }

    private int normalizeLimit(int limit) {
        return limit <= 0 ? 1000 : Math.min(limit, 5000);
    }

    private int normalizeSampleSize(int sampleSize) {
        return sampleSize <= 0 ? 20 : Math.min(sampleSize, 100);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
