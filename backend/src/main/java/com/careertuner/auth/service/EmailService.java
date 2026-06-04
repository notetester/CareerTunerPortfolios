package com.careertuner.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인증 메일 발송. SMTP 미설정(개발) 시 실제 발송 대신 링크를 로그로 출력한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final CareerTunerProperties props;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    /** 이메일 인증 링크 발송. 링크는 백엔드 verify 엔드포인트를 가리키고, 검증 후 프런트로 리다이렉트된다. */
    public void sendVerificationEmail(String to, String token) {
        String link = props.getApp().getApiBaseUrl() + "/api/auth/verify-email?token=" + token;
        send(to, "[CareerTuner] 이메일 인증을 완료해 주세요", verifyHtml(link), link);
    }

    /** 비밀번호 재설정 링크 발송(프런트 재설정 페이지로 이동). */
    public void sendPasswordResetEmail(String to, String token) {
        String link = props.getApp().getFrontendUrl() + "/auth/reset-password?token=" + token;
        send(to, "[CareerTuner] 비밀번호 재설정 안내", resetHtml(link), link);
    }

    private void send(String to, String subject, String html, String linkForDevLog) {
        boolean smtpUnset = smtpUsername == null || smtpUsername.isBlank();
        if (props.getMail().isDevMode() || smtpUnset) {
            log.info("[DEV-MAIL] (devMode={}, smtpUnset={}) → 발송 생략. 대상={}, 링크={}",
                    props.getMail().isDevMode(), smtpUnset, to, linkForDevLog);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(props.getMail().getFrom(), props.getMail().getSenderName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("메일 발송 완료: {} ({})", to, subject);
        } catch (Exception e) {
            log.error("메일 발송 실패: {}", to, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "이메일 발송에 실패했습니다.");
        }
    }

    private String verifyHtml(String link) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:auto">
                  <h2>CareerTuner 이메일 인증</h2>
                  <p>아래 버튼을 눌러 이메일 인증을 완료해 주세요. 링크는 24시간 동안 유효합니다.</p>
                  <p><a href="%s" style="display:inline-block;padding:12px 20px;background:#030213;color:#fff;border-radius:8px;text-decoration:none">이메일 인증하기</a></p>
                  <p style="color:#717182;font-size:12px">버튼이 동작하지 않으면 다음 주소를 복사해 접속하세요:<br>%s</p>
                </div>
                """.formatted(link, link);
    }

    private String resetHtml(String link) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:auto">
                  <h2>CareerTuner 비밀번호 재설정</h2>
                  <p>아래 버튼을 눌러 비밀번호를 재설정하세요. 링크는 1시간 동안 유효합니다.</p>
                  <p><a href="%s" style="display:inline-block;padding:12px 20px;background:#030213;color:#fff;border-radius:8px;text-decoration:none">비밀번호 재설정</a></p>
                  <p style="color:#717182;font-size:12px">본인이 요청하지 않았다면 이 메일을 무시하세요.</p>
                </div>
                """.formatted(link);
    }
}
