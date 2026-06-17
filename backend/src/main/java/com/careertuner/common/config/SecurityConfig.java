package com.careertuner.common.config;

import java.util.List;

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

import com.careertuner.common.security.JwtAuthenticationFilter;

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
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**", "/api-docs/**").permitAll()
                        // 인증 공개 엔드포인트
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/email/resend",
                                "/api/auth/password/reset-request", "/api/auth/password/reset",
                                "/api/auth/dormant/release-request", "/api/auth/dormant/release").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/auth/verify-email", "/api/auth/check/**", "/api/auth/oauth/**").permitAll()
                        // 커뮤니티 게시글 조회 공개
                        .requestMatchers(HttpMethod.GET,
                                "/api/community/posts", "/api/community/posts/**",
                                "/api/community/guidelines/published").permitAll()
                        // 고객센터 FAQ/공지사항 조회 공개
                        .requestMatchers(HttpMethod.GET,
                                "/api/support/faq", "/api/support/notices", "/api/support/notices/**").permitAll()
                        // 관리자 API는 URL 레벨에서도 ADMIN 권한을 요구한다.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 그 외(/api/auth/me, /api/auth/logout 및 도메인 API)는 인증 필요
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 허용 오리진(패턴). 기본값은 Vite 개발 서버 + Capacitor 네이티브 WebView 오리진.
     *   - Android WebView: http(s)://localhost
     *   - iOS WebView    : capacitor://localhost
     * 배포/LAN 테스트는 CORS_ALLOWED_ORIGINS 로 교체(쉼표 구분, 예: http://192.168.*:*,https://app.example.com).
     * 패턴이므로 와일드카드(*)를 쓰면서도 allowCredentials=true 와 함께 동작한다.
     */
    @org.springframework.beans.factory.annotation.Value(
            "${careertuner.cors.allowed-origins:http://localhost:5173,http://localhost,https://localhost,capacitor://localhost}")
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
