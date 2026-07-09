package com.careertuner.applicationcase.support;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * B 영역(지원 건 · 공고 · 공고분석 · 기업분석) 응답의 표시 시각을 한국 시간(KST)으로 맞추는 단일 지점.
 *
 * <p>배경: 운영 MySQL 은 UTC 로 동작(@@system_time_zone=UTC)해 {@code created_at}(CURRENT_TIMESTAMP)이
 * UTC 벽시계로 저장되고, {@code LocalDateTime}(오프셋 없음)으로 그대로 내려온다. 프런트는 이 값을
 * 벽시계 그대로 표시하므로 화면이 한국시간보다 9시간 어긋난다. 그래서:
 * <ul>
 *   <li>{@link #dbToDisplay(LocalDateTime)} — DB 가 만든 시각(created_at 등)을 읽을 때 UTC→KST 로 보정한다.</li>
 *   <li>{@link #now()} — Java 가 시각을 찍는 필드(confirmed_at, checked_at 등)를 JVM tz 와 무관하게 KST 로 찍는다.</li>
 * </ul>
 *
 * <p><b>주의(전제):</b> {@link #dbToDisplay}는 "DB 저장 시각 = UTC" 라는 전제에 의존한다. 이는 운영/로컬 모두
 * 기본 datasource 가 AWS(UTC) 인 현재 구성에서만 참이다. 만약 DB 세션 tz 를 KST 로 바꾸거나 KST 로컬 DB 로
 * 교체하면 이 보정은 +9시간 과보정이 되므로 제거해야 한다. 근본 해결(공통·인프라 영역: JVM/DB 세션 tz 통일)
 * 전까지의 표시 계층 보정이며, 바꿀 지점은 이 클래스 한 곳이다.
 */
public final class BDisplayTime {

    /** 앱 표시 기준 시간대(한국). */
    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");

    private BDisplayTime() {
    }

    /** Java 로 시각을 찍을 때 사용 — JVM 기본 시간대와 무관하게 KST 벽시계를 반환한다. */
    public static LocalDateTime now() {
        return LocalDateTime.now(APP_ZONE);
    }

    /**
     * DB(UTC)에 저장돼 오프셋 없이 내려온 시각을 KST 표시값으로 변환한다. {@code null} 은 그대로 통과.
     */
    public static LocalDateTime dbToDisplay(LocalDateTime dbUtc) {
        return dbUtc == null
                ? null
                : dbUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(APP_ZONE).toLocalDateTime();
    }
}
