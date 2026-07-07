package com.careertuner.fitanalysis.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
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
 * <p><b>체인 총 시간예산</b>({@code chain-total-time-budget}, 기본 120s): OSS 의 예산(90s)은 GPU tier 만
 * 묶으므로, 이 디스패처가 캐스케이드 전체의 사용자 대기 상한을 건다. 앞 tier 들이 예산을 소진하면 아직
 * 시작하지 않은 외부 tier(Claude/OpenAI)를 건너뛰고 즉시 {@link MockFitAnalysisAiService} 결정론
 * 안전망을 반환한다 — per-timeout 합(최악 ~720s) 대신 유계 응답을 보장한다. 예산 0/음수면 무제한(끔).
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
    private final MockFitAnalysisAiService mockService;
    private final CareerAnalysisOssClient ossClient;
    private final CareerAnalysisAiProviderProperties properties;

    public FallbackFitAnalysisAiService(OssFitAnalysisAiService ossService,
                                        AnthropicFitAnalysisAiService anthropicService,
                                        OpenAiFitAnalysisAiService openAiService,
                                        MockFitAnalysisAiService mockService,
                                        CareerAnalysisOssClient ossClient,
                                        CareerAnalysisAiProviderProperties properties) {
        this.ossService = ossService;
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
        this.mockService = mockService;
        this.ossClient = ossClient;
        this.properties = properties;
    }

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        // 캐스케이드 전체의 사용자 대기 상한. 소진 시 남은 외부 tier 를 건너뛰고 즉시 Mock 안전망.
        AiTotalTimeBudget chain = AiTotalTimeBudget.start(properties.getChainTotalTimeBudget());

        // 1) 자체모델(OSS) — provider=oss + base-url 설정 시. 실패하면 아래로 폴백.
        if (properties.isOss() && ossClient.available()) {
            try {
                return ossService.generate(command);
            } catch (RuntimeException ex) {
                log.warn("C 적합도 OSS 자체모델 실패 → Claude 폴백: {}", ex.getMessage());
            }
        }
        // 2) 1차 폴백: Claude(Haiku) — 공통 키라 가장 안정적. 키 없으면 건너뛰고, 실패하면 OpenAI 로.
        //    체인 예산이 남았을 때만 시작(외부 tier 는 초 단위라 예산 소진 시 즉시 Mock 으로).
        if (!chain.expired() && anthropicService.configured()) {
            try {
                return anthropicService.generate(command);
            } catch (RuntimeException ex) {
                log.warn("C 적합도 Claude 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        // 3) OpenAI 단계(키 없거나 실패하면 내부 Mock 으로 폴백 — 최종 안전망).
        if (!chain.expired()) {
            return openAiService.generate(command);
        }
        // 4) 체인 예산 소진 → 느린 외부 tier 를 건너뛰고 결정론 Mock 즉시 반환(화면 무깨짐).
        log.warn("C 적합도 체인 시간예산 {} 소진 → Mock 안전망 즉시 반환", properties.getChainTotalTimeBudget());
        return mockService.generate(command);
    }
}
