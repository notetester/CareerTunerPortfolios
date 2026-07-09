package com.careertuner.jobposting.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.uploads")
public class JobPostingUploadProperties {

    private String jobPostingDir = ".uploads/application-postings";
    private long maxFileSizeBytes = 10 * 1024 * 1024;
    /** store 시 사용할 저장 provider scheme(default local). load 는 reference prefix 로 라우팅되어 이 값과 무관. */
    private String storageProvider = "local";
}
