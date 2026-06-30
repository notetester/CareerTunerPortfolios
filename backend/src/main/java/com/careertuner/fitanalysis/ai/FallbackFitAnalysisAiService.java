package com.careertuner.fitanalysis.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiProviderProperties;
import com.careertuner.analysis.ai.provider.CareerAnalysisOssClient;

/**
 * 적합도 분석 AI 진입점(@Primary) — 자체모델(OSS) → Claude(Haiku) → OpenAI → Mock 폴백 디스패처.
 *
 * <p>D 의 {@code FallbackInterviewLlmGateway} 패턴을 C 적합도 도메인으로 가져왔다(D 파일 미수정).
 * provider=oss + base-url 설정 시 자체모델을 1차로 시도하고, 실패하면 Claude(공통 키라 가장 안정적인 1차 폴백) →
 * OpenAI({@link OpenAiFitAnalysisAiService}, 키 없으면 내부 Mock 폴백) 순으로 전환한다. 따라서 어느 provider 가
 * 죽거나 응답이 깨져도 화면은 깨지지 않는다(OpenAI 단계의 내부 Mock 이 최종 안전망).
 *
 * <p>기본값 provider=openai → 자체모델 비활성. Anthropic 키가 비어 있으면 Claude 단계도 건너뛰어 기존 동작과 동일하다.
 */
@Primary
@Service
public class FallbackFitAnalysisAiService implements FitAnalysisAiService {

    private static final Logger log = LoggerFactory.getLogger(FallbackFitAnalysisAiService.class);

    private final OssFitAnalysisAiService ossService;
    private final AnthropicFitAnalysisAiService anthropicService;
    private final OpenAiFitAnalysisAiService openAiService;
    private final CareerAnalysisOssClient ossClient;
    private final CareerAnalysisAiProviderProperties properties;

    public FallbackFitAnalysisAiService(OssFitAnalysisAiService ossService,
                                        AnthropicFitAnalysisAiService anthropicService,
                                        OpenAiFitAnalysisAiService openAiService,
                                        CareerAnalysisOssClient ossClient,
                                        CareerAnalysisAiProviderProperties properties) {
        this.ossService = ossService;
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
        this.ossClient = ossClient;
        this.properties = properties;
    }

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        // 1) 자체모델(OSS) — provider=oss + base-url 설정 시. 실패하면 아래로 폴백.
        if (properties.isOss() && ossClient.available()) {
            try {
                return ossService.generate(command);
            } catch (RuntimeException ex) {
                log.warn("C 적합도 OSS 자체모델 실패 → Claude 폴백: {}", ex.getMessage());
            }
        }
        // 2) 1차 폴백: Claude(Haiku) — 공통 키라 가장 안정적. 키 없으면 건너뛰고, 실패하면 OpenAI 로.
        if (anthropicService.configured()) {
            try {
                return anthropicService.generate(command);
            } catch (RuntimeException ex) {
                log.warn("C 적합도 Claude 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        // 3) OpenAI 단계(키 없거나 실패하면 내부 Mock 으로 폴백 — 최종 안전망).
        return openAiService.generate(command);
    }
}
