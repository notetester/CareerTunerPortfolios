package com.careertuner.planner.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.planner.domain.PlannerScheduleItem;
import com.careertuner.planner.mapper.PlannerMapper;

import lombok.RequiredArgsConstructor;

/**
 * 플래너 일정을 iCalendar(.ics) 텍스트로 내보낸다 — 구글/애플/아웃룩 캘린더에 <b>가져오기(import)</b>로 연동.
 * 단방향 내보내기라 OAuth·외부 호출이 없다(양방향 동기화는 별도 과제). RFC 5545 최소 규격.
 */
@Service
@RequiredArgsConstructor
public class PlannerICalService {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 내보내기 범위: 과거 1년 ~ 미래 2년(연 단위 스펙 계획을 포함하기 충분).
    private static final int PAST_DAYS = 365;
    private static final int FUTURE_DAYS = 730;

    private final PlannerMapper plannerMapper;

    public String export(Long userId) {
        LocalDateTime now = LocalDateTime.now(KST);
        List<PlannerScheduleItem> items = plannerMapper.findScheduleItems(
                userId, now.minusDays(PAST_DAYS), now.plusDays(FUTURE_DAYS));
        String stamp = LocalDateTime.now(ZoneOffset.UTC).format(UTC);

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n")
          .append("VERSION:2.0\r\n")
          .append("PRODID:-//CareerTuner//Planner//KO\r\n")
          .append("CALSCALE:GREGORIAN\r\n")
          .append("METHOD:PUBLISH\r\n")
          .append("X-WR-CALNAME:CareerTuner 플래너\r\n");
        for (PlannerScheduleItem item : items) {
            if (item.getStartAt() == null) {
                continue; // 시작일 없는 항목은 캘린더 이벤트로 만들지 않는다.
            }
            appendEvent(sb, item, stamp);
        }
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private void appendEvent(StringBuilder sb, PlannerScheduleItem item, String stamp) {
        boolean allDay = item.isAllDay() || "DATE".equalsIgnoreCase(item.getTimingPrecision())
                || "DAY".equalsIgnoreCase(item.getTimingPrecision())
                || "YEAR".equalsIgnoreCase(item.getTimingPrecision())
                || "MONTH".equalsIgnoreCase(item.getTimingPrecision());
        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:careertuner-planner-").append(item.getId()).append("@careertuner\r\n");
        sb.append("DTSTAMP:").append(stamp).append("\r\n");
        if (allDay) {
            // 종일 이벤트: VALUE=DATE, DTEND 는 배타적(종료일+1). 종료 없으면 시작일 하루.
            java.time.LocalDate start = item.getStartAt().toLocalDate();
            java.time.LocalDate endExclusive = (item.getEndAt() != null ? item.getEndAt().toLocalDate() : start).plusDays(1);
            sb.append("DTSTART;VALUE=DATE:").append(start.format(DATE)).append("\r\n");
            sb.append("DTEND;VALUE=DATE:").append(endExclusive.format(DATE)).append("\r\n");
        } else {
            // 시각 이벤트: KST → UTC 변환해 표기(캘린더가 로컬 시간대로 재변환).
            sb.append("DTSTART:").append(toUtc(item.getStartAt())).append("\r\n");
            LocalDateTime end = item.getEndAt() != null ? item.getEndAt() : item.getStartAt().plusHours(1);
            sb.append("DTEND:").append(toUtc(end)).append("\r\n");
        }
        sb.append("SUMMARY:").append(escape(item.getTitle())).append("\r\n");
        if (item.getDescription() != null && !item.getDescription().isBlank()) {
            sb.append("DESCRIPTION:").append(escape(item.getDescription())).append("\r\n");
        }
        if ("DEADLINE".equalsIgnoreCase(item.getKind())) {
            sb.append("CATEGORIES:마감\r\n");
        }
        sb.append("END:VEVENT\r\n");
    }

    private static String toUtc(LocalDateTime kstDateTime) {
        return kstDateTime.atZone(KST).withZoneSameInstant(ZoneOffset.UTC).format(UTC);
    }

    /** RFC 5545 텍스트 이스케이프(역슬래시·세미콜론·콤마·개행). */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }
}
