package com.careertuner.admin.chatbot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.chatbot.dto.ThresholdBucket;
import com.careertuner.admin.chatbot.dto.ThresholdHistogramRow;
import com.careertuner.admin.chatbot.dto.ThresholdPreviewResponse;
import com.careertuner.admin.chatbot.mapper.AdminChatbotResponseLogMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * 임계값 슬라이더 미리보기 구현(F2). 읽기 전용 — 실 챗봇 컷오프 불변, 저장/적용 없음(§1-Q3).
 * gapCount = COUNT(top_similarity &lt; threshold), total = COUNT(top_similarity IS NOT NULL),
 * 그리고 0.05 폭(0.30~0.95) 히스토그램(빈 버킷 0 채움)으로 슬라이더 위치별 변화를 한 번에 그린다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminChatbotThresholdServiceImpl implements AdminChatbotThresholdService {

    /** 히스토그램 버킷 폭. */
    private static final double BUCKET_WIDTH = 0.05;
    /** 히스토그램 구간 하한(이상). 디자인 슬라이더 하단 여유 포함. */
    private static final double HIST_FROM = 0.30;
    /** 히스토그램 구간 상한(미만). */
    private static final double HIST_TO = 0.95;

    private final AdminChatbotResponseLogMapper responseLogMapper;

    @Override
    public ThresholdPreviewResponse preview(AuthUser authUser, Double threshold) {
        requireAdmin(authUser);
        double t = sanitize(threshold);

        long total = responseLogMapper.countWithSimilarity();
        long gapCount = responseLogMapper.countBelowThreshold(t);
        List<ThresholdBucket> histogram = buildHistogram(responseLogMapper.similarityHistogram());

        return new ThresholdPreviewResponse(t, gapCount, total, histogram);
    }

    /** null 은 INVALID_INPUT, 비정상/범위 밖 값은 [0.0,1.0] 으로 보정(읽기 전용 — 던지기보다 보정 우선). */
    private double sanitize(Double threshold) {
        if (threshold == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "threshold 값이 필요합니다.");
        }
        double t = threshold;
        if (Double.isNaN(t)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "threshold 값이 올바르지 않습니다.");
        }
        return Math.max(0.0, Math.min(1.0, t));
    }

    /**
     * raw 버킷(데이터 있는 것만)을 [0.30,0.95) 전 구간 0.05 폭 버킷으로 펴고 빈 버킷은 0 — 연속 막대.
     * 버킷 인덱스는 매퍼 SQL 의 FLOOR(ROUND(top_similarity*100)/5) 와 동일 의미(센트 산술 → 0.30→6 … 0.90→18).
     * 부동소수 나눗셈을 쓰지 않으므로 경계값(0.30/0.35/0.60/0.70 …)도 한 칸 밀리지 않고 정확히 정렬된다.
     */
    private List<ThresholdBucket> buildHistogram(List<ThresholdHistogramRow> raw) {
        Map<Integer, Long> byBucket = raw.stream()
                .collect(Collectors.toMap(ThresholdHistogramRow::getBucket, ThresholdHistogramRow::getCount, (a, b) -> a));
        int fromIdx = (int) Math.round(HIST_FROM / BUCKET_WIDTH); // 0.30 → 6
        int toIdx = (int) Math.round(HIST_TO / BUCKET_WIDTH);     // 0.95 → 19 (exclusive)
        List<ThresholdBucket> out = new ArrayList<>(toIdx - fromIdx);
        for (int idx = fromIdx; idx < toIdx; idx++) {
            double from = idx * BUCKET_WIDTH;
            out.add(new ThresholdBucket(round2(from), round2(from + BUCKET_WIDTH), byBucket.getOrDefault(idx, 0L)));
        }
        return out;
    }

    /** 0.05*idx 누적의 부동소수 잡음 제거(예: 0.30000000004 → 0.30). */
    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !com.careertuner.admin.common.AdminAccess.isAdmin(authUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
