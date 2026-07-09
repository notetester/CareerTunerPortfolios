package com.careertuner.planner.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.planner.domain.PlannerScheduleItem;
import com.careertuner.planner.domain.PlannerScheduleReminder;
import com.careertuner.planner.mapper.PlannerMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerReminderScheduler {

    private static final int BATCH_SIZE = 50;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final PlannerMapper plannerMapper;
    private final NotificationService notificationService;

    @Scheduled(
            initialDelayString = "${careertuner.planner.reminder.initial-delay-ms:60000}",
            fixedDelayString = "${careertuner.planner.reminder.fixed-delay-ms:60000}")
    @Transactional
    public void dispatchDueReminders() {
        List<PlannerScheduleReminder> reminders = plannerMapper.findDueReminders(LocalDateTime.now(), BATCH_SIZE);
        for (PlannerScheduleReminder reminder : reminders) {
            PlannerScheduleItem item = plannerMapper.findScheduleItemByReminderId(reminder.getId());
            if (item == null) {
                plannerMapper.markReminderSent(reminder.getId());
                continue;
            }
            try {
                notificationService.notify(Notification.builder()
                        .userId(item.getUserId())
                        .type("SCHEDULE_REMINDER")
                        .targetType("SCHEDULE")
                        .targetId(item.getId())
                        .title("일정 알림: " + item.getTitle())
                        .message(message(item))
                        .link("/planner?item=" + item.getId())
                        .build());
                plannerMapper.markReminderSent(reminder.getId());
            } catch (RuntimeException ex) {
                log.warn("플래너 리마인더 알림 발송 실패: reminderId={}, itemId={}", reminder.getId(), item.getId(), ex);
            }
        }
    }

    private String message(PlannerScheduleItem item) {
        String time = item.getStartAt() == null ? "시간 미정" : TIME_FORMAT.format(item.getStartAt());
        String linked = item.getApplicationCompanyName() == null
                ? ""
                : " · " + item.getApplicationCompanyName()
                + (item.getApplicationJobTitle() == null ? "" : " " + item.getApplicationJobTitle());
        return time + linked;
    }
}
