package com.careertuner.interview.handoff;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/**
 * 폰 마이크 핸드오프 페어링 저장소 (인메모리).
 *
 * <p>데스크탑(마이크 없음)이 음성 모의면접을 진행할 때 폰을 원격 마이크로 쓰기 위한
 * WebRTC 시그널링 중계다. non-trickle SDP(ICE 수집 완료 후 교환)라 offer/answer
 * 두 번의 교환이면 충분해, WebSocket 없이 짧은 폴링 REST 로 동작한다(1차: STUN only, 같은 망).
 *
 * <ul>
 *   <li>코드 6자리 + TTL 10분 + 같은 계정만 접근 — 코드 추측/오용 방지.</li>
 *   <li>단일 인스턴스 전제(현 배포 구조). 스케일아웃 시 Redis 등으로 교체 지점.</li>
 *   <li>SDP 는 서버에 저장만 하고 해석하지 않는다. 오디오는 P2P 로 흘러 서버를 거치지 않는다.</li>
 * </ul>
 */
@Component
public class MicHandoffService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    record Pairing(String code, Long userId, Long sessionId, Instant createdAt,
                   AtomicReference<String> offerSdp, AtomicReference<String> answerSdp,
                   AtomicBoolean phoneJoined) {
        boolean expired() {
            return createdAt.plus(TTL).isBefore(Instant.now());
        }
    }

    private final Map<String, Pairing> pairings = new ConcurrentHashMap<>();

    /** 페어링 생성 — 6자리 코드 반환. 데스크탑이 호출한다. */
    public String create(Long userId, Long sessionId) {
        sweep();
        String code;
        do {
            code = String.format("%06d", RANDOM.nextInt(1_000_000));
        } while (pairings.containsKey(code));
        pairings.put(code, new Pairing(code, userId, sessionId, Instant.now(),
                new AtomicReference<>(), new AtomicReference<>(), new AtomicBoolean(false)));
        return code;
    }

    /** 데스크탑의 offer SDP 게시. */
    public void putOffer(String code, Long userId, String sdp) {
        find(code, userId).offerSdp().set(sdp);
    }

    /** 폰의 answer SDP 게시. */
    public void putAnswer(String code, Long userId, String sdp) {
        find(code, userId).answerSdp().set(sdp);
    }

    /** 현재 교환 상태 — role=phone 이면 합류로 표시한다(데스크탑 폴링이 감지). */
    public State state(String code, Long userId, boolean asPhone) {
        Pairing p = find(code, userId);
        if (asPhone) {
            p.phoneJoined().set(true);
        }
        return new State(p.sessionId(), p.phoneJoined().get(), p.offerSdp().get(), p.answerSdp().get());
    }

    /** 페어링 종료(어느 쪽이든). */
    public void close(String code, Long userId) {
        Pairing p = pairings.get(code);
        if (p != null && p.userId().equals(userId)) {
            pairings.remove(code);
        }
    }

    private Pairing find(String code, Long userId) {
        sweep();
        Pairing p = pairings.get(code);
        if (p == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "만료되었거나 존재하지 않는 연결 코드입니다.");
        }
        if (!p.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "같은 계정으로 로그인한 기기에서만 연결할 수 있습니다.");
        }
        return p;
    }

    private void sweep() {
        pairings.values().removeIf(Pairing::expired);
    }

    public record State(Long sessionId, boolean phoneJoined, String offerSdp, String answerSdp) {
    }
}
