package com.careertuner.dashboard.ai;

import java.time.Duration;

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

    /**
     * 폴백 디스패처가 Claude tier 의 per-attempt 타임아웃({@code perAttemptTimeout})과 체인 데드라인
     * ({@code chainDeadlineNanos})을 주입하는 오버로드. 6-arg client.request(...) 로 그대로 넘겨 첫 시도는
     * 데드라인과 무관하게 보장하고 재시도만 유계화한다. 실패 시 예외를 던져 상위가 OpenAI 로 폴백한다.
     */
    public DashboardInsightAiResult summarize(DashboardInsightAiCommand command,
                                              Duration perAttemptTimeout,
                                              long chainDeadlineNanos) {
        StructuredResponse response = anthropicClient.request(
                DashboardInsightStructuredMapper.SCHEMA_NAME,
                mapper.schema(),
                DashboardInsightPromptCatalog.SYSTEM_PROMPT,
                mapper.userPrompt(command),
                perAttemptTimeout,
                chainDeadlineNanos);
        return mapper.toResult(response.payload(), response.usage());
    }
}
