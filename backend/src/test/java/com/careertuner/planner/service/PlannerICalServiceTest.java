package com.careertuner.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.planner.domain.PlannerScheduleItem;
import com.careertuner.planner.mapper.PlannerMapper;

class PlannerICalServiceTest {

    private final PlannerMapper mapper = mock(PlannerMapper.class);
    private final PlannerICalService service = new PlannerICalService(mapper);

    private static PlannerScheduleItem item(Long id, String title, String kind, boolean allDay,
                                            String precision, LocalDateTime start, LocalDateTime end) {
        return PlannerScheduleItem.builder()
                .id(id).userId(1L).title(title).kind(kind).allDay(allDay)
                .timingPrecision(precision).startAt(start).endAt(end).timezone("Asia/Seoul")
                .build();
    }

    @Test
    void emitsValidCalendarWithAllDayAndTimedEvents() {
        when(mapper.findScheduleItems(eq(1L), any(), any())).thenReturn(List.of(
                item(10L, "정보처리기사 필기시험", "EVENT", true, "DAY",
                        LocalDateTime.of(2026, 8, 7, 0, 0), null),
                item(11L, "면접 준비", "TASK", false, "MINUTE",
                        LocalDateTime.of(2026, 8, 5, 14, 0), LocalDateTime.of(2026, 8, 5, 15, 0))));

        String ics = service.export(1L);

        assertThat(ics).startsWith("BEGIN:VCALENDAR").contains("VERSION:2.0")
                .endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("BEGIN:VEVENT").contains("END:VEVENT");
        // 종일 이벤트: VALUE=DATE, 종료일 배타적(+1일).
        assertThat(ics).contains("DTSTART;VALUE=DATE:20260807").contains("DTEND;VALUE=DATE:20260808");
        // 시각 이벤트: KST 14:00 → UTC 05:00.
        assertThat(ics).contains("DTSTART:20260805T050000Z");
        assertThat(ics).contains("SUMMARY:정보처리기사 필기시험").contains("SUMMARY:면접 준비");
    }

    @Test
    void escapesSpecialCharactersAndSkipsUndatedItems() {
        when(mapper.findScheduleItems(eq(1L), any(), any())).thenReturn(List.of(
                item(20L, "회사; 마감, 지원", "DEADLINE", true, "DATE",
                        LocalDateTime.of(2026, 9, 1, 0, 0), null),
                item(21L, "시작일 없음", "TASK", true, "DATE", null, null)));

        String ics = service.export(1L);

        assertThat(ics).contains("SUMMARY:회사\\; 마감\\, 지원"); // RFC5545 이스케이프
        assertThat(ics).contains("CATEGORIES:마감");
        assertThat(ics).doesNotContain("시작일 없음"); // 시작일 없는 항목은 이벤트로 만들지 않음
    }

    @Test
    void emptyPlannerStillProducesValidEmptyCalendar() {
        when(mapper.findScheduleItems(eq(1L), any(), any())).thenReturn(List.of());

        String ics = service.export(1L);

        assertThat(ics).startsWith("BEGIN:VCALENDAR").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).doesNotContain("BEGIN:VEVENT");
    }
}
