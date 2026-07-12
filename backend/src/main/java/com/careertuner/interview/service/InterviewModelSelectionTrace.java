package com.careertuner.interview.service;

import org.springframework.stereotype.Component;

import com.careertuner.ai.common.model.RequestedAiModel;

/**
 * 요청 스코프 <b>면접 생성 모델 선택 홀더</b>. {@link InterviewOpenAiClient} 의 여러 생성 메서드가 단일 @Primary
 * {@link FallbackInterviewLlmGateway} 를 공유하므로, 생성 진입점(질문/모범답변/꼬리질문/리포트)이 요청 스레드에서
 * 이 값을 set 하고 게이트웨이가 읽어 tier 를 고른다. 생성 호출은 요청 스레드에서 동기 실행되므로 ThreadLocal 안전
 * (F 챗봇의 {@code ChatModelSelectionTrace} 와 동일 패턴). 진입점은 반드시 {@code finally} 로 {@link #clear()}.
 *
 * <p><b>채점(EVAL/Critic)에는 절대 적용하지 않는다</b> — 채점은 이 게이트웨이가 아니라 {@code InterviewEvaluatorProvider}
 * 가 처리하고, 채점 공정성을 위해 서버 고정 모델을 쓴다. 그래서 채점 엔드포인트는 이 값을 set 하지 않는다.
 */
@Component
public class InterviewModelSelectionTrace {

    private final ThreadLocal<RequestedAiModel> selection = new ThreadLocal<>();

    public void set(RequestedAiModel model) {
        selection.set(model == null ? RequestedAiModel.AUTO : model);
    }

    public RequestedAiModel current() {
        RequestedAiModel model = selection.get();
        return model == null ? RequestedAiModel.AUTO : model;
    }

    public void clear() {
        selection.remove();
    }
}
