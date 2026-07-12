package com.careertuner.common.config;

import java.util.List;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.careertuner.common.security.AdminAccountStateFilter;
import com.careertuner.common.security.JwtAuthenticationFilter;
import com.careertuner.common.security.SecurityErrorResponseWriter;
import com.careertuner.user.mapper.UserMapper;

/**
 * 보안 설정 — JWT 기반 stateless 인증.
 *
 * <p>공개 엔드포인트(헬스/스웨거/인증 진입점)를 제외한 모든 요청은 액세스 토큰을 요구한다.
 * 비밀번호는 BCrypt 로 저장한다.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   UserMapper userMapper,
                                                   SecurityErrorResponseWriter securityErrorResponseWriter) throws Exception {
        AdminAccountStateFilter adminAccountStateFilter =
                new AdminAccountStateFilter(userMapper, securityErrorResponseWriter);
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        // 비동기(SSE)·에러 내부 재디스패치는 원 요청에서 이미 인증됨 → 허용(없으면 SSE/async 가 401)
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/health", "/api/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**", "/api-docs/**").permitAll()
                        // 인증 공개 엔드포인트
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register", "/api/auth/login",
                                "/api/auth/mfa/login/verify",
                                "/api/auth/refresh", "/api/auth/logout", "/api/auth/logout-all",
                                "/api/auth/email/resend",
                                "/api/auth/find-id/request",
                                "/api/auth/password/reset-request", "/api/auth/password/reset",
                                "/api/auth/dormant/release-request", "/api/auth/dormant/release",
                                "/api/auth/oauth/*/native/start",
                                "/api/auth/oauth/native/exchange").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/auth/verify-email", "/api/auth/find-id/verify",
                                "/api/auth/check/**", "/api/auth/oauth/**",
                                "/api/auth/mfa/login/status").permitAll()
                        // 커뮤니티 게시글 조회 공개
                        .requestMatchers(HttpMethod.GET,
                                "/api/community/posts", "/api/community/posts/**",
                                "/api/community/users/*/activity",
                                "/api/community/users/*/activity-tabs",
                                "/api/community/guidelines/published").permitAll()
                        // 공개 채용 게시판 조회(목록·상세) — 비로그인 브라우징 허용, /{id}/analyze(POST)는 인증 필요
                        .requestMatchers(HttpMethod.GET, "/api/job-board", "/api/job-board/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/ads").permitAll()
                        // 노출·클릭 집계는 비로그인 광고 수신자도 발사(공개 서빙 대칭). 엔드포인트는 /impression·/click.
                        .requestMatchers(HttpMethod.POST, "/api/ads/*/impression", "/api/ads/*/click").permitAll()
                        // 법적 문서(약관/개인정보/마케팅) 공개 조회
                        .requestMatchers(HttpMethod.GET, "/api/legal/**").permitAll()
                        // 고객센터 FAQ/공지사항 조회 공개
                        .requestMatchers(HttpMethod.GET,
                                "/api/support/faq", "/api/support/notices", "/api/support/notices/**").permitAll()
                        // 결제 전 가격/상품/차감 정책 조회 공개
                        .requestMatchers(HttpMethod.GET,
                                "/api/billing/plans", "/api/billing/credit-products",
                                "/api/billing/feature-benefit-policies", "/api/credit-products").permitAll()
                        // 챗봇 질문·추천 후기 요약 공개(비로그인도 사용)
                        .requestMatchers(HttpMethod.POST, "/api/chatbot/ask", "/api/chatbot/summarize-posts").permitAll()
                        // 관리자 API는 URL 레벨에서도 관리자 권한을 요구한다.
                        // SUPER_ADMIN은 관리자 권한 체계를 관리하는 상위 역할이므로 일반 관리자 API도 접근 가능하다.
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // 그 외(/api/auth/me 및 도메인 API)는 인증 필요
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> securityErrorResponseWriter.unauthorized(res))
                        .accessDeniedHandler((req, res, ex) -> securityErrorResponseWriter.forbidden(res)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(adminAccountStateFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 허용 오리진(패턴). 기본값은 Vite 개발 서버 + Capacitor 네이티브 WebView 오리진.
     * 개발 중 Vite 가 5173 외의 포트로 올라가거나, APK 테스트용 LAN IP 를 직접 호출해도
     * 브라우저 CORS 에 막히지 않도록 로컬/LAN 패턴을 기본 허용한다.
     *   - Android WebView: http(s)://localhost
     *   - iOS WebView    : capacitor://localhost
     * 배포/LAN 테스트는 CORS_ALLOWED_ORIGINS 로 교체(쉼표 구분, 예: http://192.168.*:*,https://app.example.com).
     * 패턴이므로 와일드카드(*)를 쓰면서도 allowCredentials=true 와 함께 동작한다.
     */
    @org.springframework.beans.factory.annotation.Value(
            "${careertuner.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*,http://192.168.*:*,http://localhost,https://localhost,capacitor://localhost}")
    private List<String> allowedOriginPatterns;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
