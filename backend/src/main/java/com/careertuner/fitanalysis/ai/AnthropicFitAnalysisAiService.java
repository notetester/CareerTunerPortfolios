package com.careertuner.fitanalysis.ai;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAnthropicClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog;

/**
 * 적합도 분석의 Claude(Haiku) 단계 — 폴백 디스패처의 1차 폴백 provider.
 *
 * <p>{@link OpenAiFitAnalysisAiService} 와 같은 스키마·파싱({@link FitAnalysisStructuredMapper})을 쓰되
 * 전송만 {@link CareerAnalysisAnthropicClient} 로 바꾼 형태다. 키가 없거나 호출이 실패하면 예외를 던지고,
 * 상위 {@link FallbackFitAnalysisAiService} 가 OpenAI 단계로 폴백한다(이 클래스는 자체 mock 폴백을 두지 않는다).
 */
@Service
public class AnthropicFitAnalysisAiService implements FitAnalysisAiService {

    private final CareerAnalysisAnthropicClient anthropicClient;
    private final FitAnalysisStructuredMapper mapper;

    public AnthropicFitAnalysisAiService(CareerAnalysisAnthropicClient anthropicClient,
                                         FitAnalysisStructuredMapper mapper) {
        this.anthropicClient = anthropicClient;
        this.mapper = mapper;
    }

    public boolean configured() {
        return anthropicClient.configured();
    }

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        StructuredResponse response = anthropicClient.request(
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
    }
}
