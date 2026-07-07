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

    private static final String LOCAL_REFERENCE_PREFIX = "local:application-postings/";
    private static final long BYTES_PER_KB = 1024L;
    private static final long BYTES_PER_MB = BYTES_PER_KB * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final JobPostingUploadProperties properties;
    private final JobPostingUploadLimitPolicy uploadLimitPolicy;

    public JobPostingFileStorage(JobPostingUploadProperties properties,
                                 JobPostingUploadLimitPolicy uploadLimitPolicy) {
        this.properties = properties;
        this.uploadLimitPolicy = uploadLimitPolicy;
    }

    public StoredJobPostingFile store(Long applicationCaseId, MultipartFile file, String sourceType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일을 선택해 주세요.");
        }
        long maxBytes = uploadLimitPolicy.currentMaxBytes();
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "공고 파일은 " + formatFileSize(maxBytes) + " 이하만 업로드할 수 있습니다.");
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

    public StoredJobPostingFile load(Long applicationCaseId, String fileReference, String sourceType) {
        String normalizedSourceType = normalizeUploadSourceType(sourceType);
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
            return new StoredJobPostingFile(
                    normalizedSourceType,
                    fileReference,
                    storedName,
                    contentType(normalizedSourceType, storedName),
                    target,
                    bytes);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "저장된 공고 파일을 찾을 수 없습니다.");
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

    private String formatFileSize(long bytes) {
        if (bytes >= BYTES_PER_MB) {
            double mb = bytes / (double) BYTES_PER_MB;
            return bytes % BYTES_PER_MB == 0
                    ? (bytes / BYTES_PER_MB) + "MB"
                    : String.format(Locale.ROOT, "%.1fMB", mb);
        }
        if (bytes >= BYTES_PER_KB) {
            double kb = bytes / (double) BYTES_PER_KB;
            return bytes % BYTES_PER_KB == 0
                    ? (bytes / BYTES_PER_KB) + "KB"
                    : String.format(Locale.ROOT, "%.1fKB", kb);
        }
        return bytes + "B";
    }

    private String normalizeUploadSourceType(String sourceType) {
        String normalizedSourceType = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        if (!"PDF".equals(normalizedSourceType) && !"IMAGE".equals(normalizedSourceType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "PDF 또는 이미지 파일만 사용할 수 있습니다.");
        }
        return normalizedSourceType;
    }

    private String contentType(String sourceType, String filename) {
        if ("PDF".equals(sourceType)) {
            return "application/pdf";
        }
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".png")) {
            return "image/png";
        }
        if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerFilename.endsWith(".webp")) {
            return "image/webp";
        }
        if (lowerFilename.endsWith(".gif")) {
            return "image/gif";
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일 형식이 올바르지 않습니다.");
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
