package com.careertuner.interview.handoff;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * TURN 단기 자격증명 발급 (coturn {@code use-auth-secret} 방식, RFC 5389 REST API).
 *
 * <p>coturn 과 공유하는 {@code TURN_SECRET} 으로 {@code username = <만료epoch>:<userId>} 를
 * HMAC-SHA1 서명해 임시 비밀번호를 만든다. coturn 이 같은 계산으로 검증하므로 서버 간
 * DB 공유가 필요 없다. 미설정({@code TURN_SECRET} 빈 값)이면 STUN 만 반환한다(1차 폴백).
 */
@Service
public class TurnCredentialService {

    private final String secret;
    private final List<String> turnUrls;
    private final long ttlSeconds;
    private final String stunUrl;

    public TurnCredentialService(
            @Value("${careertuner.interview.turn.secret:${TURN_SECRET:}}") String secret,
            @Value("${careertuner.interview.turn.urls:${TURN_URLS:}}") String turnUrls,
            @Value("${careertuner.interview.turn.ttl-seconds:600}") long ttlSeconds,
            @Value("${careertuner.interview.turn.stun-url:stun:stun.l.google.com:19302}") String stunUrl) {
        this.secret = secret == null ? "" : secret.trim();
        this.turnUrls = parseCsv(turnUrls);
        this.ttlSeconds = ttlSeconds;
        this.stunUrl = stunUrl;
    }

    public record IceServer(List<String> urls, String username, String credential) {
    }

    /** 사용자별 ICE 서버 목록 — STUN + (설정 시) 단기 자격증명 TURN. */
    public List<IceServer> iceServers(Long userId) {
        List<IceServer> servers = new ArrayList<>();
        servers.add(new IceServer(List.of(stunUrl), null, null));

        if (!secret.isEmpty() && !turnUrls.isEmpty()) {
            long expiry = Instant.now().getEpochSecond() + ttlSeconds;
            String username = expiry + ":" + userId;
            String credential = hmacSha1Base64(username, secret);
            servers.add(new IceServer(turnUrls, username, credential));
        }
        return servers;
    }

    private static List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv != null) {
            for (String piece : csv.split(",")) {
                if (!piece.isBlank()) {
                    out.add(piece.trim());
                }
            }
        }
        return out;
    }

    private static String hmacSha1Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("TURN 자격증명 생성 실패", e);
        }
    }
}
