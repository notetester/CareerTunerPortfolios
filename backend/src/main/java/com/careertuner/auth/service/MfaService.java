package com.careertuner.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.activitylog.service.SecurityHistoryService;
import com.careertuner.auth.domain.MfaBackupCode;
import com.careertuner.auth.domain.MfaChallenge;
import com.careertuner.auth.domain.MfaPolicy;
import com.careertuner.auth.domain.UserMfaSetting;
import com.careertuner.auth.dto.LoginRequestContext;
import com.careertuner.auth.dto.LoginResponse;
import com.careertuner.auth.dto.MfaApprovalRequest;
import com.careertuner.auth.dto.MfaBackupCodesResponse;
import com.careertuner.auth.dto.MfaChallengeResponse;
import com.careertuner.auth.dto.MfaDisableRequest;
import com.careertuner.auth.dto.MfaPolicyResponse;
import com.careertuner.auth.dto.MfaPolicyUpdateRequest;
import com.careertuner.auth.dto.MfaSetupStartResponse;
import com.careertuner.auth.dto.MfaStatusResponse;
import com.careertuner.auth.mapper.MfaMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MfaService {
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int BACKUP_CODE_COUNT = 8;

    private final MfaMapper mfaMapper;
    private final UserMapper userMapper;
    private final TotpService totpService;
    private final MfaSecretCipher secretCipher;
    private final PasswordEncoder passwordEncoder;
    private final SecurityHistoryService securityHistoryService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public MfaStatusResponse status(AuthUser authUser) {
        User user = requireUser(authUser.id());
        UserMfaSetting setting = mfaMapper.findSettingByUserId(user.getId());
        MfaPolicy policy = policy();
        return new MfaStatusResponse(
                setting != null && setting.isEnabled(),
                setting != null && setting.isVerified(),
                setting != null ? setting.getMfaType() : "TOTP",
                setting != null ? setting.getDeviceName() : null,
                setting == null || setting.isPushEnabled(),
                mfaMapper.countUnusedBackupCodes(user.getId()),
                policy.isRequireAdmins() && isAdmin(user)
        );
    }

    @Transactional
    public MfaSetupStartResponse startSetup(AuthUser authUser, String deviceName) {
        User user = requireUser(authUser.id());
        String secret = totpService.newSecret();
        String normalizedDeviceName = normalizeDeviceName(deviceName);
        mfaMapper.upsertSetting(UserMfaSetting.builder()
                .userId(user.getId())
                .enabled(false)
                .verified(false)
                .mfaType("TOTP")
                .secretKeyEncrypted(secretCipher.encrypt(secret))
                .deviceName(normalizedDeviceName)
                .pushEnabled(true)
                .build());
        securityHistoryService.record("MFA_SETUP", "START", user.getId(), true, user.getEmail(), null);
        return new MfaSetupStartResponse(secret, otpauthUri(user, secret), normalizedDeviceName);
    }

    @Transactional
    public MfaBackupCodesResponse verifySetup(AuthUser authUser, String code) {
        User user = requireUser(authUser.id());
        UserMfaSetting setting = requireSetting(user.getId());
        if (!totpService.verify(secretCipher.decrypt(setting.getSecretKeyEncrypted()), code)) {
            securityHistoryService.record("MFA_SETUP", "VERIFY", user.getId(), false, user.getEmail(), "INVALID_TOTP");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증 코드가 올바르지 않습니다.");
        }
        mfaMapper.enableSetting(user.getId());
        List<String> backupCodes = regenerateBackupCodesInternal(user.getId());
        securityHistoryService.record("MFA_SETUP", "COMPLETE", user.getId(), true, user.getEmail(), null);
        return new MfaBackupCodesResponse(backupCodes);
    }

    @Transactional
    public void disable(AuthUser authUser, MfaDisableRequest request) {
        User user = requireUser(authUser.id());
        UserMfaSetting setting = requireEnabledSetting(user.getId());
        boolean validTotp = request.code() != null
                && totpService.verify(secretCipher.decrypt(setting.getSecretKeyEncrypted()), request.code());
        boolean validBackup = request.backupCode() != null && consumeBackupCode(user.getId(), request.backupCode());
        if (!validTotp && !validBackup) {
            securityHistoryService.record("MFA_DISABLE", "COMPLETE", user.getId(), false, user.getEmail(), "INVALID_MFA");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "2단계 인증 해제 코드가 올바르지 않습니다.");
        }
        mfaMapper.disableSetting(user.getId());
        mfaMapper.deleteBackupCodes(user.getId());
        securityHistoryService.record("MFA_DISABLE", "COMPLETE", user.getId(), true, user.getEmail(), null);
    }

    @Transactional
    public MfaBackupCodesResponse regenerateBackupCodes(AuthUser authUser) {
        User user = requireUser(authUser.id());
        requireEnabledSetting(user.getId());
        List<String> codes = regenerateBackupCodesInternal(user.getId());
        securityHistoryService.record("MFA_BACKUP_CODE", "REGENERATE", user.getId(), true, user.getEmail(), null);
        return new MfaBackupCodesResponse(codes);
    }

    @Transactional
    public LoginResponse beginLoginIfRequired(User user, LoginRequestContext context) {
        UserMfaSetting setting = mfaMapper.findSettingByUserId(user.getId());
        if (setting == null || !setting.isEnabled() || !setting.isVerified()) {
            return null;
        }
        mfaMapper.expireOldChallenges(LocalDateTime.now());
        String token = randomToken(32);
        mfaMapper.insertChallenge(MfaChallenge.builder()
                .userId(user.getId())
                .challengeToken(token)
                .challengeType("LOGIN")
                .deliveryType(policy().isAllowPushApproval() && setting.isPushEnabled() ? "TOTP_OR_PUSH" : "TOTP")
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plus(CHALLENGE_TTL))
                .ipAddress(context != null ? truncate(context.ipAddress(), 45) : null)
                .userAgent(context != null ? truncate(context.userAgent(), 500) : null)
                .build());
        securityHistoryService.record("MFA_LOGIN", "REQUIRED", user.getId(), true, user.getEmail(), null);
        return LoginResponse.mfaRequired(token, policy().isAllowPushApproval() ? "TOTP_OR_PUSH" : "TOTP", CHALLENGE_TTL.toSeconds());
    }

    @Transactional
    public User verifyLoginChallenge(String challengeToken, String code, String backupCode, boolean useApprovedChallenge) {
        MfaChallenge challenge = requireActiveChallenge(challengeToken);
        User user = requireUser(challenge.getUserId());
        UserMfaSetting setting = requireEnabledSetting(user.getId());

        boolean verified = false;
        String reason = null;
        if (useApprovedChallenge) {
            verified = "APPROVED".equals(challenge.getStatus());
            reason = verified ? null : "PUSH_NOT_APPROVED";
        } else if (code != null && !code.isBlank()) {
            verified = totpService.verify(secretCipher.decrypt(setting.getSecretKeyEncrypted()), code);
            reason = verified ? null : "INVALID_TOTP";
        } else if (backupCode != null && !backupCode.isBlank() && policy().isAllowBackupCode()) {
            verified = consumeBackupCode(user.getId(), backupCode);
            reason = verified ? null : "INVALID_BACKUP_CODE";
        } else {
            reason = "MFA_CODE_REQUIRED";
        }

        if (!verified) {
            securityHistoryService.record("MFA_LOGIN", "VERIFY", user.getId(), false, user.getEmail(), reason);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "2단계 인증에 실패했습니다.");
        }
        mfaMapper.markChallengeVerified(challengeToken);
        mfaMapper.touchSettingUsed(user.getId());
        securityHistoryService.record("MFA_LOGIN", "VERIFY", user.getId(), true, user.getEmail(), null);
        return user;
    }

    @Transactional(readOnly = true)
    public MfaChallenge findChallenge(String challengeToken) {
        return mfaMapper.findChallengeByToken(challengeToken);
    }

    @Transactional(readOnly = true)
    public List<MfaChallengeResponse> pendingPushChallenges(AuthUser authUser) {
        requireEnabledSetting(authUser.id());
        return mfaMapper.findPendingPushChallenges(authUser.id()).stream()
                .map(this::toChallengeResponse)
                .toList();
    }

    @Transactional
    public void approvePushChallenge(AuthUser authUser, MfaApprovalRequest request) {
        MfaChallenge challenge = requireActiveChallenge(request.challengeToken());
        if (!challenge.getUserId().equals(authUser.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 로그인 요청만 승인할 수 있습니다.");
        }
        if (Boolean.FALSE.equals(request.approve())) {
            mfaMapper.markChallengeDenied(request.challengeToken(), normalizeDeviceName(request.deviceName()));
            securityHistoryService.record("MFA_PUSH", "DENY", authUser.id(), true, null, null);
            return;
        }
        mfaMapper.markChallengeApproved(request.challengeToken(), normalizeDeviceName(request.deviceName()));
        securityHistoryService.record("MFA_PUSH", "APPROVE", authUser.id(), true, null, null);
    }

    @Transactional(readOnly = true)
    public MfaPolicyResponse policyResponse() {
        MfaPolicy policy = policy();
        return new MfaPolicyResponse(policy.isRequireAdmins(), policy.isAllowBackupCode(), policy.isAllowPushApproval());
    }

    @Transactional
    public MfaPolicyResponse updatePolicy(AuthUser authUser, MfaPolicyUpdateRequest request) {
        if (!"SUPER_ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "최고 관리자만 MFA 정책을 변경할 수 있습니다.");
        }
        MfaPolicy current = policy();
        MfaPolicy next = MfaPolicy.builder()
                .id(1L)
                .requireAdmins(request.requireAdmins() != null ? request.requireAdmins() : current.isRequireAdmins())
                .allowBackupCode(request.allowBackupCode() != null ? request.allowBackupCode() : current.isAllowBackupCode())
                .allowPushApproval(request.allowPushApproval() != null ? request.allowPushApproval() : current.isAllowPushApproval())
                .updatedBy(authUser.id())
                .build();
        mfaMapper.upsertPolicy(next);
        securityHistoryService.record("MFA_POLICY", "UPDATE", authUser.id(), true, null, null);
        return policyResponse();
    }

    private List<String> regenerateBackupCodesInternal(Long userId) {
        mfaMapper.deleteBackupCodes(userId);
        List<String> codes = java.util.stream.IntStream.range(0, BACKUP_CODE_COUNT)
                .mapToObj(i -> randomBackupCode())
                .toList();
        for (String code : codes) {
            mfaMapper.insertBackupCode(MfaBackupCode.builder()
                    .userId(userId)
                    .codeHash(passwordEncoder.encode(code))
                    .build());
        }
        return codes;
    }

    private boolean consumeBackupCode(Long userId, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        for (MfaBackupCode backup : mfaMapper.findUnusedBackupCodes(userId)) {
            if (passwordEncoder.matches(normalized, backup.getCodeHash())) {
                mfaMapper.markBackupCodeUsed(backup.getId());
                return true;
            }
        }
        return false;
    }

    private MfaChallenge requireActiveChallenge(String token) {
        MfaChallenge challenge = mfaMapper.findChallengeByToken(token);
        if (challenge == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "MFA 요청을 찾을 수 없습니다.");
        }
        if ("EXPIRED".equals(challenge.getStatus()) || challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            mfaMapper.expireOldChallenges(LocalDateTime.now());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "MFA 요청이 만료되었습니다.");
        }
        if ("DENIED".equals(challenge.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "모바일 앱에서 로그인을 거절했습니다.");
        }
        if (!"PENDING".equals(challenge.getStatus()) && !"APPROVED".equals(challenge.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "이미 처리된 MFA 요청입니다.");
        }
        return challenge;
    }

    private UserMfaSetting requireSetting(Long userId) {
        UserMfaSetting setting = mfaMapper.findSettingByUserId(userId);
        if (setting == null || setting.getSecretKeyEncrypted() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "MFA 설정을 먼저 시작해 주세요.");
        }
        return setting;
    }

    private UserMfaSetting requireEnabledSetting(Long userId) {
        UserMfaSetting setting = requireSetting(userId);
        if (!setting.isEnabled() || !setting.isVerified()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "MFA가 활성화되어 있지 않습니다.");
        }
        return setting;
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return user;
    }

    private MfaPolicy policy() {
        MfaPolicy policy = mfaMapper.findPolicy();
        if (policy != null) {
            return policy;
        }
        MfaPolicy fallback = MfaPolicy.builder()
                .id(1L)
                .requireAdmins(false)
                .allowBackupCode(true)
                .allowPushApproval(true)
                .build();
        mfaMapper.upsertPolicy(fallback);
        return fallback;
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole());
    }

    private MfaChallengeResponse toChallengeResponse(MfaChallenge challenge) {
        return new MfaChallengeResponse(
                challenge.getChallengeToken(),
                challenge.getStatus(),
                challenge.getIpAddress(),
                challenge.getUserAgent(),
                challenge.getDeviceName(),
                challenge.getExpiresAt(),
                challenge.getCreatedAt()
        );
    }

    private String otpauthUri(User user, String secret) {
        String label = enc("CareerTuner:" + (user.getEmail() != null ? user.getEmail() : user.getLoginId()));
        return "otpauth://totp/" + label + "?secret=" + enc(secret) + "&issuer=CareerTuner&digits=6&period=30";
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private String randomBackupCode() {
        byte[] value = new byte[4];
        secureRandom.nextBytes(value);
        String hex = HexFormat.of().formatHex(value).toUpperCase();
        return hex.substring(0, 4) + "-" + hex.substring(4, 8);
    }

    private String normalizeDeviceName(String deviceName) {
        String normalized = deviceName == null || deviceName.isBlank() ? "내 인증 기기" : deviceName.trim();
        return truncate(normalized, 120);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
