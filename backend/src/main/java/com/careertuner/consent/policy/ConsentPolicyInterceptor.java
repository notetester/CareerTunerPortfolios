package com.careertuner.consent.policy;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.service.ConsentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 필수 약관과 기능별 동의를 컨트롤러 진입 전에 일관되게 집행한다. */
@Component
@RequiredArgsConstructor
public class ConsentPolicyInterceptor implements HandlerInterceptor {

    private final ConsentService consentService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        AuthUser user = currentUser();
        if (user == null || isAdmin(user)) {
            return true;
        }

        if (!isRecoveryOrPublicRequest(request)
                && !consentService.hasRequiredConsents(user.id())) {
            throw new BusinessException(
                    ErrorCode.CONSENT_REQUIRED,
                    "서비스 이용약관 또는 개인정보 처리 동의가 철회되었습니다. 설정에서 다시 동의해 주세요.");
        }

        for (ConsentType type : requiredConsents(handlerMethod)) {
            if (!consentService.hasCurrentConsent(user.id(), type)) {
                throw new BusinessException(ErrorCode.CONSENT_REQUIRED, missingMessage(type));
            }
        }
        return true;
    }

    private Set<ConsentType> requiredConsents(HandlerMethod handlerMethod) {
        Set<ConsentType> required = new LinkedHashSet<>();
        RequiresConsent onType = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), RequiresConsent.class);
        RequiresConsent onMethod = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequiresConsent.class);
        if (onType != null) {
            required.addAll(java.util.List.of(onType.value()));
        }
        if (onMethod != null) {
            required.addAll(java.util.List.of(onMethod.value()));
        }
        return required;
    }

    private boolean isRecoveryOrPublicRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (uri.startsWith("/api/auth/")
                || uri.startsWith("/api/consents")
                || uri.startsWith("/api/legal/")
                || uri.startsWith("/api/health")
                || uri.startsWith("/api/support/")
                || uri.startsWith("/api/admin/")) {
            return true;
        }
        if (!"GET".equals(method)) {
            return false;
        }
        return uri.equals("/api/job-board")
                || uri.startsWith("/api/job-board/")
                || uri.equals("/api/ads")
                || uri.equals("/api/community/posts")
                || uri.startsWith("/api/community/posts/")
                || uri.equals("/api/community/guidelines/published")
                || uri.equals("/api/billing/plans")
                || uri.equals("/api/billing/credit-products")
                || uri.equals("/api/billing/feature-benefit-policies")
                || uri.equals("/api/credit-products");
    }

    private String missingMessage(ConsentType type) {
        return switch (type) {
            case AI_DATA -> "AI 데이터 이용 동의가 필요합니다. 설정에서 동의한 뒤 다시 실행해 주세요.";
            case RESUME_ANALYSIS -> "이력서 분석 개인정보 수집·이용 동의가 필요합니다. 설정에서 동의한 뒤 다시 실행해 주세요.";
            default -> type.label() + " 동의가 필요합니다. 설정에서 다시 동의해 주세요.";
        };
    }

    private AuthUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthUser user ? user : null;
    }

    private boolean isAdmin(AuthUser user) {
        return "ADMIN".equalsIgnoreCase(user.role()) || "SUPER_ADMIN".equalsIgnoreCase(user.role());
    }
}
