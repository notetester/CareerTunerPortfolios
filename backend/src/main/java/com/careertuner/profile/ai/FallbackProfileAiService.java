package com.careertuner.profile.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private final AnthropicProfileAiService anthropicService;
    private final OpenAiProfileAiService openAiService;

    public FallbackProfileAiService(AnthropicProfileAiService anthropicService,
                                    OpenAiProfileAiService openAiService) {
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
    }

    @Override
    public ProfileAiResult evaluate(UserProfile profile, String featureType) {
        if (anthropicService.configured()) {
            try {
                return anthropicService.evaluate(profile, featureType);
            } catch (RuntimeException ex) {
                log.warn("프로필 Claude 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        return openAiService.evaluate(profile, featureType);
    }
}
