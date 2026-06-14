package com.careertuner.community.moderation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.community.moderation.domain.ModerationSetting;
import com.careertuner.community.moderation.domain.Strictness;
import com.careertuner.community.moderation.mapper.ModerationSettingMapper;

import jakarta.annotation.PostConstruct;

/**
 * AI 검열 설정 캐싱 서비스.
 * 시작 시 DB에서 1회 로드 → volatile 필드 보관.
 * PATCH 시 DB UPDATE + 캐시 즉시 갱신.
 */
@Service
public class ModerationSettingService {

    private static final Logger log = LoggerFactory.getLogger(ModerationSettingService.class);
    private static final int SETTING_ID = 1;

    private final ModerationSettingMapper settingMapper;

    private volatile Strictness strictness = Strictness.NORMAL;
    private volatile double hideThreshold = 0.80;

    public ModerationSettingService(ModerationSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    @PostConstruct
    void init() {
        ModerationSetting setting = settingMapper.findById(SETTING_ID);
        if (setting != null) {
            this.strictness = setting.getStrictness();
            this.hideThreshold = setting.getHideThreshold();
            log.info("검열 설정 로드: strictness={}, hideThreshold={}", strictness, hideThreshold);
        } else {
            log.warn("검열 설정 미존재, 기본값 사용: strictness=NORMAL, hideThreshold=0.80");
        }
    }

    public Strictness getStrictness() {
        return strictness;
    }

    public double getHideThreshold() {
        return hideThreshold;
    }

    public ModerationSetting getCurrent() {
        return settingMapper.findById(SETTING_ID);
    }

    public void update(Strictness newStrictness, double newThreshold) {
        settingMapper.update(SETTING_ID, newStrictness.name(), newThreshold);
        this.strictness = newStrictness;
        this.hideThreshold = newThreshold;
        log.info("검열 설정 변경: strictness={}, hideThreshold={}", newStrictness, newThreshold);
    }
}
