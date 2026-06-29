package com.careertuner.dashboard.ai;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAnthropicClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.dashboard.ai.prompt.DashboardInsightPromptCatalog;

/**
 * 대시보드 요약의 Claude(Haiku) 단계 — 폴백 디스패처의 1차 폴백 provider.
 *
 * <p>{@link OpenAiDashboardInsightAiService} 와 같은 스키마·파싱({@link DashboardInsightStructuredMapper})을
 * 쓰되 전송만 {@link CareerAnalysisAnthropicClient} 로 바꾼 형태다. 실패 시 예외를 던지고 상위
 * {@link FallbackDashboardInsightAiService} 가 OpenAI 단계로 폴백한다(자체 mock 폴백 없음).
 */
@Service
public class AnthropicDashboardInsightAiService implements DashboardInsightAiService {

    private final CareerAnalysisAnthropicClient anthropicClient;
    private final DashboardInsightStructuredMapper mapper;

    public AnthropicDashboardInsightAiService(CareerAnalysisAnthropicClient anthropicClient,
                                              DashboardInsightStructuredMapper mapper) {
        this.anthropicClient = anthropicClient;
        this.mapper = mapper;
    }

    public boolean configured() {
        return anthropicClient.configured();
    }

    @Override
    public DashboardInsightAiResult summarize(DashboardInsightAiCommand command) {
        StructuredResponse response = anthropicClient.request(
                DashboardInsightStructuredMapper.SCHEMA_NAME,
                mapper.schema(),
                DashboardInsightPromptCatalog.SYSTEM_PROMPT,
                mapper.userPrompt(command));
        return mapper.toResult(response.payload(), response.usage());
    }
}
