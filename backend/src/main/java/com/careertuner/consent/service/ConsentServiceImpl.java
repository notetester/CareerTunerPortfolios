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
import com.careertuner.consent.domain.ConsentType;
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
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "동의 설정을 입력해 주세요.");
        }
        recordIfChanged(userId, ConsentType.TERMS, request.termsAgreed(), source);
        recordIfChanged(userId, ConsentType.PRIVACY, request.privacyAgreed(), source);
        recordIfChanged(userId, ConsentType.AI_DATA, request.aiDataAgreed(), source);
        recordIfChanged(userId, ConsentType.RESUME_ANALYSIS, request.resumeAnalysisAgreed(), source);
        recordIfChanged(userId, ConsentType.MARKETING, request.marketingAgreed(), source);
        return build(userId);
    }

    @Override
    @Transactional
    public ConsentStatusResponse revokeAi(AuthUser authUser) {
        return revoke(authUser, ConsentType.AI_DATA);
    }

    @Override
    @Transactional
    public ConsentStatusResponse revoke(AuthUser authUser, ConsentType consentType) {
        Long userId = requireUser(authUser);
        if (consentType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "동의 유형을 입력해 주세요.");
        }
        recordIfChanged(userId, consentType, false, "REVOKE");
        return build(userId);
    }

    @Override
    public boolean hasCurrentConsent(Long userId, String consentType) {
        ConsentType type = ConsentType.from(consentType);
        ConsentView latest = mapper.findLatest(userId, type.name());
        return latest != null
                && latest.isAgreed()
                && latest.getRevokedAt() == null
                && type.currentVersion().equals(latest.getConsentVersion());
    }

    @Override
    public List<ConsentView> adminConsents(AuthUser authUser, String keyword, String consentType,
                                           String status, String source, String from, String to, int limit) {
        requireAdmin(authUser);
        return mapper.findAdminConsents(blankToNull(keyword), blankToNull(consentType), blankToNull(status),
                blankToNull(source), blankToNull(from), blankToNull(to), Math.max(1, Math.min(limit, 200)));
    }

    private ConsentStatusResponse build(Long userId) {
        List<ConsentView> history = mapper.findByUserId(userId);
        boolean terms = hasCurrentConsent(userId, ConsentType.TERMS);
        boolean privacy = hasCurrentConsent(userId, ConsentType.PRIVACY);
        boolean aiData = hasCurrentConsent(userId, ConsentType.AI_DATA);
        boolean resumeAnalysis = hasCurrentConsent(userId, ConsentType.RESUME_ANALYSIS);
        boolean marketing = hasCurrentConsent(userId, ConsentType.MARKETING);
        return new ConsentStatusResponse(
                terms, privacy, aiData, resumeAnalysis, marketing, !terms || !privacy, history);
    }

    private void recordIfChanged(Long userId, ConsentType type, boolean agreed, String source) {
        ConsentView latest = mapper.findLatest(userId, type.name());
        boolean current = latest != null && latest.isAgreed() && latest.getRevokedAt() == null;
        boolean currentVersion = latest != null && type.currentVersion().equals(latest.getConsentVersion());
        if (latest != null && current == agreed && (!agreed || currentVersion)) {
            return;
        }
        insert(userId, type, agreed, source);
    }

    private void insert(Long userId, ConsentType type, boolean agreed, String source) {
        mapper.insert(UserConsent.builder()
                .userId(userId)
                .consentType(type.name())
                .consentVersion(type.currentVersion())
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
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
