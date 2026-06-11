package com.careertuner.file.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.dto.FileAssetResponse;
import com.careertuner.file.mapper.FileAssetMapper;

/** 업로드 메타 저장 + 다운로드. 음성/영상 등 면접 미디어가 주 사용처. */
@Service
public class FileService {

    private static final Set<String> ALLOWED_KINDS =
            Set.of("AUDIO", "VIDEO", "RESUME", "PORTFOLIO", "POSTING", "ATTACHMENT");

    private final FileStorageService storageService;
    private final FileAssetMapper fileAssetMapper;

    public FileService(FileStorageService storageService, FileAssetMapper fileAssetMapper) {
        this.storageService = storageService;
        this.fileAssetMapper = fileAssetMapper;
    }

    @Transactional
    public FileAssetResponse upload(Long userId, String kind, String refType, Long refId, MultipartFile file) {
        String normalizedKind = normalizeKind(kind);
        FileStorageService.Stored stored = storageService.store(userId, file);

        FileAsset asset = FileAsset.builder()
                .ownerUserId(userId)
                .kind(normalizedKind)
                .refType(blankToNull(refType))
                .refId(refId)
                .originalName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(stored.sizeBytes())
                .storageKey(stored.storageKey())
                .build();
        fileAssetMapper.insert(asset);
        return FileAssetResponse.from(asset);
    }

    /** 소유자 확인 후 다운로드용 메타 + 바이트를 함께 반환한다. */
    public Download download(Long userId, Long fileId) {
        FileAsset asset = fileAssetMapper.findById(fileId);
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        if (!asset.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "파일에 접근할 권한이 없습니다.");
        }
        return new Download(asset, storageService.read(asset.getStorageKey()));
    }

    private String normalizeKind(String kind) {
        String upper = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_KINDS.contains(upper)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 파일 종류입니다.");
        }
        return upper;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Download(FileAsset asset, byte[] bytes) {
    }
}
