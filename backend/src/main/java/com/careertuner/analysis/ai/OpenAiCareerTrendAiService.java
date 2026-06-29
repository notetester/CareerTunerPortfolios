package com.careertuner.analysis.ai;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.prompt.CareerTrendPromptCatalog;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;

/**
 * 커리어 트렌드의 OpenAI 단계. 키가 있으면 실제 분석을, 없거나 실패하면 결정적 mock 으로 폴백한다.
 *
 * <p>활성 진입점(@Primary)은 {@link FallbackCareerTrendAiService}(Claude→OpenAI)다. 이 서비스는 그 폴백
 * 체인의 OpenAI 단계이며, 내부 mock 폴백이 최종 안전망이다. 스키마·파싱은 {@link CareerTrendStructuredMapper}
 * 를 Claude 단계와 공유한다.
 */
@Service
public class OpenAiCareerTrendAiService implements CareerTrendAiService {

    private final CareerAnalysisOpenAiClient openAiClient;
    private final MockCareerTrendAiService mockService;
    private final CareerTrendStructuredMapper mapper;

    public OpenAiCareerTrendAiService(CareerAnalysisOpenAiClient openAiClient,
                                      MockCareerTrendAiService mockService,
                                      CareerTrendStructuredMapper mapper) {
        this.openAiClient = openAiClient;
        this.mockService = mockService;
        this.mapper = mapper;
    }

    @Override
    public CareerTrendAiResult generate(CareerTrendAiCommand command) {
        if (!openAiClient.configured()) {
            return mockService.generate(command);
        }
        try {
            StructuredResponse response = openAiClient.request(
                    CareerTrendStructuredMapper.SCHEMA_NAME,
                    mapper.schema(),
                    CareerTrendPromptCatalog.SYSTEM_PROMPT,
                    mapper.userPrompt(command));
            return mapper.toResult(response.payload(), response.usage());
        } catch (RuntimeException exception) {
            CareerTrendAiResult fallback = mockService.generate(command);
            return new CareerTrendAiResult(
                    fallback.trendSummary(),
                    fallback.recommendedDirections(),
                    new CareerAnalysisAiUsage("mock-fallback", 0, 0, 0, true),
                    "FALLBACK",
                    exception.getMessage(),
                    true);
        }
    }
}
