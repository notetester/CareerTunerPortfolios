package com.careertuner.sms;

import java.io.FileInputStream;
import java.io.InputStream;

import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;

import lombok.extern.slf4j.Slf4j;

/**
 * Firebase Phone Auth ID 토큰 검증 클라이언트.
 *
 * <p>Firebase 는 발송·인증을 프런트 SDK 가 직접 수행하고, 백엔드는 그 결과로 발급된 ID 토큰만 검증한다.
 * 서비스계정(JSON 파일 경로)이 설정됐을 때만 검증이 가능하며, 미설정/로드 실패 시 {@link #isReady()}=false 로
 * degrade 한다({@link FcmPushClient} 와 동일한 graceful 패턴). FCM 발송용 앱과 충돌하지 않도록 별도 이름의
 * {@link FirebaseApp} 을 초기화한다(같은 서비스계정이라도 앱 인스턴스만 분리).</p>
 */
@Slf4j
@Component
public class FirebaseAuthClient {

    private static final String APP_NAME = "careertuner-auth";

    /** 서비스계정이 정상 로드됐을 때만 non-null. */
    private final FirebaseAuth auth;

    public FirebaseAuthClient(SmsProperties properties) {
        FirebaseAuth ready = null;
        String path = properties.getFirebase().getServiceAccount();
        if (path != null && !path.isBlank()) {
            try (InputStream credentials = new FileInputStream(path)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentials))
                        .build();
                FirebaseApp app = FirebaseApp.getApps().stream()
                        .filter(a -> APP_NAME.equals(a.getName()))
                        .findFirst()
                        .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
                ready = FirebaseAuth.getInstance(app);
                log.info("[phone-auth] Firebase ID 토큰 검증기 활성화(service-account 로드 완료)");
            } catch (Exception ex) {
                log.warn("[phone-auth] Firebase 서비스계정 로드 실패 — Firebase 인증 비활성: {}", ex.getMessage());
            }
        }
        this.auth = ready;
    }

    /** 서비스계정이 설정·로드돼 토큰 검증이 가능한 상태인지. */
    public boolean isReady() {
        return auth != null;
    }

    /**
     * Firebase Phone Auth ID 토큰을 검증하고 인증된 전화번호(E.164)를 반환한다.
     *
     * @throws BusinessException 검증기 미설정·토큰 무효·전화번호 클레임 누락 시
     */
    public String verifyPhoneToken(String idToken) {
        if (auth == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Firebase 인증이 설정되어 있지 않습니다.");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "인증 토큰이 없습니다.");
        }
        FirebaseToken token;
        try {
            // checkRevoked=true: 콘솔에서 취소된 토큰을 재사용하지 못하게 한다.
            token = auth.verifyIdToken(idToken, true);
        } catch (FirebaseAuthException ex) {
            log.warn("[phone-auth] Firebase ID 토큰 검증 실패: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.INVALID_INPUT, "전화번호 인증에 실패했습니다. 다시 시도해 주세요.");
        }
        Object phone = token.getClaims().get("phone_number");
        if (phone == null || phone.toString().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "인증 토큰에 전화번호 정보가 없습니다.");
        }
        return phone.toString();
    }
}
