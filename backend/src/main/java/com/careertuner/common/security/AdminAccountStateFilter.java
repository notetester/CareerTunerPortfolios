package com.careertuner.common.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 관리자 API에서 JWT 발급 당시가 아니라 현재 DB 계정 상태와 역할을 최종 확인한다.
 * 역할 claim과 현재 역할이 다르면 자동 승격/강등하지 않고 새 토큰 발급을 요구한다.
 */
public class AdminAccountStateFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountStateFilter.class);

    private final UserMapper userMapper;
    private final SecurityErrorResponseWriter errorWriter;

    public AdminAccountStateFilter(UserMapper userMapper, SecurityErrorResponseWriter errorWriter) {
        this.userMapper = userMapper;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !(uri.equals("/api/admin") || uri.startsWith("/api/admin/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser tokenUser)) {
            chain.doFilter(request, response);
            return;
        }

        final User currentUser;
        try {
            currentUser = userMapper.findById(tokenUser.id());
        } catch (RuntimeException ex) {
            log.error("관리자 현재 계정 상태 조회 실패: userId={}", tokenUser.id(), ex);
            SecurityContextHolder.clearContext();
            errorWriter.serviceUnavailable(response);
            return;
        }

        if (currentUser == null || !"ACTIVE".equals(currentUser.getStatus())) {
            SecurityContextHolder.clearContext();
            errorWriter.unauthorized(response);
            return;
        }
        if (!tokenUser.role().equals(currentUser.getRole())) {
            SecurityContextHolder.clearContext();
            // 역할 변경 시 기존 access/refresh 조합을 재사용하지 않고 인증 상태를 즉시 초기화한다.
            errorWriter.unauthorized(response);
            return;
        }
        if (!"ADMIN".equals(currentUser.getRole()) && !"SUPER_ADMIN".equals(currentUser.getRole())) {
            SecurityContextHolder.clearContext();
            errorWriter.forbidden(response);
            return;
        }

        chain.doFilter(request, response);
    }
}
