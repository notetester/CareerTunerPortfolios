package com.careertuner.jobposting.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

@Service
public class JobPostingFileStorage {

    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final JobPostingUploadProperties properties;

    public JobPostingFileStorage(JobPostingUploadProperties properties) {
        this.properties = properties;
    }

    public StoredJobPostingFile store(Long applicationCaseId, MultipartFile file, String sourceType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일을 선택해 주세요.");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드 파일 크기가 허용 범위를 초과했습니다.");
        }

        String normalizedSourceType = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!"PDF".equals(normalizedSourceType) && !"IMAGE".equals(normalizedSourceType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "PDF 또는 이미지 파일만 업로드할 수 있습니다.");
        }
        if ("PDF".equals(normalizedSourceType) && !"application/pdf".equals(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "PDF 파일 형식만 업로드할 수 있습니다.");
        }
        if ("IMAGE".equals(normalizedSourceType) && !IMAGE_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "PNG, JPG, WEBP, GIF 이미지만 업로드할 수 있습니다.");
        }

        try {
            byte[] bytes = file.getBytes();
            String extension = extension(contentType);
            String storedName = UUID.randomUUID() + extension;
            Path targetDir = Path.of(properties.getJobPostingDir(), String.valueOf(applicationCaseId)).normalize();
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(storedName).normalize();
            Files.write(target, bytes);
            String reference = "local:application-postings/%d/%s".formatted(applicationCaseId, storedName);
            return new StoredJobPostingFile(
                    normalizedSourceType,
                    reference,
                    file.getOriginalFilename() == null ? storedName : file.getOriginalFilename(),
                    contentType,
                    target,
                    bytes);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "공고문 파일을 저장하지 못했습니다.");
        }
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };
    }

    public record StoredJobPostingFile(
            String sourceType,
            String fileReference,
            String originalFilename,
            String contentType,
            Path path,
            byte[] bytes
    ) {
    }
}
