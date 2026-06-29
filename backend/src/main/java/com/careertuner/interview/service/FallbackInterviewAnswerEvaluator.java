package com.careertuner.interview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.careertuner.applicationcase.domain.ApplicationCase;

/**
 * 면접 채점 폴백 래퍼: 자체모델(OSS) → Claude(Haiku)/OpenAI/목업.
 *
 * <p>{@code provider=oss} 인데 자체 평가모델이 죽거나 미서빙(예: 4090 미접근)일 때, 기존에는
 * {@link OssAnswerEvaluator} 가 던진 예외가 그대로 전파돼 면접 채점이 통째로 실패했다(터짐).
 * 이 래퍼는 자체모델 채점 실패 시 {@link InterviewOpenAiClient}(@Primary {@link FallbackInterviewLlmGateway}
 * 경유 → Claude→OpenAI→목업)로 폴백해, 어떤 환경에서도 채점이 끊기지 않게 한다.
 *
 * <p>{@code provider=openai} 환경은 {@link InterviewEvaluatorProvider} 가 이 래퍼 대신 {@link InterviewOpenAiClient}
 * 를 바로 쓰므로(자체모델 단계 자체가 없음) 이 클래스를 거치지 않는다.
 */
class FallbackInterviewAnswerEvaluator implements InterviewAnswerEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FallbackInterviewAnswerEvaluator.class);

    private final OssAnswerEvaluator oss;
    private final InterviewOpenAiClient openAiClient;
    private final InterviewEvalProperties evalProperties;

    FallbackInterviewAnswerEvaluator(OssAnswerEvaluator oss, InterviewOpenAiClient openAiClient,
                                     InterviewEvalProperties evalProperties) {
        this.oss = oss;
        this.openAiClient = openAiClient;
        this.evalProperties = evalProperties;
    }

    @Override
    public InterviewOpenAiClient.AnswerEvaluation evaluateAnswer(String question, String answerText,
                                                                 ApplicationCase applicationCase, String ragContext,
                                                                 String referenceModelAnswer) {
        if (evalProperties.configured()) {
            try {
                return oss.evaluateAnswer(question, answerText, applicationCase, ragContext, referenceModelAnswer);
            } catch (RuntimeException ex) {
                log.warn("자체 채점(OSS) 실패 → Claude/OpenAI 폴백: {}", ex.getMessage());
            }
        }
        return openAiClient.evaluateAnswer(question, answerText, applicationCase, ragContext, referenceModelAnswer);
    }

    @Override
    public InterviewOpenAiClient.CritiqueResult critiqueEvaluation(String question, String answerText,
                                                                   int originalScore, String feedback,
                                                                   String referenceModelAnswer) {
        if (evalProperties.configured()) {
            try {
                return oss.critiqueEvaluation(question, answerText, originalScore, feedback, referenceModelAnswer);
            } catch (RuntimeException ex) {
                log.warn("자체 Critic(OSS) 실패 → Claude/OpenAI 폴백: {}", ex.getMessage());
            }
        }
        return openAiClient.critiqueEvaluation(question, answerText, originalScore, feedback, referenceModelAnswer);
    }
}
