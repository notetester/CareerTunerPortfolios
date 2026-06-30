package com.careertuner.dashboard.ai;

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
            DashboardInsightAiResult fallback = mockService.summarize(command);
            return new DashboardInsightAiResult(
                    fallback.summary(),
                    new CareerAnalysisAiUsage("mock-fallback", 0, 0, 0, true),
                    "FALLBACK",
                    exception.getMessage(),
                    true);
        }
    }
}
