package com.careertuner.analysis.ai;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.prompt.CareerTrendPromptCatalog;
import com.careertuner.analysis.ai.provider.CareerAnalysisAnthropicClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;

/**
 * 커리어 트렌드의 Claude(Haiku) 단계 — 폴백 디스패처의 1차 폴백 provider.
 *
 * <p>{@link OpenAiCareerTrendAiService} 와 같은 스키마·파싱({@link CareerTrendStructuredMapper})을 쓰되
 * 전송만 {@link CareerAnalysisAnthropicClient} 로 바꾼 형태다. 키가 없거나 호출이 실패하면 예외를 던지고,
 * 상위 {@link FallbackCareerTrendAiService} 가 OpenAI 단계로 폴백한다(자체 mock 폴백 없음).
 */
@Service
public class AnthropicCareerTrendAiService implements CareerTrendAiService {

    private final CareerAnalysisAnthropicClient anthropicClient;
    private final CareerTrendStructuredMapper mapper;

    public AnthropicCareerTrendAiService(CareerAnalysisAnthropicClient anthropicClient,
                                         CareerTrendStructuredMapper mapper) {
        this.anthropicClient = anthropicClient;
        this.mapper = mapper;
    }

    public boolean configured() {
        return anthropicClient.configured();
    }

    @Override
    public CareerTrendAiResult generate(CareerTrendAiCommand command) {
        StructuredResponse response = anthropicClient.request(
                CareerTrendStructuredMapper.SCHEMA_NAME,
                mapper.schema(),
                CareerTrendPromptCatalog.SYSTEM_PROMPT,
                mapper.userPrompt(command));
        return mapper.toResult(response.payload(), response.usage());
    }

    /**
     * 폴백 디스패처가 Claude tier 의 per-attempt 타임아웃({@code perAttemptTimeout})과 체인 데드라인
     * ({@code chainDeadlineNanos})을 주입하는 오버로드. 6-arg client.request(...) 로 그대로 넘겨 첫 시도는
     * 데드라인과 무관하게 보장하고 재시도만 유계화한다. 실패 시 예외를 던져 상위가 OpenAI 로 폴백한다.
     */
    public CareerTrendAiResult generate(CareerTrendAiCommand command,
                                        Duration perAttemptTimeout,
                                        long chainDeadlineNanos) {
        StructuredResponse response = anthropicClient.request(
                CareerTrendStructuredMapper.SCHEMA_NAME,
                mapper.schema(),
                CareerTrendPromptCatalog.SYSTEM_PROMPT,
                mapper.userPrompt(command),
                perAttemptTimeout,
                chainDeadlineNanos);
        return mapper.toResult(response.payload(), response.usage());
    }
}
