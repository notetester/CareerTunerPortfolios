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
}
