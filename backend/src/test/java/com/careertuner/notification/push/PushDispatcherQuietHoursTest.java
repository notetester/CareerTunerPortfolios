package com.careertuner.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/**
 * 방해금지 시간대 판정(#10) 경계 검증.
 *
 * <p>규칙: 시작 포함·끝 제외([start, end)). 자정을 넘기는 구간(start &gt; end)도 처리.
 * 미설정·형식오류·시작==끝은 "방해금지 없음"(false) — 안전하게 발송 허용.
 */
class PushDispatcherQuietHoursTest {

    private static boolean within(String s, String e, String now) {
        return PushDispatcher.isWithinQuietHours(s, e, LocalTime.parse(now));
    }

    @Test
    void 미설정이면_방해금지없음() {
        assertThat(within(null, null, "03:00")).isFalse();
        assertThat(within("", "07:00", "03:00")).isFalse();
        assertThat(within("22:00", "", "03:00")).isFalse();
        assertThat(within(null, "07:00", "03:00")).isFalse();
    }

    @Test
    void 형식오류나_시작끝동일이면_방해금지없음() {
        assertThat(within("이상한값", "07:00", "03:00")).isFalse();
        assertThat(within("09:00", "09:00", "09:00")).isFalse();
    }

    @Test
    void 같은날구간_시작포함_끝제외() {
        // 13:00 ~ 14:00
        assertThat(within("13:00", "14:00", "12:59")).isFalse(); // 직전
        assertThat(within("13:00", "14:00", "13:00")).isTrue();  // 시작 경계 포함
        assertThat(within("13:00", "14:00", "13:30")).isTrue();  // 내부
        assertThat(within("13:00", "14:00", "14:00")).isFalse(); // 끝 경계 제외
        assertThat(within("13:00", "14:00", "14:01")).isFalse(); // 직후
    }

    @Test
    void 자정넘는구간_양쪽포함_끝제외() {
        // 22:00 ~ 07:00 (다음날)
        assertThat(within("22:00", "07:00", "21:59")).isFalse(); // 시작 직전
        assertThat(within("22:00", "07:00", "22:00")).isTrue();  // 시작 경계 포함
        assertThat(within("22:00", "07:00", "23:30")).isTrue();  // 자정 전
        assertThat(within("22:00", "07:00", "03:00")).isTrue();  // 자정 후
        assertThat(within("22:00", "07:00", "06:59")).isTrue();  // 끝 직전
        assertThat(within("22:00", "07:00", "07:00")).isFalse(); // 끝 경계 제외
        assertThat(within("22:00", "07:00", "12:00")).isFalse(); // 한낮(구간 밖)
    }
}
