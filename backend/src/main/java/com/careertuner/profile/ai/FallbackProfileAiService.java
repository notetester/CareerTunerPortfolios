package com.careertuner.profile.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.ai.common.model.AiProviderChain;
import com.careertuner.ai.common.model.AiProviderTier;
import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.profile.domain.UserProfile;

/**
 * 프로필 평가 AI 폴백 디스패처 — Claude(Haiku) → OpenAI → 규칙기반.
 *
 * <p>Anthropic 키가 있으면 Claude 를 먼저 시도하고, 실패하면 {@link OpenAiProfileAiService}(키 없거나
 * 실패 시 {@link RuleBasedProfileAiService} 규칙기반 폴백 — 최종 안전망)로 넘어간다. 키가 비어 있으면 Claude
 * 단계는 건너뛴다. 따라서 외부 LLM 이 모두 죽어도 결정적 규칙기반 결과로 화면이 채워진다.
 */
@Service
public class FallbackProfileAiService implements ProfileAiService {

    private static final Logger log = LoggerFactory.getLogger(FallbackProfileAiService.class);

    /** 이 폴백 디스패처의 tier 순서(자체모델은 상위 FineTuned 담당이라 여기엔 Claude·OpenAI 만). */
    private static final List<AiProviderTier> DEFAULT_ORDER =
            List.of(AiProviderTier.CLAUDE, AiProviderTier.OPENAI);

    private final AnthropicProfileAiService anthropicService;
    private final OpenAiProfileAiService openAiService;

    public FallbackProfileAiService(AnthropicProfileAiService anthropicService,
                                    OpenAiProfileAiService openAiService) {
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
    }

    @Override
    public ProfileAiResult evaluate(UserProfile profile, String featureType) {
        return evaluate(profile, featureType, RequestedAiModel.AUTO);
    }

    /**
     * 사용자 선택 tier 부터 시작하는 Claude→OpenAI 폴백. AUTO 는 현행과 동일. OpenAI 단계는 내부에서
     * 규칙기반으로 폴백하므로 최종 안전망이다(외부 LLM 전멸에도 결정적 결과로 화면을 채움). 자체모델(CAREERTUNER)
     * 선택은 상위 {@link FineTunedProfileAiService} 에서 처리되고, 여기로 넘어오면 이 도메인엔 그 tier 가 없어
     * 기본 순서로 폴백한다(fail-open).
     */
    @Override
    public ProfileAiResult evaluate(UserProfile profile, String featureType, RequestedAiModel requestedModel) {
        for (AiProviderTier tier : AiProviderChain.startingFrom(requestedModel, DEFAULT_ORDER)) {
            switch (tier) {
                case CLAUDE -> {
                    if (anthropicService.configured()) {
                        try {
                            return anthropicService.evaluate(profile, featureType);
                        } catch (RuntimeException ex) {
                            log.warn("프로필 Claude 실패 → OpenAI 폴백: {}", ex.getMessage());
                        }
                    }
                }
                case OPENAI -> {
                    return openAiService.evaluate(profile, featureType);
                }
                case CAREERTUNER -> {
                    // 자체모델 tier 는 이 디스패처에 없음 — AiProviderChain 이 fail-open 으로 CLAUDE 부터 준다.
                }
            }
        }
        return openAiService.evaluate(profile, featureType);
    }
}
