package com.careertuner.applicationcase.service;

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
}
