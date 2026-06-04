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
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessValiditySeconds)))
                .signWith(key)
                .compact();
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
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(provider)
                .id(UUID.randomUUID().toString())
                .claim("type", "oauth_state")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(key)
                .compact();
    }

    public boolean validateOauthState(String state, String provider) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(state).getPayload();
            return "oauth_state".equals(c.get("type", String.class)) && provider.equals(c.getSubject());
        } catch (Exception e) {
            return false;
        }
    }
}
