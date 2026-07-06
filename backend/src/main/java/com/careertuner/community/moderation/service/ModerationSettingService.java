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
        // 코어(엄격도·숨김·제재)는 항상 존재하는 컬럼이라 실패하면 곧 진짜 오류다.
        ModerationSetting core = settingMapper.findById(SETTING_ID);
        if (core != null) {
            applyCoreToCache(core);
        } else {
            log.warn("검열/중재 코어 설정 미존재, 코드 기본값 사용");
        }
        // rate-limit·신고 블러(patches/20260706b) 컬럼은 미적용 DB 에서 실패할 수 있어 분리 로드 후 강등한다.
        try {
            ModerationSetting rl = settingMapper.findRateLimits(SETTING_ID);
            if (rl != null) {
                applyRateToCache(rl);
            }
        } catch (Exception e) {
            log.warn("rate-limit/신고 블러 설정 로드 실패 — 코드 기본값 사용(20260706b 패치 미적용 가능): {}", e.getMessage());
        }
        log.info("검열/중재 설정 로드: strictness={}, hideThreshold={}, sanction={}/{}일, "
                        + "reportBlur={}, postRL={}s/{}건, commentRL={}s/{}건, inquiryRL={}s/{}건",
                strictness, hideThreshold, sanctionThreshold, blockDays,
                reportBlurThreshold, postRateWindowSeconds, postRateMax,
                commentRateWindowSeconds, commentRateMax, inquiryRateWindowSeconds, inquiryRateMax);
    }

    private void applyCoreToCache(ModerationSetting s) {
        this.strictness = s.getStrictness();
        this.hideThreshold = s.getHideThreshold();
        this.sanctionThreshold = s.getSanctionThreshold();
        this.blockDays = s.getBlockDays();
    }

    private void applyRateToCache(ModerationSetting s) {
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

    /**
     * 현재 설정 전체 스냅샷(코어는 DB, rate-limit 은 DB→실패 시 캐시). updated_at 포함.
     * rate-limit 컬럼 미적용 DB 에서도 콘솔이 열리도록 강등한다.
     */
    public ModerationSetting getCurrent() {
        ModerationSetting core = settingMapper.findById(SETTING_ID);
        ModerationSetting out = (core != null) ? core : snapshotCore();
        try {
            ModerationSetting rl = settingMapper.findRateLimits(SETTING_ID);
            if (rl != null) {
                copyRateFields(rl, out);
                return out;
            }
        } catch (Exception e) {
            log.warn("rate-limit/신고 블러 조회 실패 — 캐시값으로 응답(20260706b 패치 미적용 가능): {}", e.getMessage());
        }
        // 캐시값으로 채운다(콘솔이 현재 적용값을 보게).
        out.setReportBlurThreshold(reportBlurThreshold);
        out.setPostRateWindowSeconds(postRateWindowSeconds);
        out.setPostRateMax(postRateMax);
        out.setCommentRateWindowSeconds(commentRateWindowSeconds);
        out.setCommentRateMax(commentRateMax);
        out.setInquiryRateWindowSeconds(inquiryRateWindowSeconds);
        out.setInquiryRateMax(inquiryRateMax);
        return out;
    }

    private static void copyRateFields(ModerationSetting from, ModerationSetting to) {
        to.setReportBlurThreshold(from.getReportBlurThreshold());
        to.setPostRateWindowSeconds(from.getPostRateWindowSeconds());
        to.setPostRateMax(from.getPostRateMax());
        to.setCommentRateWindowSeconds(from.getCommentRateWindowSeconds());
        to.setCommentRateMax(from.getCommentRateMax());
        to.setInquiryRateWindowSeconds(from.getInquiryRateWindowSeconds());
        to.setInquiryRateMax(from.getInquiryRateMax());
    }

    /** 코어 캐시 값으로 구성한 스냅샷(코어 행이 없을 때의 폴백). */
    private ModerationSetting snapshotCore() {
        ModerationSetting s = new ModerationSetting();
        s.setId(SETTING_ID);
        s.setStrictness(strictness);
        s.setHideThreshold(hideThreshold);
        s.setSanctionThreshold(sanctionThreshold);
        s.setBlockDays(blockDays);
        return s;
    }

    /**
     * 정책 저장 + 캐시 즉시 갱신. 코어는 항상 저장하고, rate-limit 컬럼은 분리 저장해
     * 미적용 DB 에서도 코어 저장이 막히지 않게 한다(패치 적용 후 rate-limit 도 영속).
     */
    public void update(ModerationSetting next) {
        next.setId(SETTING_ID);
        settingMapper.updateCore(next);
        applyCoreToCache(next);
        try {
            settingMapper.updateRateLimits(next);
            applyRateToCache(next);
        } catch (Exception e) {
            // 캐시에는 반영해 러닝 인스턴스에서 즉시 적용되게 하되, 영속은 패치 적용 후로 미룬다.
            applyRateToCache(next);
            log.warn("rate-limit/신고 블러 저장 실패 — 코어만 영속됨(20260706b 패치 미적용 가능): {}", e.getMessage());
        }
        log.info("검열/중재 설정 변경: strictness={}, hideThreshold={}, sanction={}/{}일, "
                        + "reportBlur={}, postRL={}s/{}건, commentRL={}s/{}건, inquiryRL={}s/{}건",
                strictness, hideThreshold, sanctionThreshold, blockDays,
                reportBlurThreshold, postRateWindowSeconds, postRateMax,
                commentRateWindowSeconds, commentRateMax, inquiryRateWindowSeconds, inquiryRateMax);
    }
}
