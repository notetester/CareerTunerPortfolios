package com.careertuner.ads.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.ads.domain.Advertisement;
import com.careertuner.ads.dto.AdClickResponse;
import com.careertuner.ads.dto.AdResponse;
import com.careertuner.ads.mapper.AdvertisementMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 광고 노출 로직.
 *
 * <p>선택 규칙: 활성·기간·플랫폼 매치 후보 중 최고 priority 그룹만 남기고, weight 가중 랜덤으로 뽑는다.
 * N개 요청 시 가중 랜덤을 중복 없이 반복한다. 유료플랜 사용자는 후보 조회 전에 빈 목록으로 단락.</p>
 */
@Service
@RequiredArgsConstructor
public class AdServiceImpl implements AdService {

    private static final Set<String> PLACEMENTS =
            Set.of("HOME_BANNER", "FEED_INLINE", "SIDEBAR", "INTERSTITIAL");
    private static final Set<String> PLATFORMS = Set.of("WEB", "APP", "DESKTOP");
    private static final int MAX_LIMIT = 5;

    private final AdvertisementMapper adMapper;

    @Override
    public List<AdResponse> serve(Long userId, String placement, String platform, int limit) {
        String normalizedPlacement = normalizePlacement(placement);
        String normalizedPlatform = normalizePlatform(platform);
        int count = Math.max(1, Math.min(limit, MAX_LIMIT));

        // 유료플랜(FREE 아님)에게는 광고를 노출하지 않는다. 비로그인/무료는 노출.
        if (isPaidPlan(userId)) {
            return List.of();
        }

        List<Advertisement> candidates =
                adMapper.findServable(normalizedPlacement, normalizedPlatform, LocalDateTime.now());
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 최고 priority 그룹만 남긴다(정렬은 priority DESC 선행).
        int topPriority = priorityOf(candidates.get(0));
        List<Advertisement> pool = new ArrayList<>();
        for (Advertisement ad : candidates) {
            if (priorityOf(ad) == topPriority) {
                pool.add(ad);
            }
        }

        List<AdResponse> selected = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            Advertisement picked = pickWeighted(pool);
            pool.remove(picked);
            selected.add(AdResponse.from(picked));
        }
        return selected;
    }

    @Override
    @Transactional
    public void recordImpression(Long adId) {
        // best-effort — 대상 없으면 UPDATE 0건으로 조용히 무시(광고가 UX 를 막지 않음).
        adMapper.increaseImpression(adId);
    }

    @Override
    @Transactional
    public AdClickResponse recordClick(Long adId) {
        Advertisement ad = adMapper.findById(adId);
        if (ad == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "광고를 찾을 수 없습니다.");
        }
        adMapper.increaseClick(adId);
        return new AdClickResponse(ad.getId(), ad.getLinkUrl());
    }

    // ── 내부 ──

    private boolean isPaidPlan(Long userId) {
        if (userId == null) {
            return false;
        }
        String plan = adMapper.findUserPlan(userId);
        return plan != null && !"FREE".equalsIgnoreCase(plan.trim());
    }

    private int priorityOf(Advertisement ad) {
        return ad.getPriority() != null ? ad.getPriority() : 0;
    }

    /** weight(>=1) 비례 가중 랜덤. weight 가 비어있으면 1로 취급. */
    private Advertisement pickWeighted(List<Advertisement> pool) {
        long totalWeight = 0;
        for (Advertisement ad : pool) {
            totalWeight += weightOf(ad);
        }
        long roll = ThreadLocalRandom.current().nextLong(totalWeight);
        long cursor = 0;
        for (Advertisement ad : pool) {
            cursor += weightOf(ad);
            if (roll < cursor) {
                return ad;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private long weightOf(Advertisement ad) {
        return ad.getWeight() != null && ad.getWeight() > 0 ? ad.getWeight() : 1;
    }

    private String normalizePlacement(String placement) {
        String upper = placement == null ? "" : placement.trim().toUpperCase(Locale.ROOT);
        if (!PLACEMENTS.contains(upper)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 광고 배치입니다.");
        }
        return upper;
    }

    /** 플랫폼 미지정/미상은 WEB 으로 취급. ALL 광고는 항상 매치되므로 손해 없음. */
    private String normalizePlatform(String platform) {
        String upper = platform == null ? "" : platform.trim().toUpperCase(Locale.ROOT);
        return PLATFORMS.contains(upper) ? upper : "WEB";
    }
}
