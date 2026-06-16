package com.careertuner.consent.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.auth.domain.UserConsent;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.dto.ConsentRequest;
import com.careertuner.consent.dto.ConsentStatusResponse;
import com.careertuner.consent.dto.ConsentView;
import com.careertuner.consent.mapper.ConsentMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsentServiceImpl implements ConsentService {

    private final ConsentMapper mapper;

    @Override
    public ConsentStatusResponse status(AuthUser authUser) {
        Long userId = requireUser(authUser);
        return build(userId);
    }

    @Override
    @Transactional
    public ConsentStatusResponse save(AuthUser authUser, ConsentRequest request, String source) {
        Long userId = requireUser(authUser);
        if (!request.termsAgreed() || !request.privacyAgreed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "필수 약관과 개인정보 처리방침 동의가 필요합니다.");
        }
        insert(userId, "TERMS", request.termsAgreed(), source);
        insert(userId, "PRIVACY", request.privacyAgreed(), source);
        insert(userId, "AI_DATA", request.aiDataAgreed(), source);
        insert(userId, "MARKETING", request.marketingAgreed(), source);
        return build(userId);
    }

    @Override
    @Transactional
    public ConsentStatusResponse revokeAi(AuthUser authUser) {
        Long userId = requireUser(authUser);
        insert(userId, "AI_DATA", false, "REVOKE");
        return build(userId);
    }

    @Override
    public boolean hasCurrentConsent(Long userId, String consentType) {
        ConsentView latest = mapper.findLatest(userId, consentType);
        return latest != null && latest.isAgreed() && latest.getRevokedAt() == null;
    }

    @Override
    public List<ConsentView> adminConsents(AuthUser authUser, String keyword, String consentType, int limit) {
        requireAdmin(authUser);
        return mapper.findAdminConsents(blankToNull(keyword), blankToNull(consentType), Math.max(1, Math.min(limit, 200)));
    }

    private ConsentStatusResponse build(Long userId) {
        List<ConsentView> history = mapper.findByUserId(userId);
        boolean terms = hasCurrentConsent(userId, "TERMS");
        boolean privacy = hasCurrentConsent(userId, "PRIVACY");
        boolean aiData = hasCurrentConsent(userId, "AI_DATA");
        boolean marketing = hasCurrentConsent(userId, "MARKETING");
        return new ConsentStatusResponse(terms, privacy, aiData, marketing, !terms || !privacy, history);
    }

    private void insert(Long userId, String type, boolean agreed, String source) {
        mapper.insert(UserConsent.builder()
                .userId(userId)
                .consentType(type)
                .agreed(agreed)
                .agreedAt(agreed ? LocalDateTime.now() : null)
                .revokedAt(agreed ? null : LocalDateTime.now())
                .source(source)
                .build());
    }

    private Long requireUser(AuthUser authUser) {
        if (authUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authUser.id();
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
