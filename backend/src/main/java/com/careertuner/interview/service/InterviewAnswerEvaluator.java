package com.careertuner.interview.service;

import com.careertuner.applicationcase.domain.ApplicationCase;

/**
 * 면접 답변 평가기. 평가(채점)와 검증(Critic) 을 제공한다.
 *
 * <p>구현을 갈아끼워 평가 모델을 바꾼다.
 * <ul>
 *   <li>{@link InterviewOpenAiClient} — OpenAI(기본/폴백)</li>
 *   <li>{@link OssAnswerEvaluator} — 자체 파인튜닝 모델(vLLM/TGI), {@code eval.provider=oss}</li>
 * </ul>
 * 반환 레코드는 {@link InterviewOpenAiClient} 의 것을 공유해 호출부(오케스트레이터) 변경 없이 교체된다.
 */
public interface InterviewAnswerEvaluator {

    /**
     * 답변을 채점한다.
     *
     * @param referenceModelAnswer 사용자에게 보여준 모범답안(답안지). 있으면 만점 기준으로 삼는다. 없으면 빈/널.
     */
    InterviewOpenAiClient.AnswerEvaluation evaluateAnswer(String question, String answerText,
                                                          ApplicationCase applicationCase, String ragContext,
                                                          String referenceModelAnswer);

    InterviewOpenAiClient.CritiqueResult critiqueEvaluation(String question, String answerText,
                                                            int originalScore, String feedback,
                                                            String referenceModelAnswer);
}
