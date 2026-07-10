package com.careertuner.file.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/** 업로드 바이트를 로컬 디스크에 저장/조회한다. (mediaDir 기준 상대 storageKey 사용) */
@Service
public class FileStorageService {

    private final FileStorageProperties properties;

    public FileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    public Stored store(Long ownerUserId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일을 선택해 주세요.");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드 파일 크기가 허용 범위를 초과했습니다.");
        }
        try {
            byte[] bytes = file.getBytes();
            String extension = extension(file.getOriginalFilename(), file.getContentType());
            String storedName = UUID.randomUUID() + extension;
            String relativeKey = ownerUserId + "/" + storedName;
            Path target = resolve(relativeKey);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return new Stored(relativeKey, bytes.length);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일을 저장하지 못했습니다.");
        }
    }

    public byte[] read(String storageKey) {
        try {
            Path path = resolve(storageKey);
            if (!Files.exists(path)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다.");
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일을 읽지 못했습니다.");
        }
    }

    /** 저장된 파일을 제거한다. 이미 사라진 파일은 메타데이터 정리를 막지 않도록 성공으로 본다. */
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일을 삭제하지 못했습니다.");
        }
    }

    /** 트랜잭션 롤백 보상용 스냅샷. 이미 없는 파일이면 null을 반환한다. */
    public byte[] snapshotIfExists(String storageKey) {
        try {
            Path path = resolve(storageKey);
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일 삭제를 준비하지 못했습니다.");
        }
    }

    /** DB 삭제가 롤백될 때 삭제 전 바이트를 같은 storageKey에 복원한다. */
    public void restore(String storageKey, byte[] bytes) {
        if (bytes == null) {
            return;
        }
        try {
            Path target = resolve(storageKey);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일을 복원하지 못했습니다.");
        }
    }

    /** storageKey 가 baseDir 밖을 가리키지 못하도록 정규화 후 검증한다. */
    private Path resolve(String storageKey) {
        Path base = Path.of(properties.getMediaDir()).toAbsolutePath().normalize();
        Path target = base.resolve(storageKey).normalize();
        if (!target.startsWith(base)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 파일 경로입니다.");
        }
        return target;
    }

    private String extension(String originalName, String contentType) {
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) {
                String ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
                if (ext.matches("\\.[a-z0-9]{1,8}")) {
                    return ext;
                }
            }
        }
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "audio/webm" -> ".webm";
            case "audio/mpeg" -> ".mp3";
            case "audio/wav", "audio/x-wav" -> ".wav";
            case "video/webm" -> ".webm";
            case "video/mp4" -> ".mp4";
            default -> "";
        };
    }

    public record Stored(String storageKey, long sizeBytes) {
    }
}
