package com.careertuner.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization: Bearer &lt;access token&gt; 를 검증해 SecurityContext 에 인증을 채운다.
 * 토큰이 없거나 유효하지 않으면 그냥 통과시키고(익명), 인가는 SecurityConfig 가 판단한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthUser user = tokenProvider.parseAccessToken(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {
                // 유효하지 않은 토큰 → 익명으로 진행
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
