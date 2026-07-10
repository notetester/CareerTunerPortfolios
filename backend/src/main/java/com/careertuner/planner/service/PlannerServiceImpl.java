package com.careertuner.planner.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.planner.domain.PlannerMemo;
import com.careertuner.planner.domain.PlannerScheduleItem;
import com.careertuner.planner.domain.PlannerScheduleReminder;
import com.careertuner.planner.domain.PlannerStrategyAnalysis;
import com.careertuner.planner.dto.PlannerDashboardResponse;
import com.careertuner.planner.dto.PlannerMemoRequest;
import com.careertuner.planner.dto.PlannerMemoResponse;
import com.careertuner.planner.dto.PlannerScheduleItemRequest;
import com.careertuner.planner.dto.PlannerScheduleItemResponse;
import com.careertuner.planner.dto.PlannerScheduleReminderRequest;
import com.careertuner.planner.dto.PlannerScheduleReminderResponse;
import com.careertuner.planner.dto.PlannerStrategyDraftItemResponse;
import com.careertuner.planner.dto.PlannerStrategyDraftResponse;
import com.careertuner.planner.mapper.PlannerMapper;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PlannerServiceImpl implements PlannerService {

    private static final TypeReference<List<Object>> OBJECT_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final Set<String> ALLOWED_STATUSES = Set.of("PLANNED", "IN_PROGRESS", "DONE", "CANCELED");
    private static final Set<String> ALLOWED_PRECISIONS =
            Set.of("YEAR", "MONTH", "DAY", "AM_PM", "HOUR", "MINUTE", "SECOND");
    private static final Set<String> ALLOWED_CHANNELS = Set.of(
            "WEB_TOAST",
            "BROWSER",
            "WEB_PUSH",
            "EMAIL",
            "MOBILE_VIBRATE",
            "MOBILE_SOUND",
            "MOBILE_SOUND_VIBRATE",
            "DESKTOP_SOUND",
            "DESKTOP_TOAST",
            "DESKTOP_TASKBAR");
    private static final List<String> DEFAULT_CHANNELS = List.of("WEB_TOAST", "BROWSER", "WEB_PUSH");

    private final PlannerMapper plannerMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PlannerDashboardResponse getDashboard(Long userId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime resolvedFrom = from == null ? now.minusDays(7) : from;
        LocalDateTime resolvedTo = to == null ? now.plusDays(90) : to;
        List<PlannerMemoResponse> memos = plannerMapper.findMemos(userId).stream()
                .map(PlannerMemoResponse::from)
                .toList();
        List<PlannerScheduleItemResponse> items = scheduleResponses(
                plannerMapper.findScheduleItems(userId, resolvedFrom, resolvedTo));
        return new PlannerDashboardResponse(memos, items);
    }

    @Override
    @Transactional
    public PlannerMemoResponse createMemo(Long userId, PlannerMemoRequest request) {
        validateMemo(request);
        ensureLinkedOwnership(userId, request.applicationCaseId(), request.fitAnalysisId());
        PlannerMemo memo = PlannerMemo.builder()
                .userId(userId)
                .title(trimToNull(request.title()))
                .content(trimToNull(request.content()))
                .color(defaultText(request.color(), "yellow"))
                .pinned(Boolean.TRUE.equals(request.pinned()))
                .overlayVisible(Boolean.TRUE.equals(request.overlayVisible()))
                .opacity(normalizeOpacity(request.opacity()))
                .applicationCaseId(request.applicationCaseId())
                .fitAnalysisId(request.fitAnalysisId())
                .build();
        plannerMapper.insertMemo(memo);
        return PlannerMemoResponse.from(plannerMapper.findMemo(userId, memo.getId()));
    }

    @Override
    @Transactional
    public PlannerMemoResponse updateMemo(Long userId, Long memoId, PlannerMemoRequest request) {
        validateMemo(request);
        ensureExists(plannerMapper.findMemo(userId, memoId), "메모를 찾을 수 없습니다.");
        ensureLinkedOwnership(userId, request.applicationCaseId(), request.fitAnalysisId());
        PlannerMemo memo = PlannerMemo.builder()
                .id(memoId)
                .userId(userId)
                .title(trimToNull(request.title()))
                .content(trimToNull(request.content()))
                .color(defaultText(request.color(), "yellow"))
                .pinned(Boolean.TRUE.equals(request.pinned()))
                .overlayVisible(Boolean.TRUE.equals(request.overlayVisible()))
                .opacity(normalizeOpacity(request.opacity()))
                .applicationCaseId(request.applicationCaseId())
                .fitAnalysisId(request.fitAnalysisId())
                .build();
        if (plannerMapper.updateMemo(memo) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "메모를 찾을 수 없습니다.");
        }
        return PlannerMemoResponse.from(plannerMapper.findMemo(userId, memoId));
    }

    @Override
    @Transactional
    public void deleteMemo(Long userId, Long memoId) {
        if (plannerMapper.deleteMemo(userId, memoId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "메모를 찾을 수 없습니다.");
        }
    }

    @Override
    @Transactional
    public PlannerScheduleItemResponse createScheduleItem(Long userId, PlannerScheduleItemRequest request) {
        PlannerScheduleItem item = toScheduleItem(userId, null, request);
        plannerMapper.insertScheduleItem(item);
        replaceReminders(userId, item, request.reminders());
        return scheduleResponse(plannerMapper.findScheduleItem(userId, item.getId()));
    }

    @Override
    @Transactional
    public PlannerScheduleItemResponse updateScheduleItem(Long userId, Long itemId, PlannerScheduleItemRequest request) {
        ensureExists(plannerMapper.findScheduleItem(userId, itemId), "일정을 찾을 수 없습니다.");
        PlannerScheduleItem item = toScheduleItem(userId, itemId, request);
        if (plannerMapper.updateScheduleItem(item) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다.");
        }
        replaceReminders(userId, item, request.reminders());
        return scheduleResponse(plannerMapper.findScheduleItem(userId, itemId));
    }

    @Override
    @Transactional
    public void deleteScheduleItem(Long userId, Long itemId) {
        if (plannerMapper.deleteScheduleItem(userId, itemId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다.");
        }
        plannerMapper.deleteRemindersByItem(userId, itemId);
    }

    @Override
    @Transactional(readOnly = true)
    public PlannerStrategyDraftResponse createStrategyDraft(Long userId, Long fitAnalysisId) {
        PlannerStrategyAnalysis source = plannerMapper.findStrategyAnalysis(userId, fitAnalysisId);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "적합도 분석 결과를 찾을 수 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadlineAt = source.getDeadlineDate() == null
                ? now.plusDays(14).with(LocalTime.of(18, 0))
                : source.getDeadlineDate().atTime(18, 0);
        List<String> staleReasons = staleReasons(source, now);
        List<String> actions = collectStrategyActions(source);
        String timezone = ZoneId.systemDefault().getId();

        List<PlannerStrategyDraftItemResponse> drafts = new ArrayList<>();
        LocalDateTime decisionStart = nextHour(now.plusHours(1));
        drafts.add(draft(
                userId,
                source,
                "지원 전략 검토",
                "%s %s 지원 여부와 보완 우선순위를 확정합니다.".formatted(source.getCompanyName(), source.getJobTitle()),
                decisionStart,
                decisionStart.plusMinutes(40),
                timezone,
                "decision",
                staleReasons));

        for (int i = 0; i < Math.min(3, actions.size()); i++) {
            LocalDateTime start = actionSlot(now, deadlineAt, i);
            drafts.add(draft(
                    userId,
                    source,
                    abbreviate("전략 액션: " + actions.get(i), 90),
                    actions.get(i),
                    start,
                    start.plusHours(1),
                    timezone,
                    "action-" + (i + 1),
                    staleReasons));
        }

        LocalDateTime finalCheckStart = finalCheckSlot(now, deadlineAt);
        drafts.add(draft(
                userId,
                source,
                "마감 전 최종 점검",
                "지원서 제출 상태, 첨삭 반영 여부, 면접 대비 메모를 마지막으로 확인합니다.",
                finalCheckStart,
                finalCheckStart.plusMinutes(30),
                timezone,
                "final-check",
                staleReasons));

        return new PlannerStrategyDraftResponse(
                source.getFitAnalysisId(),
                source.getApplicationCaseId(),
                source.getCompanyName(),
                source.getJobTitle(),
                now,
                staleReasons,
                drafts);
    }

    private PlannerScheduleItem toScheduleItem(Long userId, Long itemId, PlannerScheduleItemRequest request) {
        if (request.startAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "일정 시작 시각이 필요합니다.");
        }
        if (request.endAt() != null && request.endAt().isBefore(request.startAt())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "일정 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
        ensureLinkedOwnership(userId, request.applicationCaseId(), request.fitAnalysisId());
        String status = normalizeStatus(request.status());
        boolean allDay = Boolean.TRUE.equals(request.allDay());
        return PlannerScheduleItem.builder()
                .id(itemId)
                .userId(userId)
                .title(request.title().trim())
                .description(trimToNull(request.description()))
                .kind(defaultText(request.kind(), "TASK"))
                .status(status)
                .allDay(allDay)
                .timingPrecision(normalizePrecision(request.timingPrecision(), allDay))
                .startAt(request.startAt())
                .endAt(request.endAt())
                .timezone(defaultText(request.timezone(), ZoneId.systemDefault().getId()))
                .applicationCaseId(request.applicationCaseId())
                .fitAnalysisId(request.fitAnalysisId())
                .sourceType(defaultText(request.sourceType(), "MANUAL"))
                .sourceRef(trimToNull(request.sourceRef()))
                .sourceSnapshotJson(trimToNull(request.sourceSnapshotJson()))
                .overlayVisible(Boolean.TRUE.equals(request.overlayVisible()))
                .opacity(normalizeOpacity(request.opacity()))
                .pinned(Boolean.TRUE.equals(request.pinned()))
                .clickThrough(Boolean.TRUE.equals(request.clickThrough()))
                .build();
    }

    private void replaceReminders(Long userId, PlannerScheduleItem item, List<PlannerScheduleReminderRequest> requests) {
        plannerMapper.deleteRemindersByItem(userId, item.getId());
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (PlannerScheduleReminderRequest request : requests) {
            PlannerScheduleReminder reminder = toReminder(item, request);
            if (reminder != null) {
                plannerMapper.insertReminder(reminder);
            }
        }
    }

    private PlannerScheduleReminder toReminder(PlannerScheduleItem item, PlannerScheduleReminderRequest request) {
        Integer offsetMinutes = request.offsetMinutes();
        LocalDateTime remindAt = request.remindAt();
        if (remindAt == null && offsetMinutes != null) {
            remindAt = item.getStartAt().minusMinutes(offsetMinutes);
        }
        if (remindAt == null) {
            return null;
        }
        return PlannerScheduleReminder.builder()
                .scheduleItemId(item.getId())
                .remindAt(remindAt)
                .offsetMinutes(offsetMinutes)
                .channelsJson(toJson(normalizeChannels(request.channels())))
                .soundEnabled(request.soundEnabled() == null || request.soundEnabled())
                .vibrationEnabled(request.vibrationEnabled() == null || request.vibrationEnabled())
                .status("PENDING")
                .build();
    }

    private List<PlannerScheduleItemResponse> scheduleResponses(List<PlannerScheduleItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = items.stream().map(PlannerScheduleItem::getId).toList();
        Map<Long, List<PlannerScheduleReminder>> reminders = plannerMapper.findRemindersByScheduleItemIds(itemIds).stream()
                .collect(Collectors.groupingBy(PlannerScheduleReminder::getScheduleItemId));
        return items.stream()
                .map(item -> PlannerScheduleItemResponse.of(item, reminderResponses(reminders.get(item.getId()))))
                .toList();
    }

    private PlannerScheduleItemResponse scheduleResponse(PlannerScheduleItem item) {
        return scheduleResponses(List.of(item)).get(0);
    }

    private List<PlannerScheduleReminderResponse> reminderResponses(List<PlannerScheduleReminder> reminders) {
        if (reminders == null || reminders.isEmpty()) {
            return List.of();
        }
        return reminders.stream()
                .map(reminder -> PlannerScheduleReminderResponse.of(reminder, parseChannels(reminder.getChannelsJson())))
                .toList();
    }

    private PlannerStrategyDraftItemResponse draft(
            Long userId,
            PlannerStrategyAnalysis source,
            String title,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String timezone,
            String ref,
            List<String> staleReasons) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("fitAnalysisId", source.getFitAnalysisId());
        snapshot.put("applicationCaseId", source.getApplicationCaseId());
        snapshot.put("fitScore", source.getFitScore());
        snapshot.put("analysisCreatedAt", source.getAnalysisCreatedAt());
        snapshot.put("generatedAt", LocalDateTime.now());
        snapshot.put("staleReasons", staleReasons);
        snapshot.put("actionRef", ref);
        return new PlannerStrategyDraftItemResponse(
                title,
                description,
                "STRATEGY",
                false,
                "MINUTE",
                startAt,
                endAt,
                timezone,
                source.getApplicationCaseId(),
                source.getFitAnalysisId(),
                "FIT_ANALYSIS_STRATEGY",
                "fit-analysis:%d:%s".formatted(source.getFitAnalysisId(), ref),
                toJson(snapshot),
                defaultReminderDrafts(startAt),
                plannerMapper.countScheduleOverlaps(userId, startAt, endAt, null));
    }

    private List<PlannerScheduleReminderRequest> defaultReminderDrafts(LocalDateTime startAt) {
        LocalDateTime now = LocalDateTime.now();
        List<PlannerScheduleReminderRequest> reminders = new ArrayList<>();
        LocalDateTime dayBefore = startAt.minusDays(1);
        if (dayBefore.isAfter(now)) {
            reminders.add(new PlannerScheduleReminderRequest(dayBefore, 24 * 60, DEFAULT_CHANNELS, true, true));
        }
        LocalDateTime hourBefore = startAt.minusHours(1);
        if (hourBefore.isAfter(now)) {
            reminders.add(new PlannerScheduleReminderRequest(hourBefore, 60, DEFAULT_CHANNELS, true, true));
        } else {
            LocalDateTime tenMinutesBefore = startAt.minusMinutes(10);
            if (tenMinutesBefore.isAfter(now)) {
                reminders.add(new PlannerScheduleReminderRequest(tenMinutesBefore, 10, DEFAULT_CHANNELS, true, true));
            }
        }
        return reminders;
    }

    private List<String> staleReasons(PlannerStrategyAnalysis source, LocalDateTime now) {
        List<String> reasons = new ArrayList<>();
        if (source.getLatestFitAnalysisId() != null && !source.getLatestFitAnalysisId().equals(source.getFitAnalysisId())) {
            reasons.add("이 지원 건에는 더 최신 적합도 분석이 있습니다.");
        }
        if (source.getApplicationUpdatedAt() != null
                && source.getAnalysisCreatedAt() != null
                && source.getApplicationUpdatedAt().isAfter(source.getAnalysisCreatedAt())) {
            reasons.add("지원 건 정보가 이 분석 이후 수정되었습니다.");
        }
        if (source.getDeadlineDate() != null && source.getDeadlineDate().atTime(23, 59, 59).isBefore(now)) {
            reasons.add("공고 마감일이 이미 지났습니다.");
        }
        if (source.getDeadlineDate() != null
                && Duration.between(now, source.getDeadlineDate().atTime(18, 0)).toHours() <= 24) {
            reasons.add("마감까지 24시간 이하로 남아 일정이 압축되었습니다.");
        }
        return reasons;
    }

    private List<String> collectStrategyActions(PlannerStrategyAnalysis source) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        actions.addAll(parseActionList(source.getStrategyActions()));
        actions.addAll(parseActionList(source.getRecommendedStudy()));
        actions.addAll(parseActionList(source.getRecommendedCertificates()));
        actions.addAll(parseActionList(source.getGapRecommendations()));
        actions.addAll(parseActionList(source.getCertificateRecommendations()));
        if (actions.isEmpty() && source.getStrategy() != null && !source.getStrategy().isBlank()) {
            actions.add(abbreviate(source.getStrategy().trim(), 180));
        }
        if (actions.isEmpty()) {
            actions.add("프로필과 지원서에 현재 적합도 분석의 부족 역량 보완 계획을 반영합니다.");
        }
        return actions.stream().limit(6).toList();
    }

    private List<String> parseActionList(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(trimmed, OBJECT_LIST).stream()
                    .map(this::actionText)
                    .filter(text -> text != null && !text.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (Exception ignored) {
            return Arrays.stream(trimmed.split("\\r?\\n|,"))
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .distinct()
                    .toList();
        }
    }

    @SuppressWarnings("unchecked")
    private String actionText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Map<?, ?> raw) {
            Map<Object, Object> map = (Map<Object, Object>) raw;
            String title = firstString(map, "title", "task", "skill", "name", "category");
            String detail = firstString(map, "practiceTask", "reason", "description", "action", "expectedDuration");
            if (title != null && detail != null) {
                return title + " - " + detail;
            }
            return title != null ? title : detail;
        }
        return String.valueOf(value);
    }

    private String firstString(Map<Object, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
            if (value != null && !(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private LocalDateTime actionSlot(LocalDateTime now, LocalDateTime deadlineAt, int index) {
        long hoursUntilDeadline = Math.max(1, Duration.between(now, deadlineAt).toHours());
        if (hoursUntilDeadline <= 24) {
            return nextHour(now.plusHours(2L + index * 2L));
        }
        if (hoursUntilDeadline <= 72) {
            return nextHour(now.plusHours(6L + index * 8L));
        }
        return now.plusDays(index + 1L)
                .withHour(index % 2 == 0 ? 10 : 15)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }

    private LocalDateTime finalCheckSlot(LocalDateTime now, LocalDateTime deadlineAt) {
        LocalDateTime preferred = deadlineAt.minusHours(4);
        if (preferred.isAfter(now.plusHours(2))) {
            return nextHour(preferred);
        }
        return nextHour(now.plusHours(2));
    }

    private LocalDateTime nextHour(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.HOURS).plusHours(value.getMinute() == 0 ? 0 : 1);
    }

    private void validateMemo(PlannerMemoRequest request) {
        if (trimToNull(request.title()) == null && trimToNull(request.content()) == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "메모 제목 또는 내용을 입력해야 합니다.");
        }
    }

    private void ensureLinkedOwnership(Long userId, Long applicationCaseId, Long fitAnalysisId) {
        if (applicationCaseId != null && plannerMapper.countApplicationCase(userId, applicationCaseId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "연결할 지원 건을 찾을 수 없습니다.");
        }
        if (fitAnalysisId != null && plannerMapper.countFitAnalysis(userId, fitAnalysisId, applicationCaseId) == 0) {
            String message = applicationCaseId == null
                    ? "연결할 적합도 분석을 찾을 수 없습니다."
                    : "선택한 적합도 분석은 연결할 지원 건의 결과가 아닙니다.";
            ErrorCode errorCode = applicationCaseId == null ? ErrorCode.NOT_FOUND : ErrorCode.INVALID_INPUT;
            throw new BusinessException(errorCode, message);
        }
    }

    private void ensureExists(Object value, String message) {
        if (value == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, message);
        }
    }

    private String normalizeStatus(String value) {
        String normalized = defaultText(value, "PLANNED").toUpperCase();
        return ALLOWED_STATUSES.contains(normalized) ? normalized : "PLANNED";
    }

    private String normalizePrecision(String value, boolean allDay) {
        String normalized = defaultText(value, allDay ? "DAY" : "MINUTE").toUpperCase();
        return ALLOWED_PRECISIONS.contains(normalized) ? normalized : allDay ? "DAY" : "MINUTE";
    }

    private double normalizeOpacity(Double value) {
        if (value == null) {
            return 1.0;
        }
        return Math.max(0.2, Math.min(1.0, value));
    }

    private List<String> normalizeChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return DEFAULT_CHANNELS;
        }
        LinkedHashSet<String> result = channels.stream()
                .filter(channel -> channel != null && !channel.isBlank())
                .map(channel -> channel.trim().toUpperCase())
                .filter(ALLOWED_CHANNELS::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return result.isEmpty() ? DEFAULT_CHANNELS : List.copyOf(result);
    }

    private List<String> parseChannels(String channelsJson) {
        if (channelsJson == null || channelsJson.isBlank()) {
            return DEFAULT_CHANNELS;
        }
        try {
            return objectMapper.readValue(channelsJson, STRING_LIST);
        } catch (Exception ignored) {
            return DEFAULT_CHANNELS;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "플래너 데이터를 JSON으로 저장하지 못했습니다.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "...";
    }
}
