package com.careertuner.auth.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MfaSecretCipher {
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final CareerTunerProperties props;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MFA 시크릿 암호화에 실패했습니다.");
        }
    }

    public String decrypt(String encryptedText) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MFA 시크릿 복호화에 실패했습니다.");
        }
    }

    private SecretKeySpec keySpec() throws Exception {
        String seed = props.getJwt().getSecret();
        byte[] key = MessageDigest.getInstance("SHA-256")
                .digest(seed.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }
}
