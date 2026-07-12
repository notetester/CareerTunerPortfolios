package com.careertuner.fitanalysis.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
import com.careertuner.ai.common.model.AiProviderChain;
import com.careertuner.ai.common.model.AiProviderTier;
import com.careertuner.ai.common.model.RequestedAiModel;
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

    /** C 적합도 기본 tier 순서. AUTO 는 이 전체를, 명시 선택은 그 tier 부터 하위까지 시도한다. */
    private static final List<AiProviderTier> DEFAULT_ORDER =
            List.of(AiProviderTier.CAREERTUNER, AiProviderTier.CLAUDE, AiProviderTier.OPENAI);

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
        return generate(command, RequestedAiModel.AUTO);
    }

    /**
     * 사용자 선택 모델부터 시작하는 폴백 디스패치. {@code AUTO} 는 기존 자체→Claude→OpenAI 순서와 <b>byte-동일</b>
     * 동작이고, 명시 선택은 그 tier 부터 시작하되 실패/미가용 시 하위 tier → OpenAI 내부 Mock 안전망까지 폴백한다
     * (선택이 화면을 깨지 않는다). <b>판단값은 어느 tier 를 타든 각 provider 가 규칙엔진으로 먼저 계산</b>하므로
     * 모델 선택은 설명 텍스트만 바꾼다(뉴로-심볼릭 불변식).
     */
    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command, RequestedAiModel requestedModel) {
        // 체인 데드라인 — 각 tier 의 첫 시도는 못 자르고(per-tier 타임아웃 우선), 클라이언트 내부 재시도만 유계한다.
        long deadline = AiTotalTimeBudget.deadlineNanos(aiRuntimeSettings.analysisChainTotalTimeBudget());
        // 명시적 CAREERTUNER 선택은 전역 provider 토글(properties.isOss())을 우회해 자체모델을 시도한다
        // (엔드포인트가 있으면). AUTO 는 기존처럼 토글을 존중한다.
        boolean explicitSelf = requestedModel == RequestedAiModel.CAREERTUNER;

        for (AiProviderTier tier : AiProviderChain.startingFrom(requestedModel, DEFAULT_ORDER)) {
            switch (tier) {
                case CAREERTUNER -> {
                    if (ossClient.available() && (properties.isOss() || explicitSelf)) {
                        try {
                            return ossService.generate(command);
                        } catch (RuntimeException ex) {
                            log.warn("C 적합도 OSS 자체모델 실패 → 다음 폴백: {}", ex.getMessage());
                        }
                    }
                }
                case CLAUDE -> {
                    // Claude(Haiku) — 공통 키라 가장 안정적. 키 없으면 건너뛰고, 실패하면 다음 tier 로.
                    if (anthropicService.configured()) {
                        try {
                            return anthropicService.generate(command, aiRuntimeSettings.analysisClaudeTimeout(), deadline);
                        } catch (RuntimeException ex) {
                            log.warn("C 적합도 Claude 실패 → 다음 폴백: {}", ex.getMessage());
                        }
                    }
                }
                case OPENAI -> {
                    // 항상 시도(최종 안전망). 키 없거나 실패하면 내부 Mock 으로 폴백(절대 예외 없음).
                    return openAiService.generate(command, aiRuntimeSettings.analysisOpenaiTimeout(), deadline);
                }
            }
        }
        // 시도 순서가 OPENAI 로 끝나지 않는 경우는 없지만(방어), 최종 안전망을 보장한다.
        return openAiService.generate(command, aiRuntimeSettings.analysisOpenaiTimeout(), deadline);
    }
}
