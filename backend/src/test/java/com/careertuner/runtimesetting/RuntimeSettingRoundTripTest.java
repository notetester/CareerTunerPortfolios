package com.careertuner.runtimesetting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.runtimesetting.domain.RuntimeSetting;
import com.careertuner.runtimesetting.service.RuntimeSettingService;

/**
 * 런타임 설정 <b>실 DB round-trip</b> — 콘솔 저장이 team1_db 에 영속되고 {@code getValue} 가 DB→fallback
 * 순으로 읽는지 증명. (현재 앱 코드에는 getValue 소비처가 없어 console-only 이나, 저장/조회 메커니즘은 정상.)
 * {@code @Transactional} 롤백으로 공유 DB 오염 없음.
 */
@SpringBootTest
@Transactional
class RuntimeSettingRoundTripTest {

    @Autowired
    RuntimeSettingService service;

    @Test
    void save_persistsAndGetValueReadsFromDb() {
        service.saveRuntimeSetting(RuntimeSetting.builder()
                .settingKey("test.roundtrip.key")
                .settingGroup("TEST")
                .displayName("round-trip")
                .settingValue("hello-42")
                .valueType("STRING")
                .secret(false).editable(true).active(true)
                .build(), null);

        // DB→fallback 순: 저장한 활성 설정은 DB 값을 돌려준다
        assertThat(service.getValue("test.roundtrip.key", "FALLBACK")).isEqualTo("hello-42");
        // 없는 키는 fallback 경로
        assertThat(service.getValue("test.roundtrip.absent", "FB")).isEqualTo("FB");
    }

    /** 실소비처(ApplicationCaseAutoPipelineService.autoPipelineEnabled) 가 읽는 것과 동일한 getBoolean 경로 검증. */
    @Test
    void getBoolean_readsDbFlag_forAutoPipelineConsumer() {
        service.saveRuntimeSetting(RuntimeSetting.builder()
                .settingKey("application-case.auto-pipeline.enabled")
                .settingGroup("FEATURE")
                .displayName("자동 파이프라인 사용")
                .settingValue("false")
                .valueType("BOOLEAN")
                .secret(false).editable(true).active(true)
                .build(), null);

        // 관리자가 콘솔에서 false 로 저장 → 소비처가 읽는 getBoolean 이 DB값(false)을 반영(코드 기본값 true 아님)
        assertThat(service.getBoolean("application-case.auto-pipeline.enabled", true)).isFalse();
        // 키가 없으면 코드 기본값(fallback) 사용
        assertThat(service.getBoolean("application-case.auto-pipeline.absent", true)).isTrue();
    }
}
