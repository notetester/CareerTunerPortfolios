package com.careertuner.billing.policy;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.careertuner.billing.service.AiChargeRequestSettlementService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 비용 고지 없는 플랫폼/API 호출이 AI 실행과 정산을 우회하지 못하게 컨트롤러 전에 차단한다. */
@Component
@RequiredArgsConstructor
public class AiChargePreflightInterceptor implements HandlerInterceptor {

    private final AiChargePreflightService preflightService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        RequiresAiCharge requirement = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequiresAiCharge.class);
        if (requirement == null) {
            requirement = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequiresAiCharge.class);
        }
        if (requirement == null) return true;

        AuthUser user = currentUser();
        if (user == null) return true; // 인증 경계가 최종 401을 담당한다.

        String feature = request.getHeader(AiChargeRequestSettlementService.FEATURE_HEADER);
        String acknowledgement = request.getHeader(
                AiChargeRequestSettlementService.ACKNOWLEDGEMENT_HEADER);
        if (!requirement.value().equals(feature)
                || acknowledgement == null || acknowledgement.isBlank()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "AI 사용 비용과 환불 정책을 확인한 뒤 다시 시도해 주세요.");
        }
        preflightService.requireAcknowledged(user.id(), requirement.value(), acknowledgement);
        return true;
    }

    private AuthUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthUser user
                ? user
                : null;
    }
}
