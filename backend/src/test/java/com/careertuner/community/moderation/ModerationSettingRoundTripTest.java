package com.careertuner.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.community.moderation.domain.ModerationSetting;
import com.careertuner.community.moderation.mapper.ModerationSettingMapper;
import com.careertuner.community.moderation.service.ModerationSettingService;

/**
 * 중재 설정 <b>실 DB round-trip</b> 검증 — 콘솔 저장값이 실제 DB(team1_db)에 영속되고,
 * 코드가 런타임에 그 값을 읽는지(코드 기본값이 아니라)를 증명한다.
 * {@code @Transactional} 로 커밋 없이 롤백 → 공유 DB 오염 없음.
 */
@SpringBootTest
@Transactional
class ModerationSettingRoundTripTest {

    @Autowired
    ModerationSettingService service;
    @Autowired
    ModerationSettingMapper mapper;

    @Test
    void update_persistsToRealDb_andRuntimeReadsIt() {
        ModerationSetting before = mapper.findById(1);
        assertThat(before).as("id=1 시드 행이 실제 DB에 존재해야 한다").isNotNull();

        // 코드 기본값(reportBlur=3, postRateMax=10)과 확실히 다른 값으로 저장
        ModerationSetting next = new ModerationSetting();
        next.setStrictness(before.getStrictness());
        next.setHideThreshold(before.getHideThreshold());
        next.setSanctionThreshold(before.getSanctionThreshold());
        next.setBlockDays(before.getBlockDays());
        next.setReportBlurThreshold(77);
        next.setPostRateWindowSeconds(before.getPostRateWindowSeconds());
        next.setPostRateMax(99);
        next.setCommentRateWindowSeconds(before.getCommentRateWindowSeconds());
        next.setCommentRateMax(before.getCommentRateMax());
        next.setInquiryRateWindowSeconds(before.getInquiryRateWindowSeconds());
        next.setInquiryRateMax(before.getInquiryRateMax());

        service.update(next);

        // (1) 실제 DB에 영속됐는지 — 매퍼로 다시 읽어 확인
        ModerationSetting persisted = mapper.findById(1);
        assertThat(persisted.getReportBlurThreshold()).isEqualTo(77);
        assertThat(persisted.getPostRateMax()).isEqualTo(99);

        // (2) 런타임 소비 경로(getter, 게시글/댓글 블러·rate-limit이 읽는 값)가 DB 저장값을 반영하는지
        assertThat(service.getReportBlurThreshold()).isEqualTo(77);
        assertThat(service.getPostRateMax()).isEqualTo(99);
    }
}
