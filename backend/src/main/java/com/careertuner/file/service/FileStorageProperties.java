package com.careertuner.file.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 파일 업로드 저장 설정. 기본값으로 즉시 동작하며 env 로 override 가능.
 * 음성/영상은 servlet multipart 한도(기본 10MB)도 함께 영향을 받으므로,
 * 큰 파일이 필요하면 SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE 도 함께 올린다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.file")
public class FileStorageProperties {

    private String mediaDir = ".uploads/media";
    private long maxFileSizeBytes = 10L * 1024 * 1024;
}
