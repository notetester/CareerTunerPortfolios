package com.careertuner.admin.notification.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.notification.dto.AdminNotificationResponse;
import com.careertuner.admin.notification.dto.AdminNotificationStatsResponse;
import com.careertuner.admin.notification.dto.AdminNotificationStatsResponse.CategoryStat;
import com.careertuner.admin.notification.dto.AdminNotificationStatsResponse.TrendPoint;
import com.careertuner.admin.notification.mapper.AdminNotificationMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private final AdminNotificationMapper notificationMapper;

    @Override
    public List<AdminNotificationResponse> getNotifications(AuthUser authUser, int size) {
        requireAdmin(authUser);
        int limit = Math.max(20, Math.min(size, 200));
        return notificationMapper.findRecent(limit);
    }

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TREND_DAYS = 7;

    @Override
    public AdminNotificationStatsResponse getStats(AuthUser authUser) {
        requireAdmin(authUser);
        long total = notificationMapper.countAll();
        long read = notificationMapper.countRead();
        long unread = total - read;
        int readRate = rate(read, total);

        List<CategoryStat> categories = aggregateCategories(notificationMapper.countByType());
        List<TrendPoint> trend = buildTrend(notificationMapper.countByDayLast7());
        long todaySent = trend.isEmpty() ? 0 : trend.get(trend.size() - 1).count();

        return new AdminNotificationStatsResponse(total, read, unread, readRate, todaySent, categories, trend);
    }

    /** type별 원자료를 표시 카테고리로 버킷팅해 발송/읽음/읽음률까지 BE 에서 계산한다. */
    private List<CategoryStat> aggregateCategories(List<Map<String, Object>> rows) {
        Map<String, long[]> byCat = new LinkedHashMap<>(); // category -> [sent, read]
        for (Map<String, Object> m : rows) {
            String cat = AdminNotificationCategories.of(str(m.get("type")));
            long[] agg = byCat.computeIfAbsent(cat, k -> new long[2]);
            agg[0] += num(m.get("sent"));
            agg[1] += num(m.get("read"));
        }
        List<CategoryStat> result = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byCat.entrySet()) {
            long sent = e.getValue()[0];
            long readCnt = e.getValue()[1];
            int r = rate(readCnt, sent);
            result.add(new CategoryStat(e.getKey(), sent, readCnt, r, sent > 0 && r < 50));
        }
        return result;
    }

    /** 최근 7일을 과거→오늘 순으로 0 채워 만들고, 일자별 발송 수를 매핑한다(KST). */
    private List<TrendPoint> buildTrend(List<Map<String, Object>> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> m : rows) {
            counts.put(str(m.get("day")), num(m.get("cnt")));
        }
        LocalDate today = LocalDate.now(KST);
        List<TrendPoint> trend = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String key = d.format(DAY_FMT);
            trend.add(new TrendPoint(key, counts.getOrDefault(key, 0L), i == 0));
        }
        return trend;
    }

    private static int rate(long part, long whole) {
        return whole > 0 ? (int) Math.round(part * 100.0 / whole) : 0;
    }

    private static long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }
}
