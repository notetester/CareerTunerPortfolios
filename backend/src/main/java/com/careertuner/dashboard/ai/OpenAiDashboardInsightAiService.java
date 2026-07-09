package com.careertuner.dashboard.ai;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.dashboard.ai.prompt.DashboardInsightPromptCatalog;

/**
 * 대시보드 요약의 OpenAI 단계. 키가 있으면 실제 요약을, 없거나 실패하면 결정적 mock 으로 폴백한다.
 *
 * <p>활성 진입점(@Primary)은 {@link FallbackDashboardInsightAiService}(Claude→OpenAI)다. 이 서비스는 그
 * 폴백 체인의 OpenAI 단계이며, 내부 mock 폴백이 최종 안전망이다. 스키마·파싱은
 * {@link DashboardInsightStructuredMapper} 를 Claude 단계와 공유한다.
 */
@Service
public class OpenAiDashboardInsightAiService implements DashboardInsightAiService {

    private final CareerAnalysisOpenAiClient openAiClient;
    private final MockDashboardInsightAiService mockService;
    private final DashboardInsightStructuredMapper mapper;

    public OpenAiDashboardInsightAiService(CareerAnalysisOpenAiClient openAiClient,
                                           MockDashboardInsightAiService mockService,
                                           DashboardInsightStructuredMapper mapper) {
        this.openAiClient = openAiClient;
        this.mockService = mockService;
        this.mapper = mapper;
    }

    @Override
    public DashboardInsightAiResult summarize(DashboardInsightAiCommand command) {
        if (!openAiClient.configured()) {
            return mockService.summarize(command);
        }
        try {
            StructuredResponse response = openAiClient.request(
                    DashboardInsightStructuredMapper.SCHEMA_NAME,
                    mapper.schema(),
                    DashboardInsightPromptCatalog.SYSTEM_PROMPT,
                    mapper.userPrompt(command));
            return mapper.toResult(response.payload(), response.usage());
        } catch (RuntimeException exception) {
            return mockFallback(command, exception);
        }
    }

    /**
     * 폴백 디스패처가 OpenAI tier 의 per-attempt 타임아웃({@code perAttemptTimeout})과 체인 데드라인
     * ({@code chainDeadlineNanos})을 주입하는 오버로드. 6-arg client.request(...) 로 그대로 넘긴다.
     *
     * <p>내부 Mock 폴백(키 없음/RuntimeException 시 {@link MockDashboardInsightAiService} 결과 · FALLBACK)이
     * 최종 안전망이며, 이 오버로드도 절대 예외를 던지지 않는다.
     */
    public DashboardInsightAiResult summarize(DashboardInsightAiCommand command,
                                              Duration perAttemptTimeout,
                                              long chainDeadlineNanos) {
        if (!openAiClient.configured()) {
            return mockService.summarize(command);
        }
        try {
            StructuredResponse response = openAiClient.request(
                    DashboardInsightStructuredMapper.SCHEMA_NAME,
                    mapper.schema(),
                    DashboardInsightPromptCatalog.SYSTEM_PROMPT,
                    mapper.userPrompt(command),
                    perAttemptTimeout,
                    chainDeadlineNanos);
            return mapper.toResult(response.payload(), response.usage());
        } catch (RuntimeException exception) {
            return mockFallback(command, exception);
        }
    }

    private DashboardInsightAiResult mockFallback(DashboardInsightAiCommand command, RuntimeException exception) {
        DashboardInsightAiResult fallback = mockService.summarize(command);
        return new DashboardInsightAiResult(
                fallback.summary(),
                new CareerAnalysisAiUsage("mock-fallback", 0, 0, 0, true),
                "FALLBACK",
                exception.getMessage(),
                true);
    }
}
