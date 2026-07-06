package com.careertuner.community.moderation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.community.moderation.domain.ModerationSetting;
import com.careertuner.community.moderation.domain.Strictness;
import com.careertuner.community.moderation.mapper.ModerationSettingMapper;

import jakarta.annotation.PostConstruct;

/**
 * AI 검열 + 콘텐츠 중재 정책 캐싱 서비스.
 * 시작 시 DB에서 1회 로드 → volatile 필드 보관.
 * PATCH 시 DB UPDATE + 캐시 즉시 갱신.
 *
 * <p>엄격도/숨김 임계/사용자 제재에 더해, 글·댓글·문의 작성 rate-limit(도배 방지)과
 * 신고 누적 블러 임계까지 단일행으로 관리한다(코드가 런타임 참조). TripTogether
 * ContentModerationPolicy 의 rate-limit·reportThreshold 축을 이식했다.</p>
 */
@Service
public class ModerationSettingService {

    private static final Logger log = LoggerFactory.getLogger(ModerationSettingService.class);
    private static final int SETTING_ID = 1;

    private final ModerationSettingMapper settingMapper;

    private volatile Strictness strictness = Strictness.NORMAL;
    private volatile double hideThreshold = 0.80;
    private volatile int sanctionThreshold = 3;
    private volatile int blockDays = 7;

    // ── 작성 rate-limit + 신고 블러 임계 (코드 기본값 = 기존 @Value 기본값과 동일) ──
    private volatile int reportBlurThreshold = 3;
    private volatile int postRateWindowSeconds = 60;
    private volatile int postRateMax = 10;
    private volatile int commentRateWindowSeconds = 60;
    private volatile int commentRateMax = 20;
    private volatile int inquiryRateWindowSeconds = 600;
    private volatile int inquiryRateMax = 0; // 0 = 무제약(문의는 도입 전 무제한이 기본 동작)

    public ModerationSettingService(ModerationSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    @PostConstruct
    void init() {
        // 단일 행 전체 로드. 컬럼은 patches/20260706b 로 DB 에 적용돼 있어야 한다(미적용이면 여기서 실패해
        // 스키마 문제를 드러낸다 — 조용한 강등으로 숨기지 않는다). 행이 없을 때만 코드 기본값을 쓴다.
        ModerationSetting s = settingMapper.findById(SETTING_ID);
        if (s != null) {
            applyToCache(s);
        } else {
            log.warn("검열/중재 설정 행(id={}) 미존재 — 코드 기본값 사용", SETTING_ID);
        }
        log.info("검열/중재 설정 로드: strictness={}, hideThreshold={}, sanction={}/{}일, "
                        + "reportBlur={}, postRL={}s/{}건, commentRL={}s/{}건, inquiryRL={}s/{}건",
                strictness, hideThreshold, sanctionThreshold, blockDays,
                reportBlurThreshold, postRateWindowSeconds, postRateMax,
                commentRateWindowSeconds, commentRateMax, inquiryRateWindowSeconds, inquiryRateMax);
    }

    private void applyToCache(ModerationSetting s) {
        this.strictness = s.getStrictness();
        this.hideThreshold = s.getHideThreshold();
        this.sanctionThreshold = s.getSanctionThreshold();
        this.blockDays = s.getBlockDays();
        this.reportBlurThreshold = s.getReportBlurThreshold();
        this.postRateWindowSeconds = s.getPostRateWindowSeconds();
        this.postRateMax = s.getPostRateMax();
        this.commentRateWindowSeconds = s.getCommentRateWindowSeconds();
        this.commentRateMax = s.getCommentRateMax();
        this.inquiryRateWindowSeconds = s.getInquiryRateWindowSeconds();
        this.inquiryRateMax = s.getInquiryRateMax();
    }

    public Strictness getStrictness() {
        return strictness;
    }

    public double getHideThreshold() {
        return hideThreshold;
    }

    public int getSanctionThreshold() {
        return sanctionThreshold;
    }

    public int getBlockDays() {
        return blockDays;
    }

    public int getReportBlurThreshold() {
        return reportBlurThreshold;
    }

    public int getPostRateWindowSeconds() {
        return postRateWindowSeconds;
    }

    public int getPostRateMax() {
        return postRateMax;
    }

    public int getCommentRateWindowSeconds() {
        return commentRateWindowSeconds;
    }

    public int getCommentRateMax() {
        return commentRateMax;
    }

    public int getInquiryRateWindowSeconds() {
        return inquiryRateWindowSeconds;
    }

    public int getInquiryRateMax() {
        return inquiryRateMax;
    }

    /** 현재 설정 전체(단일 행). 행이 없을 때만 캐시 스냅샷으로 응답한다. updated_at 포함. */
    public ModerationSetting getCurrent() {
        ModerationSetting s = settingMapper.findById(SETTING_ID);
        return s != null ? s : snapshot();
    }

    /** 캐시 값으로 구성한 스냅샷(행이 없을 때의 폴백). */
    private ModerationSetting snapshot() {
        ModerationSetting s = new ModerationSetting();
        s.setId(SETTING_ID);
        s.setStrictness(strictness);
        s.setHideThreshold(hideThreshold);
        s.setSanctionThreshold(sanctionThreshold);
        s.setBlockDays(blockDays);
        s.setReportBlurThreshold(reportBlurThreshold);
        s.setPostRateWindowSeconds(postRateWindowSeconds);
        s.setPostRateMax(postRateMax);
        s.setCommentRateWindowSeconds(commentRateWindowSeconds);
        s.setCommentRateMax(commentRateMax);
        s.setInquiryRateWindowSeconds(inquiryRateWindowSeconds);
        s.setInquiryRateMax(inquiryRateMax);
        return s;
    }

    /** 정책 저장(단일 UPDATE) + 캐시 즉시 갱신. */
    public void update(ModerationSetting next) {
        next.setId(SETTING_ID);
        settingMapper.update(next);
        applyToCache(next);
        log.info("검열/중재 설정 변경: strictness={}, hideThreshold={}, sanction={}/{}일, "
                        + "reportBlur={}, postRL={}s/{}건, commentRL={}s/{}건, inquiryRL={}s/{}건",
                strictness, hideThreshold, sanctionThreshold, blockDays,
                reportBlurThreshold, postRateWindowSeconds, postRateMax,
                commentRateWindowSeconds, commentRateMax, inquiryRateWindowSeconds, inquiryRateMax);
    }
}
