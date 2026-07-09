package com.careertuner.applicationcase.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class BDisplayTimeTest {

    @Test
    void dbToDisplayConvertsUtcToKst() {
        // 운영 DB(UTC) 저장 시각 → 화면(KST)은 +9시간.
        LocalDateTime dbUtc = LocalDateTime.of(2026, 7, 8, 15, 34, 44);
        assertThat(BDisplayTime.dbToDisplay(dbUtc))
                .isEqualTo(LocalDateTime.of(2026, 7, 9, 0, 34, 44));
    }

    @Test
    void dbToDisplayCrossesDateBoundary() {
        // 날짜 경계: UTC 2026-07-08T23:00 → KST 2026-07-09T08:00.
        assertThat(BDisplayTime.dbToDisplay(LocalDateTime.of(2026, 7, 8, 23, 0, 0)))
                .isEqualTo(LocalDateTime.of(2026, 7, 9, 8, 0, 0));
    }

    @Test
    void dbToDisplayPassesNullThrough() {
        assertThat(BDisplayTime.dbToDisplay(null)).isNull();
    }

    @Test
    void nowUsesKoreaZoneRegardlessOfJvmDefault() {
        // JVM 기본 tz 를 UTC 로 바꿔도 now() 는 KST 벽시계여야 한다.
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            LocalDateTime utcWall = LocalDateTime.now(java.time.ZoneOffset.UTC);
            LocalDateTime kstWall = BDisplayTime.now();
            // KST 는 UTC 보다 앞서므로, 같은 순간의 벽시계는 UTC 벽시계보다 미래다.
            assertThat(kstWall).isAfter(utcWall);
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }
}
