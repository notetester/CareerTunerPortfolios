package com.careertuner.ai.chat.llm;

import org.springframework.stereotype.Component;

import com.careertuner.ai.common.model.RequestedAiModel;

/**
 * 요청 스코프 <b>챗봇 모델 선택 홀더</b>. LangChain4j 의 @AiService(CommunityChatAgent·IntakeChatAgent·
 * QuickReplyAgent·SummaryAgent)는 생성 시점에 단일 @Primary {@link FallbackChatModel} 을 바인딩하므로 메서드
 * 인자로 모델 tier 를 실을 seam 이 없다. 그래서 챗봇 진입점(ask/summarize)이 요청 스레드에서 이 값을 set 하고
 * {@link FallbackChatModel#chat} 이 읽어 tier 를 고른다. 에이전트 호출은 요청 스레드에서 동기 실행되므로
 * ThreadLocal 로 안전하며(이 코드베이스의 {@code SearchTrace} 와 동일 패턴), 진입점은 반드시 {@code finally}
 * 로 {@link #clear()} 한다(스레드 재사용 시 다음 요청 누수 방지). 값이 없으면 AUTO(현행 폴백).
 */
@Component
public class ChatModelSelectionTrace {

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
