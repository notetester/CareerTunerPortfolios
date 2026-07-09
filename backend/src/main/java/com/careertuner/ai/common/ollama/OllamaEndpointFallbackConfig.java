package com.careertuner.ai.common.ollama;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

/**
 * Ollama 엔드포인트 자동 폴백 구성.
 *
 * <p>공유 4090 Ollama(기본 {@code http://localhost:11434})가 꺼져 있으면, 부팅 시 1회 프로브해
 * 살아있는 폴백(기본 {@code http://localhost:11434})으로 {@code ai.ollama.base-url} 과
 * {@code langchain4j.ollama.chat-model.base-url} 프로퍼티를 교체한다. 기존 Ollama 클라이언트들
 * (검열/챗봇/관리자 초안/에이전트)은 생성 시점에 base-url 을 고정하므로, 빈 생성 전에 프로퍼티를
 * 바꾸는 {@link BeanFactoryPostProcessor} 방식이 각 도메인 코드를 건드리지 않는 가장 보수적인 지점이다.
 *
 * <p>보수 원칙(FcmPushClient 의 graceful degrade 패턴과 동일 취지):
 * <ul>
 *   <li>설정된 엔드포인트가 살아있으면 아무것도 바꾸지 않는다.</li>
 *   <li>폴백 후보까지 전멸이면 아무것도 바꾸지 않는다 — 기존과 동일하게 설정값으로 호출하고 실패한다.</li>
 *   <li>프로브/교체 중 오류가 나도 부팅을 깨지 않는다(로그만 남기고 기존 동작 유지).</li>
 *   <li>{@code AI_OLLAMA_FALLBACK_ENABLED=false} 로 전체 기능을 끌 수 있다.</li>
 * </ul>
 *
 * <p>런타임(부팅 이후) 재조회가 필요한 호출부는 {@link OllamaEndpointResolver} 빈을 주입받아
 * {@code resolve()}/{@code reportFailure()} 를 사용한다(60초 캐시 + 실패 시 다음 후보 재시도).
 */
@Configuration
public class OllamaEndpointFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(OllamaEndpointFallbackConfig.class);

    /** 폴백 기능 ON/OFF (env {@code AI_OLLAMA_FALLBACK_ENABLED}). */
    static final String ENABLED_KEY = "ai.ollama.fallback-enabled";
    /** 폴백 후보 목록(콤마 구분, env {@code AI_OLLAMA_FALLBACK_BASE_URLS}). */
    static final String FALLBACK_URLS_KEY = "ai.ollama.fallback-base-urls";
    static final String BASE_URL_KEY = "ai.ollama.base-url";
    static final String LANGCHAIN4J_BASE_URL_KEY = "langchain4j.ollama.chat-model.base-url";
    /** 부팅 교체 후에도 원래 설정값을 잃지 않도록 보존하는 합성 키(런타임 리졸버가 1순위 후보로 사용). */
    static final String ORIGINAL_BASE_URL_KEY = "ai.ollama.configured-base-url";

    static final String DEFAULT_BASE_URL = "http://localhost:11434";
    static final String DEFAULT_FALLBACK_URLS = "http://localhost:11434";

    /**
     * 부팅 시 1회 실행되는 프로퍼티 교체기.
     * static — 다른 빈들이 생성되기 전에(=@ConfigurationProperties 바인딩 전에) 동작해야 한다.
     */
    @Bean
    public static BeanFactoryPostProcessor ollamaEndpointFallbackProcessor(ConfigurableEnvironment environment) {
        return beanFactory -> {
            try {
                if (!environment.getProperty(ENABLED_KEY, Boolean.class, true)) {
                    log.info("[ollama] 엔드포인트 자동 폴백 비활성({}=false)", ENABLED_KEY);
                    return;
                }
                String configured = environment.getProperty(BASE_URL_KEY, DEFAULT_BASE_URL);
                String fallbacks = environment.getProperty(FALLBACK_URLS_KEY, DEFAULT_FALLBACK_URLS);
                OllamaEndpointResolver resolver =
                        new OllamaEndpointResolver(configured, OllamaEndpointResolver.parseCandidates(fallbacks));
                String resolved = resolver.resolve();
                if (resolved.equals(resolver.primaryBaseUrl())) {
                    // 설정값이 살아있거나 전 후보 전멸 — 기존 동작 그대로.
                    return;
                }
                Map<String, Object> overrides = new LinkedHashMap<>();
                overrides.put(BASE_URL_KEY, resolved);
                overrides.put(ORIGINAL_BASE_URL_KEY, configured);
                // langchain4j 에이전트 모델도 같은 설정(AI_OLLAMA_BASE_URL)을 쓰는 경우에만 함께 교체한다.
                String langchainConfigured = environment.getProperty(LANGCHAIN4J_BASE_URL_KEY, configured);
                if (stripTrailingSlash(langchainConfigured).equals(stripTrailingSlash(configured))) {
                    overrides.put(LANGCHAIN4J_BASE_URL_KEY, resolved);
                }
                environment.getPropertySources()
                        .addFirst(new MapPropertySource("ollamaEndpointFallback", overrides));
                log.info("[ollama] 설정된 엔드포인트({}) 미응답 → 부팅 프로퍼티를 폴백 {} 로 교체: {}",
                        configured, resolved, overrides.keySet());
            } catch (Exception e) {
                // 폴백 로직 오류가 부팅을 깨지 않게 한다 — 기존 설정 그대로 진행.
                log.warn("[ollama] 엔드포인트 폴백 처리 실패 — 설정값 그대로 사용: {}", e.toString());
            }
        };
    }

    /** 런타임 폴백 확장점 — 호출 시점의 살아있는 엔드포인트가 필요한 도메인이 주입받아 사용한다. */
    @Bean
    public OllamaEndpointResolver ollamaEndpointResolver(Environment environment) {
        // 부팅 교체가 일어났어도 원래 설정값(예: 4090)을 1순위 후보로 유지해, 이후 살아나면 다시 선택되게 한다.
        String fallbackToCurrent = environment.getProperty(BASE_URL_KEY, DEFAULT_BASE_URL);
        String configured = environment.getProperty(ORIGINAL_BASE_URL_KEY, fallbackToCurrent);
        String fallbacks = environment.getProperty(FALLBACK_URLS_KEY, DEFAULT_FALLBACK_URLS);
        return new OllamaEndpointResolver(configured, OllamaEndpointResolver.parseCandidates(fallbacks));
    }

    private static String stripTrailingSlash(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }
}
