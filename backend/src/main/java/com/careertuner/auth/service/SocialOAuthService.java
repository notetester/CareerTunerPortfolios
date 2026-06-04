package com.careertuner.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.config.CareerTunerProperties.Oauth.Provider;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카카오/네이버/구글 OAuth2 authorization-code 흐름을 수동 REST 로 처리한다
 * (Spring Security oauth2Login 미사용 — TripTogether 와 동일한 방식).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialOAuthService {

    private final CareerTunerProperties props;
    private final RestClient rest = RestClient.create();

    /** 제공자 인가(authorize) URL. 사용자를 이 URL 로 리다이렉트한다. */
    public String getAuthorizationUrl(String provider, String state) {
        Provider p = providerConfig(provider);
        if (!p.isConfigured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, provider + " OAuth 키가 설정되지 않았습니다.");
        }
        return switch (provider) {
            case "KAKAO" -> "https://kauth.kakao.com/oauth/authorize?response_type=code"
                    + "&client_id=" + enc(p.getClientId())
                    + "&redirect_uri=" + enc(p.getRedirectUri())
                    + "&state=" + enc(state);
            case "NAVER" -> "https://nid.naver.com/oauth2.0/authorize?response_type=code"
                    + "&client_id=" + enc(p.getClientId())
                    + "&redirect_uri=" + enc(p.getRedirectUri())
                    + "&state=" + enc(state);
            case "GOOGLE" -> "https://accounts.google.com/o/oauth2/v2/auth?response_type=code"
                    + "&client_id=" + enc(p.getClientId())
                    + "&redirect_uri=" + enc(p.getRedirectUri())
                    + "&scope=" + enc("openid email profile")
                    + "&state=" + enc(state);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 소셜 제공자: " + provider);
        };
    }

    /** 콜백의 code 로 액세스 토큰을 받고, 사용자 정보를 조회한다. */
    public SocialUserInfo fetchUserInfo(String provider, String code, String state) {
        String accessToken = exchangeToken(provider, code, state);
        return switch (provider) {
            case "KAKAO" -> fetchKakao(accessToken);
            case "NAVER" -> fetchNaver(accessToken);
            case "GOOGLE" -> fetchGoogle(accessToken);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 소셜 제공자: " + provider);
        };
    }

    public boolean isSupported(String provider) {
        return "KAKAO".equals(provider) || "NAVER".equals(provider) || "GOOGLE".equals(provider);
    }

    // ── 내부 ──

    private String exchangeToken(String provider, String code, String state) {
        Provider p = providerConfig(provider);
        String tokenUrl = switch (provider) {
            case "KAKAO" -> "https://kauth.kakao.com/oauth/token";
            case "NAVER" -> "https://nid.naver.com/oauth2.0/token";
            case "GOOGLE" -> "https://oauth2.googleapis.com/token";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 소셜 제공자: " + provider);
        };
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", p.getClientId());
        if (p.getClientSecret() != null && !p.getClientSecret().isBlank()) {
            form.add("client_secret", p.getClientSecret());
        }
        form.add("redirect_uri", p.getRedirectUri());
        form.add("code", code);
        if ("NAVER".equals(provider) && state != null) {
            form.add("state", state);
        }

        Map<?, ?> body = rest.post().uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        Object accessToken = body != null ? body.get("access_token") : null;
        if (accessToken == null) {
            log.warn("[{}] 토큰 교환 응답에 access_token 없음: {}", provider, body);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, provider + " 토큰 교환에 실패했습니다.");
        }
        return accessToken.toString();
    }

    private SocialUserInfo fetchKakao(String accessToken) {
        Map<?, ?> me = userInfo("https://kapi.kakao.com/v2/user/me", accessToken);
        String id = String.valueOf(me.get("id"));
        Map<?, ?> account = asMap(me.get("kakao_account"));
        String email = account != null ? (String) account.get("email") : null;
        String name = null;
        if (account != null) {
            Map<?, ?> profile = asMap(account.get("profile"));
            if (profile != null) {
                name = (String) profile.get("nickname");
            }
        }
        return new SocialUserInfo("KAKAO", id, email, name != null ? name : "카카오사용자");
    }

    private SocialUserInfo fetchNaver(String accessToken) {
        Map<?, ?> me = userInfo("https://openapi.naver.com/v1/nid/me", accessToken);
        Map<?, ?> response = asMap(me.get("response"));
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "네이버 사용자 정보를 가져오지 못했습니다.");
        }
        String id = String.valueOf(response.get("id"));
        String email = (String) response.get("email");
        String name = (String) response.get("name");
        if (name == null) {
            name = (String) response.get("nickname");
        }
        return new SocialUserInfo("NAVER", id, email, name != null ? name : "네이버사용자");
    }

    private SocialUserInfo fetchGoogle(String accessToken) {
        Map<?, ?> me = userInfo("https://www.googleapis.com/oauth2/v2/userinfo", accessToken);
        String id = String.valueOf(me.get("id"));
        String email = (String) me.get("email");
        String name = (String) me.get("name");
        return new SocialUserInfo("GOOGLE", id, email, name != null ? name : "구글사용자");
    }

    private Map<?, ?> userInfo(String url, String accessToken) {
        Map<?, ?> body = rest.get().uri(url)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        if (body == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "소셜 사용자 정보를 가져오지 못했습니다.");
        }
        return body;
    }

    private Provider providerConfig(String provider) {
        return switch (provider) {
            case "KAKAO" -> props.getOauth().getKakao();
            case "NAVER" -> props.getOauth().getNaver();
            case "GOOGLE" -> props.getOauth().getGoogle();
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 소셜 제공자: " + provider);
        };
    }

    private static Map<?, ?> asMap(Object o) {
        return (o instanceof Map<?, ?> m) ? m : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
