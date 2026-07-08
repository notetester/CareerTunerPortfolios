package com.careertuner.jobposting.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.service.JobPostingStorageProvider.Loaded;
import com.careertuner.jobposting.service.JobPostingStorageProvider.Written;

/**
 * 공고 업로드 파일의 저장 facade. 검증(size·type)·네이밍·{@link StoredJobPostingFile} 조립은 여기서 공용 처리하고,
 * 원시 바이트 저장/조회는 {@link JobPostingStorageProvider} 에 위임한다.
 *
 * <ul>
 *   <li>{@code store()} 는 {@code careertuner.uploads.storage-provider}(default {@code local})로 고른 provider 를 쓴다.</li>
 *   <li>{@code load()} 는 env 무관, <b>reference 의 scheme prefix</b>로 provider 를 라우팅한다(기존 {@code local:} 하위호환).</li>
 * </ul>
 */
@Service
public class JobPostingFileStorage {

    private static final long BYTES_PER_KB = 1024L;
    private static final long BYTES_PER_MB = BYTES_PER_KB * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final JobPostingUploadLimitPolicy uploadLimitPolicy;
    private final Map<String, JobPostingStorageProvider> providersByScheme;
    private final JobPostingStorageProvider storeProvider;

    public JobPostingFileStorage(JobPostingUploadProperties properties,
                                 JobPostingUploadLimitPolicy uploadLimitPolicy,
                                 List<JobPostingStorageProvider> providers) {
        this.uploadLimitPolicy = uploadLimitPolicy;
        this.providersByScheme = providers.stream()
                .collect(Collectors.toUnmodifiableMap(JobPostingStorageProvider::scheme, Function.identity()));
        String configured = properties.getStorageProvider() == null
                ? LocalJobPostingStorageProvider.SCHEME
                : properties.getStorageProvider().trim().toLowerCase(Locale.ROOT);
        JobPostingStorageProvider selected = providersByScheme.get(configured);
        if (selected == null) {
            throw new IllegalStateException("Unknown job posting storage provider: '" + configured
                    + "'. available=" + providersByScheme.keySet());
        }
        this.storeProvider = selected;
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

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "공고문 파일을 저장하지 못했습니다.");
        }
        String storedName = UUID.randomUUID() + extension(contentType);
        Written written = storeProvider.write(applicationCaseId, storedName, bytes, contentType);
        return new StoredJobPostingFile(
                normalizedSourceType,
                written.reference(),
                file.getOriginalFilename() == null ? storedName : file.getOriginalFilename(),
                contentType,
                written.path(),
                bytes);
    }

    public StoredJobPostingFile load(Long applicationCaseId, String fileReference, String sourceType) {
        String normalizedSourceType = normalizeUploadSourceType(sourceType);
        JobPostingStorageProvider provider = providerForReference(fileReference);
        Loaded loaded = provider.read(applicationCaseId, fileReference);
        return new StoredJobPostingFile(
                normalizedSourceType,
                fileReference,
                loaded.storedName(),
                contentType(normalizedSourceType, loaded.storedName()),
                loaded.path(),
                loaded.bytes());
    }

    /** reference 의 scheme prefix(콜론 앞)로 provider 를 고른다. 알 수 없는 scheme 은 거부한다. */
    private JobPostingStorageProvider providerForReference(String fileReference) {
        if (fileReference == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장된 공고 파일 참조가 올바르지 않습니다.");
        }
        int colon = fileReference.indexOf(':');
        if (colon <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장된 공고 파일 참조가 올바르지 않습니다.");
        }
        String scheme = fileReference.substring(0, colon).toLowerCase(Locale.ROOT);
        JobPostingStorageProvider provider = providersByScheme.get(scheme);
        if (provider == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장된 공고 파일 참조가 올바르지 않습니다.");
        }
        return provider;
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
