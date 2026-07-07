package com.careertuner.jobposting.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Cloudinary 저장 provider 설정 (careertuner.uploads.cloudinary.*). 자격증명은 셸 env 로만 주입한다
 * (로그/커밋 노출 금지). cloud-name/api-key/api-secret 이 모두 non-blank 여야 provider 빈이 등록된다
 * ({@link CloudinaryStorageConfig} 의 @ConditionalOnExpression).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.uploads.cloudinary")
public class CloudinaryProperties {

    private String cloudName;
    private String apiKey;
    private String apiSecret;
    /** 저장 폴더 prefix (public_id 앞부분). */
    private String folder = "application-postings";
    /** delivery type — 사적 원본이라 authenticated 권장(private 은 원본만 보호, 변환본 노출 가능). */
    private String deliveryType = "authenticated";
}
