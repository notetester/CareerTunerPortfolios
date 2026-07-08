package com.careertuner.fitanalysis.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.fitanalysis.certificate.CertificateNeedGate.Decision;

class CertificateNeedGateTest {

    @Test
    void genericItJobWithoutAnySignal_gateOff_notNeeded() {
        Decision d = CertificateNeedGate.evaluate(
                List.of("Java", "Spring"), List.of("AWS"), "REST API 개발", "백엔드 개발자",
                List.of(), List.of("Java", "Spring", "AWS"), false);

        assertThat(d.active()).isFalse();
        assertThat(d.status()).isEqualTo(CertificateStrategyStatus.NOT_NEEDED);
        assertThat(d.triggeredSignals()).isEmpty();
    }

    @Test
    void postingNamesCertificate_gateOn_stronglyRequired() {
        Decision d = CertificateNeedGate.evaluate(
                List.of("Java"), List.of("정보처리기사"), "백엔드 개발", "백엔드 개발자",
                List.of(), List.of("Spring"), false);

        assertThat(d.active()).isTrue();
        assertThat(d.status()).isEqualTo(CertificateStrategyStatus.REQUIRED_OR_STRONGLY_PREFERRED);
        assertThat(d.triggeredSignals()).contains("POSTING_NAMES_CERTIFICATE");
    }

    @Test
    void nationalTechGradeSuffixInDuties_gateOn() {
        // 'OO기능사' 처럼 한글 2자 이상 + 등급 접미사만 인정(바 '기사' 오탐 방지).
        Decision d = CertificateNeedGate.evaluate(
                List.of("설비"), List.of(), "전기기능사 우대", "설비 기사",
                List.of(), List.of(), false);

        assertThat(d.active()).isTrue();
        assertThat(d.triggeredSignals()).contains("POSTING_NAMES_CERTIFICATE");
    }

    @Test
    void licensedJob_gateOn_stronglyRequired() {
        Decision d = CertificateNeedGate.evaluate(
                List.of(), List.of(), "소방 설비 점검 및 관리", "소방 안전관리자",
                List.of(), List.of(), false);

        assertThat(d.active()).isTrue();
        assertThat(d.status()).isEqualTo(CertificateStrategyStatus.REQUIRED_OR_STRONGLY_PREFERRED);
        assertThat(d.triggeredSignals()).contains("LICENSED_JOB");
    }

    @Test
    void gapCertifiableSkill_gateOn_recommended() {
        // 공고엔 자격증 이름 없음, 부족 역량 'SQL' 이 자격증으로 객관화하기 좋음 → RECOMMENDED.
        Decision d = CertificateNeedGate.evaluate(
                List.of("Python"), List.of(), "데이터 파이프라인 구축", "데이터 엔지니어",
                List.of(), List.of("SQL"), false);

        assertThat(d.active()).isTrue();
        assertThat(d.status()).isEqualTo(CertificateStrategyStatus.RECOMMENDED);
        assertThat(d.triggeredSignals()).containsExactly("GAP_CERTIFIABLE");
    }

    @Test
    void heldCertRelevantToPostingOnly_gateOn_useExisting() {
        // 보유 자격증명이 공고 텍스트에 등장하지만 우리 이름 사전엔 없어 '명시' 신호는 아님 → 강점 어필.
        Decision d = CertificateNeedGate.evaluate(
                List.of("성실성"), List.of("한국사능력검정 우대"), "일반 사무", "사무직",
                List.of("한국사능력검정"), List.of(), false);

        assertThat(d.active()).isTrue();
        assertThat(d.status()).isEqualTo(CertificateStrategyStatus.USE_EXISTING_AS_STRENGTH);
        assertThat(d.triggeredSignals()).containsExactly("HELD_CERT_RELEVANT");
    }

    @Test
    void userRequestedButNoObjectiveSignal_gateOn_optionalLowPriority() {
        Decision d = CertificateNeedGate.evaluate(
                List.of("Java"), List.of(), "웹 개발", "백엔드 개발자",
                List.of(), List.of("Spring"), true);

        assertThat(d.active()).isTrue();
        assertThat(d.status()).isEqualTo(CertificateStrategyStatus.OPTIONAL_LOW_PRIORITY);
        assertThat(d.triggeredSignals()).containsExactly("USER_REQUESTED");
    }

    @Test
    void nullInputsAreSafe_gateOff() {
        Decision d = CertificateNeedGate.evaluate(null, null, null, null, null, null, false);

        assertThat(d.active()).isFalse();
        assertThat(d.status()).isEqualTo(CertificateStrategyStatus.NOT_NEEDED);
    }
}
