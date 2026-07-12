package com.careertuner.user.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.careertuner.auth.domain.EmailVerification;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.auth.service.EmailService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.web.FrontendReturnTarget;
import com.careertuner.common.web.FrontendReturnUrlResolver;
import com.careertuner.user.domain.User;
import com.careertuner.user.domain.UserResumeDetail;
import com.careertuner.user.dto.AccountInfoResponse;
import com.careertuner.user.dto.UserResumeDetailRequest;
import com.careertuner.user.dto.UserResumeDetailResponse;
import com.careertuner.user.mapper.UserAccountMapper;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 계정 확충 + 이력서 상세 스펙 서비스.
 *
 * <p>로그인 아이디는 최초 1회만 설정(변경 불가), 전화번호/아이디는 전역 UNIQUE.
 * 이력서 상세는 user_profile 저장 패턴과 동일하게 JSON 직렬화 후 upsert 한다.</p>
 */
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountMapper mapper;
    private final ObjectMapper objectMapper;
    private final AuthMapper authMapper;
    private final EmailService emailService;
    private final FrontendReturnUrlResolver frontendReturnUrlResolver;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public AccountInfoResponse accountInfo(Long userId) {
        return toAccountInfo(requireUser(userId));
    }

    @Override
    @Transactional
    public AccountInfoResponse setLoginId(Long userId, String loginId) {
        User user = requireUser(userId);
        if (user.getLoginId() != null && !user.getLoginId().isBlank()) {
            throw new BusinessException(ErrorCode.CONFLICT, "로그인 아이디는 이미 설정되어 변경할 수 없습니다.");
        }
        String normalized = loginId == null ? "" : loginId.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디를 입력해 주세요.");
        }
        if (mapper.countByLoginId(normalized) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        try {
            // login_id IS NULL 인 경우에만 반영 — 경합 시 0행이면 이미 설정됨
            if (mapper.setLoginIdIfAbsent(userId, normalized) == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "로그인 아이디는 이미 설정되어 변경할 수 없습니다.");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        return toAccountInfo(requireUser(userId));
    }

    @Override
    @Transactional
    public AccountInfoResponse setPhone(Long userId, String phone) {
        requireUser(userId);
        String normalized = normalizePhone(phone);
        if (mapper.countByPhone(normalized, userId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 등록된 전화번호입니다.");
        }
        try {
            if (mapper.updatePhone(userId, normalized) != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "계정 상태가 변경되어 전화번호를 저장하지 못했습니다.");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 등록된 전화번호입니다.");
        }
        return toAccountInfo(requireUser(userId));
    }

    @Override
    @Transactional
    public void requestEmailRegistration(Long userId, String email) {
        requestEmailRegistration(userId, email, frontendReturnUrlResolver.primary());
    }

    @Override
    @Transactional
    public void requestEmailRegistration(Long userId, String email, FrontendReturnTarget returnTarget) {
        User user = requireUser(userId);
        String normalized = normalizeEmail(email);
        if (mapper.countByEmailExcludingUser(normalized, userId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 다른 계정에서 사용 중인 이메일입니다.");
        }
        if (normalized.equalsIgnoreCase(user.getEmail()) && user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 인증된 이메일입니다.");
        }

        String purpose = normalized.equalsIgnoreCase(user.getEmail()) ? "VERIFY" : "EMAIL_CHANGE";
        EmailVerification verification = EmailVerification.builder()
                .userId(userId)
                .email(normalized)
                .token(UUID.randomUUID().toString())
                .purpose(purpose)
                .frontendClient(returnTarget.client())
                .expiredAt(LocalDateTime.now().plusHours(24))
                .build();
        authMapper.insertEmailVerification(verification);
        emailService.sendVerificationEmail(normalized, verification.getToken(), returnTarget);
    }

    @Override
    @Transactional
    public AccountInfoResponse unlinkSocial(Long userId, String provider) {
        User user = mapper.findByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        String normalizedProvider = normalizeProvider(provider);
        var linkedProviders = mapper.findLinkedProviders(userId);
        boolean removingExisting = linkedProviders.stream()
                .anyMatch(p -> normalizedProvider.equalsIgnoreCase(p));
        if (!removingExisting) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "연결된 소셜 계정이 없습니다.");
        }
        boolean remainingSocial = linkedProviders.stream()
                .anyMatch(p -> !normalizedProvider.equalsIgnoreCase(p));
        if (!remainingSocial && !hasUsableLocalLogin(user)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "연동 해제 후 사용할 수 있는 로그인 수단이 남아 있지 않습니다. 먼저 아이디/이메일 로그인 또는 다른 소셜 계정을 추가해 주세요.");
        }
        mapper.deleteSocial(userId, normalizedProvider);
        return toAccountInfo(requireUser(userId));
    }

    @Override
    @Transactional
    public void deleteOwnAccount(Long userId, String password, String confirmation) {
        User user = mapper.findByIdForUpdate(userId);
        if (user == null || "DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        if (!"회원탈퇴".equals(confirmation == null ? "" : confirmation.trim())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "확인 문구로 회원탈퇴를 입력해 주세요.");
        }
        if (user.isPasswordEnabled()
                && (password == null || password.isBlank()
                || !passwordEncoder.matches(password, user.getPassword()))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다.");
        }
        // 잠근 users 행을 먼저 DELETED+tombstone 으로 전환한다. 이후 단계가 하나라도 실패하면
        // @Transactional 경계에서 전부 rollback되어 부분 탈퇴 상태가 남지 않는다.
        if (mapper.anonymizeAndSoftDeleteOwnAccount(userId) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 탈퇴했거나 계정 상태가 변경되었습니다.");
        }
        authMapper.revokeAllForUser(userId);
        mapper.deleteAllSocialLinks(userId);
        mapper.deleteAllPushSubscriptions(userId);
        mapper.expireAllEmailVerifications(userId);
        mapper.deleteAllSmsOtpCodes(userId);
        mapper.hideAndAnonymizeNicknameProfiles(userId);
        mapper.anonymizeChatProfiles(userId);
        mapper.clearConversationNicknameProfiles(userId);
        mapper.deactivateConversationMemberships(userId);
        mapper.cancelPendingFriendRequests(userId);
        mapper.cancelPendingConversationInvites(userId);
        mapper.deleteDesktopPresence(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResumeDetailResponse getResumeDetail(Long userId) {
        UserResumeDetail detail = mapper.findResumeDetail(userId);
        return toResumeResponse(userId, detail);
    }

    @Override
    @Transactional
    public UserResumeDetailResponse saveResumeDetail(Long userId, UserResumeDetailRequest request) {
        UserResumeDetail detail = UserResumeDetail.builder()
                .userId(userId)
                .educationJson(json(request.education()))
                .careerJson(json(request.career()))
                .certificateJson(json(request.certificates()))
                .languageJson(json(request.languages()))
                .awardJson(json(request.awards()))
                .activityJson(json(request.activities()))
                .skillJson(json(request.skills()))
                .portfolioJson(json(request.portfolios()))
                .desiredConditionJson(json(request.desiredCondition()))
                .build();
        mapper.upsertResumeDetail(detail);
        return toResumeResponse(userId, mapper.findResumeDetail(userId));
    }

    // ── 매핑 ──

    private AccountInfoResponse toAccountInfo(User user) {
        var providers = mapper.findLinkedProviders(user.getId());
        boolean temporaryEmail = isTemporaryEmail(user.getEmail());
        return new AccountInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getLoginId(),
                user.getLoginId() != null && !user.getLoginId().isBlank(),
                user.getPhone(),
                user.isPhoneVerified(),
                user.isEmailVerified(),
                temporaryEmail,
                temporaryEmail || !user.isEmailVerified(),
                user.isPasswordEnabled(),
                !user.isPasswordEnabled(),
                providers);
    }

    private UserResumeDetailResponse toResumeResponse(Long userId, UserResumeDetail detail) {
        if (detail == null) {
            return new UserResumeDetailResponse(userId, null, null, null, null, null, null, null, null, null, null);
        }
        return new UserResumeDetailResponse(
                userId,
                object(detail.getEducationJson()),
                object(detail.getCareerJson()),
                object(detail.getCertificateJson()),
                object(detail.getLanguageJson()),
                object(detail.getAwardJson()),
                object(detail.getActivityJson()),
                object(detail.getSkillJson()),
                object(detail.getPortfolioJson()),
                object(detail.getDesiredConditionJson()),
                detail.getUpdatedAt());
    }

    private User requireUser(Long userId) {
        User user = mapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        return user;
    }

    private String normalizePhone(String phone) {
        String trimmed = phone == null ? "" : phone.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "전화번호를 입력해 주세요.");
        }
        // 하이픈 제거 후 재포맷(01012345678 → 010-1234-5678)로 통일 저장
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() == 11) {
            return "%s-%s-%s".formatted(digits.substring(0, 3), digits.substring(3, 7), digits.substring(7));
        }
        if (digits.length() == 10) {
            return "%s-%s-%s".formatted(digits.substring(0, 3), digits.substring(3, 6), digits.substring(6));
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "휴대폰 번호 형식이 올바르지 않습니다.");
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일을 입력해 주세요.");
        }
        return normalized;
    }

    private String normalizeProvider(String provider) {
        String normalized = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("KAKAO") && !normalized.equals("NAVER") && !normalized.equals("GOOGLE")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 소셜 제공자입니다.");
        }
        return normalized;
    }

    private boolean hasUsableLocalLogin(User user) {
        return user.isPasswordEnabled()
                && ((user.getLoginId() != null && !user.getLoginId().isBlank())
                || (!isTemporaryEmail(user.getEmail()) && user.isEmailVerified()));
    }

    private boolean isTemporaryEmail(String email) {
        return email == null || email.isBlank() || email.toLowerCase(Locale.ROOT).endsWith("@social.careertuner");
    }

    private Object object(String jsonValue) {
        if (jsonValue == null || jsonValue.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonValue, Object.class);
        } catch (Exception e) {
            return jsonValue;
        }
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이력서 상세 JSON 형식이 올바르지 않습니다.");
        }
    }
}
