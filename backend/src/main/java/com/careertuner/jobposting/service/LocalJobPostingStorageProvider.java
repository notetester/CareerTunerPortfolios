package com.careertuner.jobposting.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/**
 * 로컬 디스크 기반 공고 파일 저장 provider(현행 동작 이관). reference = {@code local:application-postings/{caseId}/{name}}.
 * 경로 traversal·지원건 불일치 방어는 이 provider 안에서 처리한다.
 */
@Component
public class LocalJobPostingStorageProvider implements JobPostingStorageProvider {

    static final String SCHEME = "local";
    private static final String LOCAL_REFERENCE_PREFIX = "local:application-postings/";

    private final JobPostingUploadProperties properties;

    public LocalJobPostingStorageProvider(JobPostingUploadProperties properties) {
        this.properties = properties;
    }

    @Override
    public String scheme() {
        return SCHEME;
    }

    @Override
    public Written write(Long applicationCaseId, String storedName, byte[] bytes, String contentType) {
        try {
            Path targetDir = Path.of(properties.getJobPostingDir(), String.valueOf(applicationCaseId)).normalize();
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(storedName).normalize();
            Files.write(target, bytes);
            String reference = "local:application-postings/%d/%s".formatted(applicationCaseId, storedName);
            return new Written(reference, target);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "공고문 파일을 저장하지 못했습니다.");
        }
    }

    @Override
    public Loaded read(Long applicationCaseId, String fileReference) {
        if (fileReference == null || !fileReference.startsWith(LOCAL_REFERENCE_PREFIX)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장된 공고 파일 참조가 올바르지 않습니다.");
        }

        String relativeReference = fileReference.substring(LOCAL_REFERENCE_PREFIX.length());
        String[] parts = relativeReference.split("/", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장된 공고 파일 참조가 올바르지 않습니다.");
        }
        if (!String.valueOf(applicationCaseId).equals(parts[0])) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원 건과 공고 파일 참조가 일치하지 않습니다.");
        }

        String storedName = parts[1];
        if (storedName.contains("..") || storedName.contains("/") || storedName.contains("\\")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장된 공고 파일 참조가 올바르지 않습니다.");
        }

        Path uploadRoot = Path.of(properties.getJobPostingDir()).toAbsolutePath().normalize();
        Path target = uploadRoot.resolve(parts[0]).resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장된 공고 파일 참조가 올바르지 않습니다.");
        }

        try {
            byte[] bytes = Files.readAllBytes(target);
            return new Loaded(bytes, target, storedName);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "저장된 공고 파일을 찾을 수 없습니다.");
        }
    }
}
