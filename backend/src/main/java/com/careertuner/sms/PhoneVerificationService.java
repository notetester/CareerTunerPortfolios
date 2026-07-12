package com.careertuner.sms;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 전화번호 SMS OTP 인증 서비스.
 *
 * <p>실 제공자 키가 있으면 실 발송, 없으면 {@link MockSmsProvider} 로 발송해 데모를 완결한다.
 * Mock 발송일 때만 결과의 {@code devCode} 로 코드를 노출해 프런트가 자동입력할 수 있게 한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsProperties properties;
    private final SmsProviderRouter providerRouter;
    private final SmsOtpMapper smsOtpMapper;
    private final UserMapper userMapper;
    private final FirebaseAuthClient firebaseAuthClient;
    private final com.careertuner.activitylog.service.SecurityHistoryService securityHistoryService;

    /** 전화번호 인증 코드를 발송한다. Mock 이면 devCode 를 함께 반환한다. */
    @Transactional
    public OtpRequestResult requestOtp(Long userId, String rawPhone) {
        String phone = normalizePhone(rawPhone);

        // 쿨다운 — 직전 발송 후 cooldownSeconds 이내 재요청 차단.
        LocalDateTime lastIssued = smsOtpMapper.findLastIssuedAt(userId, phone);
        if (lastIssued != null) {
            long elapsed = Duration.between(lastIssued, LocalDateTime.now()).getSeconds();
            long remain = properties.getCooldownSeconds() - elapsed;
            if (remain > 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "잠시 후 다시 시도해 주세요. (" + remain + "초 남음)");
            }
        }

        String code = generateCode();
        SmsOtpCode otp = SmsOtpCode.builder()
                .userId(userId)
                .phone(phone)
                .code(code)
                .attemptCount(0)
                .maxAttempts(properties.getMaxAttempts())
                .expiresAt(LocalDateTime.now().plusSeconds(properties.getOtpValiditySeconds()))
                .build();
        smsOtpMapper.insert(otp);

        SmsProvider provider = providerRouter.resolve();
        SmsSendResult sendResult = provider.send(phone, code);
        if (!sendResult.success()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "인증 문자 발송에 실패했습니다.");
        }

        securityHistoryService.record("PHONE_VERIFY", "ISSUE", userId, true, phone, null);

        // 실 발송이면 devCode 미포함, Mock(데모)이면 코드 노출.
        String devCode = sendResult.realSending() ? null : code;
        return new OtpRequestResult(
                true,
                sendResult.provider(),
                sendResult.realSending(),
                devCode,
                properties.getOtpValiditySeconds(),
                properties.getCooldownSeconds());
    }

    /**
     * 입력한 코드를 검증한다. 성공 시 users.phone / phone_verified 를 갱신한다.
     *
     * <p>{@code noRollbackFor = BusinessException.class}: 코드 불일치 시 {@code increaseAttempt} 로 올린
     * 시도 횟수가 직후 던지는 {@link BusinessException}(RuntimeException) 의 기본 롤백에 휩쓸려
     * 0 으로 되돌아가면 max-attempts 게이트가 영원히 열려 브루트포스가 가능해진다. 실패에도
     * 시도 횟수 증가만은 커밋되도록 해당 예외를 롤백 제외로 둔다(성공 경로 쓰기의 원자성은 유지).</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public OtpVerifyResult verifyOtp(Long userId, String rawPhone, String inputCode) {
        String phone = normalizePhone(rawPhone);
        String code = inputCode == null ? "" : inputCode.trim();
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "인증번호를 입력해 주세요.");
        }

        SmsOtpCode otp = smsOtpMapper.findLatestActive(userId, phone);
        if (otp == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "유효한 인증번호가 없습니다. 인증번호를 다시 요청해 주세요.");
        }
        if (otp.getAttemptCount() >= otp.getMaxAttempts()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요.");
        }

        smsOtpMapper.increaseAttempt(otp.getId());
        if (!otp.getCode().equals(code)) {
            int remain = otp.getMaxAttempts() - (otp.getAttemptCount() + 1);
            if (remain <= 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요.");
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "인증번호가 일치하지 않습니다. (남은 시도 " + remain + "회)");
        }

        // 전화번호 저장을 먼저 시도한다. UNIQUE 충돌 시(다른 계정 점유) 여기서 중단되어야
        // OTP 를 verified 로 소진하지 않는다(noRollbackFor 로 markVerified 가 커밋돼 버리는 것 방지).
        try {
            userMapper.markPhoneVerified(userId, phone);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 다른 계정에서 사용 중인 전화번호입니다.");
        }
        smsOtpMapper.markVerified(otp.getId());
        securityHistoryService.record("PHONE_VERIFY", "COMPLETE", userId, true, phone, null);
        return new OtpVerifyResult(true, phone);
    }

    /**
     * Firebase Phone Auth 로 발급된 ID 토큰을 검증하고 전화번호 인증을 완료한다.
     *
     * <p>발송·코드검증은 프런트 Firebase SDK 가 끝낸 상태다. 여기서는 토큰의 진위와 전화번호 클레임만
     * 확인하고, 검증된 번호를 국내 형식으로 맞춰 {@code users.phone / phone_verified} 를 갱신한다.
     * OTP 테이블은 사용하지 않는다.</p>
     */
    @Transactional
    public OtpVerifyResult verifyFirebase(Long userId, String idToken) {
        String e164 = firebaseAuthClient.verifyPhoneToken(idToken);
        String phone = normalizePhone(e164ToLocal(e164));
        try {
            userMapper.markPhoneVerified(userId, phone);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 다른 계정에서 사용 중인 전화번호입니다.");
        }
        securityHistoryService.record("PHONE_VERIFY", "COMPLETE", userId, true, phone, null);
        return new OtpVerifyResult(true, phone);
    }

    /** 프런트에 내려보낼 전화번호 인증 설정(provider + Firebase 웹 config). 서비스계정은 절대 포함하지 않는다. */
    public PhoneAuthConfigResult authConfig() {
        String provider = properties.normalizedProvider();
        if ("firebase".equals(provider) && properties.getFirebase().webConfigured()) {
            SmsProperties.Firebase fb = properties.getFirebase();
            return new PhoneAuthConfigResult(provider, new FirebaseWebConfig(
                    fb.getApiKey(), fb.getAuthDomain(), fb.getProjectId(),
                    fb.getAppId(), fb.getMessagingSenderId()));
        }
        // firebase 가 아니거나 웹 config 미완비 → 기존 OTP 흐름(백엔드 발송/검증)으로 취급한다.
        return new PhoneAuthConfigResult("otp", null);
    }

    /** 현재 사용자의 전화번호 인증 상태. */
    public PhoneStatusResult status(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return new PhoneStatusResult(user.getPhone(), user.isPhoneVerified());
    }

    private static String generateCode() {
        int value = RANDOM.nextInt(1_000_000); // 0 ~ 999999
        return String.format("%06d", value);
    }

    /** E.164(+8210…) 를 국내 저장 형식(0…)으로 맞춘다. 기존 OTP 저장값과 형식을 통일하기 위함. */
    private static String e164ToLocal(String e164) {
        String digits = e164 == null ? "" : e164.replaceAll("[^0-9]", "");
        if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }

    /** 숫자만 남긴다(하이픈·공백 제거). 40자 컬럼을 넘지 않게 자른다. */
    private static String normalizePhone(String phone) {
        if (phone == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "전화번호를 입력해 주세요.");
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 9 || digits.length() > 20) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "올바른 전화번호를 입력해 주세요.");
        }
        return digits;
    }

    // ── 결과 DTO ──

    public record OtpRequestResult(
            boolean sent,
            String provider,
            boolean realSending,
            String devCode,
            int validitySeconds,
            int cooldownSeconds) {
    }

    public record OtpVerifyResult(boolean verified, String phone) {
    }

    public record PhoneStatusResult(String phone, boolean phoneVerified) {
    }

    /** 프런트 인증 흐름 선택용 설정. provider="firebase" 이고 firebase 웹 config 완비 시 firebase 흐름을 탄다. */
    public record PhoneAuthConfigResult(String provider, FirebaseWebConfig firebase) {
    }

    /** Firebase 웹 SDK 초기화용 공개 config(비밀 없음). */
    public record FirebaseWebConfig(
            String apiKey,
            String authDomain,
            String projectId,
            String appId,
            String messagingSenderId) {
    }
}
