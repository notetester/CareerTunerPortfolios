package com.careertuner.common.web;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** Codex Sites 요청이 금융 상태를 변경하지 못하도록 컨트롤러 실행 직전에 차단한다. */
@Component
@RequiredArgsConstructor
public class SitesFinancialMutationInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final FrontendReturnUrlResolver frontendReturnUrlResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || isSafeMethod(request.getMethod())
                || !handlerMethod.hasMethodAnnotation(SitesFinancialMutation.class)) {
            return true;
        }

        FrontendReturnTarget target = frontendReturnUrlResolver.resolve(request);
        if (FrontendReturnUrlResolver.SITES_CLIENT.equals(target.client())) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN, "Sites 백업에서는 금융 거래 기능을 이용할 수 없습니다.");
        }
        return true;
    }

    private static boolean isSafeMethod(String method) {
        return method != null && SAFE_METHODS.contains(method);
    }
}
