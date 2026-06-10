package com.careertuner.payment.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "careertuner.toss.payments")
public class TossPaymentProperties {

    private String secretKey = "";
    private String confirmUrl = "https://api.tosspayments.com/v1/payments/confirm";
    private String successUrl = "http://localhost:5173/billing/success";
    private String failUrl = "http://localhost:5173/billing/fail";
    private Duration timeout = Duration.ofSeconds(10);

    /** 외부 Toss 승인 API를 호출할 수 있는 최소 설정이 들어왔는지 확인한다. */
    public boolean configured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
