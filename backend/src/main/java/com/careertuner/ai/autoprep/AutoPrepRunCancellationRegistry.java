package com.careertuner.ai.autoprep;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** 브라우저가 명시적으로 실행을 취소할 수 있도록 사용자·runId별 협력적 취소 신호를 보관한다. */
@Component
public class AutoPrepRunCancellationRegistry {

    private static final long EARLY_CANCEL_TTL_MS = 120_000L;
    private static final int MAX_EARLY_CANCELS = 2_048;

    private final ConcurrentHashMap<RunKey, AutoPrepCancellation> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RunKey, Long> earlyCancelledUntil = new ConcurrentHashMap<>();

    public void register(Long userId, String runId, AutoPrepCancellation cancellation) {
        if (!valid(userId, runId) || cancellation == null) {
            return;
        }
        RunKey key = new RunKey(userId, runId);
        AutoPrepCancellation previous = active.put(key, cancellation);
        if (previous != null && previous != cancellation) {
            previous.cancel();
        }
        // 같은 runId의 지연/중복 stream도 TTL 동안 모두 취소되도록 tombstone은 register가 소비하지 않는다.
        Long cancelledUntil = earlyCancelledUntil.get(key);
        if (cancelledUntil != null && cancelledUntil >= System.currentTimeMillis()) {
            cancellation.cancel();
        }
    }

    public boolean cancel(Long userId, String runId) {
        if (!valid(userId, runId)) {
            return false;
        }
        RunKey key = new RunKey(userId, runId);
        long now = System.currentTimeMillis();
        // cancel POST가 stream register보다 먼저 도착하는 네트워크 역전을 위한 짧은 tombstone.
        earlyCancelledUntil.put(key, now + EARLY_CANCEL_TTL_MS);
        trimEarlyCancels(now);
        AutoPrepCancellation cancellation = active.get(key);
        return cancellation == null || cancellation.cancel();
    }

    public void unregister(Long userId, String runId, AutoPrepCancellation cancellation) {
        if (valid(userId, runId) && cancellation != null) {
            RunKey key = new RunKey(userId, runId);
            active.remove(key, cancellation);
        }
    }

    int activeCount() {
        return active.size();
    }

    int earlyCancelCount() {
        return earlyCancelledUntil.size();
    }

    private void trimEarlyCancels(long now) {
        earlyCancelledUntil.entrySet().removeIf(entry -> entry.getValue() < now);
        int overflow = earlyCancelledUntil.size() - MAX_EARLY_CANCELS;
        if (overflow <= 0) {
            return;
        }
        earlyCancelledUntil.entrySet().stream()
                .sorted(Comparator.comparingLong(java.util.Map.Entry::getValue))
                .limit(overflow)
                .map(java.util.Map.Entry::getKey)
                .toList()
                .forEach(earlyCancelledUntil::remove);
    }

    private static boolean valid(Long userId, String runId) {
        return userId != null && runId != null && !runId.isBlank();
    }

    private record RunKey(Long userId, String runId) {
        private RunKey {
            Objects.requireNonNull(userId);
            runId = runId.trim();
        }
    }
}
