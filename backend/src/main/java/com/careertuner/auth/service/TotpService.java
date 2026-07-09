package com.careertuner.auth.service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

@Service
public class TotpService {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int SECRET_BYTES = 20;
    private static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;

    private final SecureRandom secureRandom = new SecureRandom();

    public String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public boolean verify(String secret, String code) {
        String normalized = code == null ? "" : code.replaceAll("\\s", "");
        if (!normalized.matches("\\d{6}")) {
            return false;
        }
        long step = Instant.now().getEpochSecond() / PERIOD_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            if (generate(secret, step + offset).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public String generateNow(String secret) {
        return generate(secret, Instant.now().getEpochSecond() / PERIOD_SECONDS);
    }

    private String generate(String secret, long step) {
        try {
            byte[] key = decodeBase32(secret);
            byte[] counter = ByteBuffer.allocate(8).putLong(step).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            return "";
        }
    }

    private String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32[(buffer >> (bitsLeft - 5)) & 31]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32[(buffer << (5 - bitsLeft)) & 31]);
        }
        return out.toString();
    }

    private byte[] decodeBase32(String secret) {
        String normalized = secret.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        byte[] result = new byte[normalized.length() * 5 / 8];
        int index = 0;
        for (char c : normalized.toCharArray()) {
            int value = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (value < 0) {
                continue;
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        if (index == result.length) {
            return result;
        }
        byte[] trimmed = new byte[index];
        System.arraycopy(result, 0, trimmed, 0, index);
        return trimmed;
    }
}
