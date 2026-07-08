package com.careertuner.community.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 커뮤니티/공지/FAQ 본문 이미지 저장 설정 (careertuner.community.image.*).
 *
 * <p>B(공고)가 도입한 Cloudinary SDK/빈을 그대로 재사용하되, 본문 이미지는 <b>공개 표시</b>가 목적이라
 * B의 authenticated(사적) delivery 와 달리 <b>public(type=upload)</b> 로 올린다. 폴더는 B(application-postings)와
 * 겹치지 않도록 owner 접두사 {@code f/} 아래 scope(community/notice/faq)로 분리한다.
 *
 * <p>Cloudinary 자격증명({@code careertuner.uploads.cloudinary.*})이 없으면 {@link com.cloudinary.Cloudinary}
 * 빈이 없어 업로드가 SERVICE_UNAVAILABLE 로 degrade 한다(키 없이도 앱은 기동).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.community.image")
public class CommunityImageProperties {

    /** Cloudinary 폴더 owner 접두사. 실제 경로는 {@code {folder}/{scope}/{ownerId}/{uuid}}. */
    private String folder = "f";

    /** 이미지 1장 최대 크기(바이트). 기본 5MB. */
    private long maxFileSizeBytes = 5L * 1024 * 1024;
}
