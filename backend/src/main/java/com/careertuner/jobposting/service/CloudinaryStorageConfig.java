package com.careertuner.jobposting.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

/**
 * Cloudinary 저장 provider 배선 — 자격증명(cloud-name/api-key/api-secret)이 <b>모두 non-blank</b>일 때만 빈 등록.
 *
 * <p>미설정 또는 <b>빈 문자열</b>이면 {@link Cloudinary}·{@link CloudinaryJobPostingStorageProvider} 빈이 없어
 * default local 로만 부팅한다(키 없이 앱 기동 보장). {@code @ConditionalOnProperty} 는 빈 문자열도 "존재"로 판정해
 * 오탐하므로, non-blank 를 SpEL 로 명시 검증한다.
 */
@Configuration
@ConditionalOnExpression(
        "!'${careertuner.uploads.cloudinary.cloud-name:}'.isBlank() "
        + "and !'${careertuner.uploads.cloudinary.api-key:}'.isBlank() "
        + "and !'${careertuner.uploads.cloudinary.api-secret:}'.isBlank()")
public class CloudinaryStorageConfig {

    @Bean
    public Cloudinary cloudinary(CloudinaryProperties properties) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.getCloudName(),
                "api_key", properties.getApiKey(),
                "api_secret", properties.getApiSecret(),
                "secure", true));
    }

    @Bean
    public CloudinaryJobPostingStorageProvider cloudinaryJobPostingStorageProvider(
            Cloudinary cloudinary, CloudinaryProperties properties) {
        return new CloudinaryJobPostingStorageProvider(cloudinary, properties);
    }
}
