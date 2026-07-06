package com.careertuner.support.chatbot.quota;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챗봇 쿼터 정책 <b>실 DB round-trip</b> — 콘솔 저장값이 team1_db 에 영속되고, ChatbotController.ask 가
 * 읽는 getter 가 DB 저장값을 반영하는지 증명. {@code @Transactional} 롤백.
 */
@SpringBootTest
@Transactional
class ChatbotQuotaPolicyRoundTripTest {

    @Autowired
    ChatbotQuotaPolicyService service;
    @Autowired
    ChatbotQuotaPolicyMapper mapper;

    @Test
    void update_persistsToRealDb_andRequestPathReadsIt() {
        assertThat(mapper.findPolicy()).as("chatbot_quota_policy 행이 실제 DB에 존재해야 한다").isNotNull();

        service.update(true, 37, null); // 기본값(OFF/100)과 다른 distinctive 값

        ChatbotQuotaPolicy persisted = mapper.findPolicy();
        assertThat(persisted.enabled()).isTrue();
        assertThat(persisted.dailyLimit()).isEqualTo(37);

        // ChatbotController.ask 가 읽는 getter
        assertThat(service.isEnabled()).isTrue();
        assertThat(service.getDailyLimit()).isEqualTo(37);
    }
}
