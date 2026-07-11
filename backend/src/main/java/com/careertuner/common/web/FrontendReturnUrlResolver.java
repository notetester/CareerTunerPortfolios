package com.careertuner.common.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 요청이 시작된 신뢰 가능한 프런트엔드로 OAuth·메일·결제 결과를 돌려보낸다. */
@Component
@RequiredArgsConstructor
public class FrontendReturnUrlResolver {

    public static final String FRONTEND_CLIENT_HEADER = "X-CareerTuner-Frontend-Client";
    public static final String PRIMARY_CLIENT = "primary";
    public static final String SITES_CLIENT = "sites";

    private final CareerTunerProperties props;

    /** 클라이언트가 보낸 이름을 엄격히 해석한다. 누락은 primary이고 알 수 없는 값은 거부한다. */
    public FrontendReturnTarget resolve(HttpServletRequest request) {
        return resolveClient(request == null ? null : request.getHeader(FRONTEND_CLIENT_HEADER));
    }

    public FrontendReturnTarget resolveClient(String client) {
        if (client == null || PRIMARY_CLIENT.equals(client)) {
            return primary();
        }
        if (SITES_CLIENT.equals(client)) {
            return configuredTarget(SITES_CLIENT, props.getApp().getSitesFrontendUrl());
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 프런트엔드 클라이언트입니다.");
    }

    /** DB나 과거 OAuth state의 값은 기존 데이터 호환을 위해 알 수 없더라도 primary로 안전하게 돌린다. */
    public FrontendReturnTarget resolveStoredClient(String client) {
        if (SITES_CLIENT.equals(normalizeClient(client)) && hasText(props.getApp().getSitesFrontendUrl())) {
            return configuredTarget(SITES_CLIENT, props.getApp().getSitesFrontendUrl());
        }
        return primary();
    }

    public FrontendReturnTarget primary() {
        return configuredTarget(PRIMARY_CLIENT, props.getApp().getFrontendUrl());
    }

    private FrontendReturnTarget configuredTarget(String client, String configuredUrl) {
        String normalized = normalizeOrigin(configuredUrl);
        if (normalized == null) {
            if (SITES_CLIENT.equals(client)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "Sites 프런트엔드가 설정되지 않았습니다.");
            }
            throw new IllegalStateException("careertuner.app.frontend-url must be a valid frontend origin");
        }
        return new FrontendReturnTarget(client, normalized);
    }

    static String normalizeOrigin(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            if (scheme == null || host == null || uri.isOpaque()
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (path != null && !path.isBlank() && !"/".equals(path))) {
                return null;
            }
            boolean local = "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host) || "::1".equals(host);
            if (!("https".equalsIgnoreCase(scheme) || (local && "http".equalsIgnoreCase(scheme)))) {
                return null;
            }
            return new URI(scheme.toLowerCase(Locale.ROOT), null, host.toLowerCase(Locale.ROOT),
                    uri.getPort(), null, null, null).toString();
        } catch (IllegalArgumentException | URISyntaxException e) {
            return null;
        }
    }

    private static String normalizeClient(String client) {
        return hasText(client) ? client.trim().toLowerCase(Locale.ROOT) : PRIMARY_CLIENT;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
