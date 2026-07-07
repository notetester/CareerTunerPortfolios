package com.careertuner.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.careertuner.common.config.CareerTunerProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 액세스 토큰 발급/검증과 OAuth state 토큰(무상태 CSRF 방지) 처리.
 * 리프레시 토큰은 DB(refresh_token)에서 관리하므로 여기서 다루지 않는다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessValiditySeconds;

    public JwtTokenProvider(CareerTunerProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessValiditySeconds = props.getJwt().getAccessTokenValiditySeconds();
    }

    public long getAccessValiditySeconds() {
        return accessValiditySeconds;
    }

    public String createAccessToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessValiditySeconds)))
                .signWith(key);
        if (email != null && !email.isBlank()) {
            builder.claim("email", email);
        }
        return builder.compact();
    }

    /** 유효하지 않으면 JwtException 을 던진다. */
    public AuthUser parseAccessToken(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!"access".equals(c.get("type", String.class))) {
            throw new JwtException("not an access token");
        }
        return new AuthUser(Long.valueOf(c.getSubject()), c.get("email", String.class), c.get("role", String.class));
    }

    /** OAuth 콜백 검증용 서명 state 토큰(5분). 세션/쿠키 없이 CSRF 를 방지한다. */
    public String createOauthState(String provider) {
        return createOauthState(provider, "LOGIN", null);
    }

    public String createOauthLinkState(String provider, Long userId) {
        return createOauthState(provider, "LINK", userId);
    }

    private String createOauthState(String provider, String mode, Long userId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(provider)
                .id(UUID.randomUUID().toString())
                .claim("type", "oauth_state")
                .claim("mode", mode)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(key);
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.compact();
    }

    public boolean validateOauthState(String state, String provider) {
        try {
            parseOauthState(state, provider);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public OauthState parseOauthState(String state, String provider) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(state).getPayload();
        if (!"oauth_state".equals(c.get("type", String.class)) || !provider.equals(c.getSubject())) {
            throw new JwtException("invalid oauth state");
        }
        String mode = c.get("mode", String.class);
        if (mode == null || mode.isBlank()) {
            mode = "LOGIN";
        }
        Object userIdClaim = c.get("userId");
        Long userId = userIdClaim instanceof Number number ? number.longValue() : null;
        return new OauthState(provider, mode, userId);
    }

    public record OauthState(String provider, String mode, Long userId) {
        public boolean linkMode() {
            return "LINK".equals(mode);
        }
    }
}
