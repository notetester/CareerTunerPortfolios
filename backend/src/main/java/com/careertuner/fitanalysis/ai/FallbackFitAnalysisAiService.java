package com.careertuner.fitanalysis.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiProviderProperties;
import com.careertuner.analysis.ai.provider.CareerAnalysisOssClient;

/**
 * 적합도 분석 AI 진입점(@Primary) — 자체모델(OSS) → OpenAI → Mock 폴백 디스패처.
 *
 * <p>D 의 {@code FallbackInterviewLlmGateway} 패턴을 C 적합도 도메인으로 가져왔다(D 파일 미수정).
 * provider=oss + base-url 설정 시 자체모델을 1차로 시도하고, 실패하면 OpenAI({@link OpenAiFitAnalysisAiService},
 * 키 없으면 내부 Mock 폴백)로 전환한다. 따라서 자체모델이 죽거나 응답이 깨져도 화면은 깨지지 않는다.
 *
 * <p>기본값 provider=openai → 기존 동작과 동일(자체모델 비활성). base-url 미설정 시에도 OSS 는 시도하지 않는다.
 */
@Primary
@Service
public class FallbackFitAnalysisAiService implements FitAnalysisAiService {

    private static final Logger log = LoggerFactory.getLogger(FallbackFitAnalysisAiService.class);

    private final OssFitAnalysisAiService ossService;
    private final OpenAiFitAnalysisAiService openAiService;
    private final CareerAnalysisOssClient ossClient;
    private final CareerAnalysisAiProviderProperties properties;

    public FallbackFitAnalysisAiService(OssFitAnalysisAiService ossService,
                                        OpenAiFitAnalysisAiService openAiService,
                                        CareerAnalysisOssClient ossClient,
                                        CareerAnalysisAiProviderProperties properties) {
        this.ossService = ossService;
        this.openAiService = openAiService;
        this.ossClient = ossClient;
        this.properties = properties;
    }

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        if (properties.isOss() && ossClient.available()) {
            try {
                return ossService.generate(command);
            } catch (RuntimeException ex) {
                log.warn("C 적합도 OSS 자체모델 실패 → OpenAI/Mock 폴백: {}", ex.getMessage());
            }
        }
        // OpenAI 단계(키 있으면 실제 호출, 없거나 실패하면 OpenAiFitAnalysisAiService 내부에서 Mock 으로 폴백).
        return openAiService.generate(command);
    }
}
