package com.careertuner.fitanalysis.ai;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog;

/**
 * 적합도 분석의 OpenAI 단계. 키가 있으면 실제 구조화 분석을 실행하고, 없거나 실패하면 결정적 mock 으로 폴백한다.
 *
 * <p>활성 진입점(@Primary)은 {@link FallbackFitAnalysisAiService}(OSS→Claude→OpenAI)다. 이 서비스는 그
 * 폴백 체인의 마지막(OpenAI) 단계이며, 내부 mock 폴백이 최종 안전망 역할을 해 화면이 깨지지 않게 한다.
 * 스키마·파싱은 {@link FitAnalysisStructuredMapper} 를 Claude 단계와 공유한다.
 */
@Service
public class OpenAiFitAnalysisAiService implements FitAnalysisAiService {

    private final CareerAnalysisOpenAiClient openAiClient;
    private final MockFitAnalysisAiService mockService;
    private final FitAnalysisStructuredMapper mapper;

    public OpenAiFitAnalysisAiService(CareerAnalysisOpenAiClient openAiClient,
                                      MockFitAnalysisAiService mockService,
                                      FitAnalysisStructuredMapper mapper) {
        this.openAiClient = openAiClient;
        this.mockService = mockService;
        this.mapper = mapper;
    }

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        if (!openAiClient.configured()) {
            return mockService.generate(command);
        }

        try {
            StructuredResponse response = openAiClient.request(
                    FitAnalysisStructuredMapper.SCHEMA_NAME,
                    mapper.schema(),
                    FitAnalysisPromptCatalog.SYSTEM_PROMPT,
                    FitAnalysisPromptCatalog.userPrompt(
                            command.companyName(),
                            command.jobTitle(),
                            String.join(", ", command.requiredSkills()),
                            String.join(", ", command.preferredSkills()),
                            command.duties(),
                            String.join(", ", command.profileSkills()),
                            String.join(", ", command.profileCertificates()),
                            command.desiredJob()));
            return mapper.toResult(response.payload(), response.usage());
        } catch (RuntimeException exception) {
            FitAnalysisAiResult fallback = mockService.generate(command);
            return new FitAnalysisAiResult(
                    fallback.fitScore(),
                    fallback.matchedSkills(),
                    fallback.missingSkills(),
                    fallback.recommendedStudy(),
                    fallback.recommendedCertificates(),
                    fallback.strategy(),
                    fallback.scoreBasis(),
                    fallback.gapRecommendations(),
                    fallback.learningRoadmap(),
                    fallback.certificateRecommendations(),
                    fallback.strategyActions(),
                    fallback.conditionMatrix(),
                    fallback.applyDecision(),
                    new CareerAnalysisAiUsage("mock-fallback", 0, 0, 0, true),
                    "FALLBACK",
                    exception.getMessage(),
                    true);
        }
    }
}
