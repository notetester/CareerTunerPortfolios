package com.careertuner.interview.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 설정({@code careertuner.interview.eval.provider})에 따라 답변 평가기를 선택한다.
 * provider=oss 이고 자체 평가기 빈이 있으면 자체 모델을, 그 외에는 OpenAI 를 쓴다.
 * 호출부(오케스트레이터)는 이 Provider 만 보고, 구체 구현은 신경 쓰지 않는다.
 */
@Component
public class InterviewEvaluatorProvider {

    private final InterviewAnswerEvaluator evaluator;
    private final String providerName;

    public InterviewEvaluatorProvider(InterviewEvalProperties properties,
                                      InterviewOpenAiClient openAiClient,
                                      ObjectProvider<OssAnswerEvaluator> ossProvider) {
        OssAnswerEvaluator oss = ossProvider.getIfAvailable();
        if (properties.isOss() && oss != null) {
            this.evaluator = oss;
            this.providerName = "oss";
        } else {
            this.evaluator = openAiClient;
            this.providerName = "openai";
        }
    }

    public InterviewAnswerEvaluator get() {
        return evaluator;
    }

    public String providerName() {
        return providerName;
    }
}
