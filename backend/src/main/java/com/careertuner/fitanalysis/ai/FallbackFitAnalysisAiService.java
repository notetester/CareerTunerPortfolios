package com.careertuner.fitanalysis.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
import com.careertuner.ai.common.settings.AiRuntimeSettings;
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
 * <p><b>하이브리드 폴백-타임아웃</b>: 설정된 각 tier 는 <b>항상 최소 한 번은 시도</b>된다(예산 소진으로
 * 건너뛰지 않음). 각 tier 의 첫 시도는 그 tier 의 "최소 보장" per-attempt 타임아웃
 * ({@link CareerAnalysisAiProviderProperties#getClaudeTimeout()}/{@code getOpenaiTimeout()})으로 유계되고,
 * 이 값이 체인 total 보다 우선한다. 체인 총 시간예산
 * ({@link CareerAnalysisAiProviderProperties#getChainTotalTimeBudget()}, 기본 120s)은 각 클라이언트 내부
 * <b>재시도 증폭만 억제하는 보조 상한</b>이다(첫 시도는 절대 못 자름). Mock 은 세 tier 가 다 시도된 뒤
 * OpenAI tier 내부 폴백으로만 도달한다(진짜 최후 안전망) — 별도 Mock tier 로는 진입하지 않는다.
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
    // 시간 정책(체인 예산·per-tier 타임아웃)은 DB-first 런타임 설정에서 읽는다(정적 프로퍼티 fallback).
    private final AiRuntimeSettings aiRuntimeSettings;

    public FallbackFitAnalysisAiService(OssFitAnalysisAiService ossService,
                                        AnthropicFitAnalysisAiService anthropicService,
                                        OpenAiFitAnalysisAiService openAiService,
                                        CareerAnalysisOssClient ossClient,
                                        CareerAnalysisAiProviderProperties properties,
                                        AiRuntimeSettings aiRuntimeSettings) {
        this.ossService = ossService;
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
        this.ossClient = ossClient;
        this.properties = properties;
        this.aiRuntimeSettings = aiRuntimeSettings;
    }

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        // 체인 데드라인 — 각 tier 의 첫 시도는 못 자르고(per-tier 타임아웃 우선), 클라이언트 내부 재시도만 유계한다.
        // 시간예산·per-tier 타임아웃은 DB-first 런타임 설정에서 읽는다(정적 프로퍼티 fallback).
        long deadline = AiTotalTimeBudget.deadlineNanos(aiRuntimeSettings.analysisChainTotalTimeBudget());

        // 1) 자체모델(OSS) — provider=oss + base-url 설정 시. 실패하면 아래로 폴백(OSS 는 자체 oss.total-time-budget 보유).
        if (properties.isOss() && ossClient.available()) {
            try {
                return ossService.generate(command);
            } catch (RuntimeException ex) {
                log.warn("C 적합도 OSS 자체모델 실패 → Claude 폴백: {}", ex.getMessage());
            }
        }
        // 2) 1차 폴백: Claude(Haiku) — 공통 키라 가장 안정적. 키 없으면 건너뛰고, 실패하면 OpenAI 로.
        //    첫 시도는 claudeTimeout 이 보장(체인 예산 소진 무관), 재시도만 체인 데드라인이 억제.
        if (anthropicService.configured()) {
            try {
                return anthropicService.generate(command, aiRuntimeSettings.analysisClaudeTimeout(), deadline);
            } catch (RuntimeException ex) {
                log.warn("C 적합도 Claude 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        // 3) OpenAI 단계 — 항상 시도(최종 안전망). 키 없거나 실패하면 내부 Mock 으로 폴백(절대 예외 없음).
        //    첫 시도는 openaiTimeout 이 보장, 재시도만 체인 데드라인이 억제.
        return openAiService.generate(command, aiRuntimeSettings.analysisOpenaiTimeout(), deadline);
    }
}
