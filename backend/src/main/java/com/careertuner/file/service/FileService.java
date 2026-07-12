package com.careertuner.file.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.dto.FileAssetResponse;
import com.careertuner.file.mapper.FileAssetMapper;

/** 업로드 메타 저장 + 다운로드. 음성/영상 등 면접 미디어가 주 사용처. */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final Set<String> ALLOWED_KINDS =
            Set.of("AUDIO", "VIDEO", "RESUME", "PORTFOLIO", "POSTING", "ATTACHMENT");

    private final FileStorageService storageService;
    private final FileAssetMapper fileAssetMapper;
    private final PendingFileCleanupWorker pendingFileCleanupWorker;

    public FileService(FileStorageService storageService, FileAssetMapper fileAssetMapper,
                       PendingFileCleanupWorker pendingFileCleanupWorker) {
        this.storageService = storageService;
        this.fileAssetMapper = fileAssetMapper;
        this.pendingFileCleanupWorker = pendingFileCleanupWorker;
    }

    @Transactional
    public FileAssetResponse upload(Long userId, String kind, String refType, Long refId, MultipartFile file) {
        String normalizedKind = normalizeKind(kind);
        FileStorageService.Stored stored = storageService.store(userId, file);
        try {
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
            registerUploadRollbackCleanup(stored.storageKey());
            return FileAssetResponse.from(asset);
        } catch (RuntimeException ex) {
            try {
                storageService.delete(stored.storageKey());
            } catch (RuntimeException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            throw ex;
        }
    }

    /**
     * 업로드만 되고 아직 연결 안 된(ref_id=null) 소유자 본인 파일들을 지정 ref 에 연결한다.
     * 남의 파일·이미 연결된 파일·없는 파일은 조용히 건너뛴다(첨부 하이재킹/재부모 방지). maxCount 까지만.
     */
    @Transactional
    public void linkOwnedFiles(Long userId, List<Long> fileIds, String refType, Long refId, int maxCount) {
        linkOwnedFilesOfKind(userId, fileIds, null, refType, refId, maxCount);
    }

    /**
     * 미연결 파일 중 소유자와 kind 가 모두 맞는 파일만 도메인 참조에 연결한다.
     * 조건부 UPDATE 로 검사와 갱신을 원자화해 동시 요청에서 다른 도메인으로 재부모화되지 않게 한다.
     */
    @Transactional
    public void linkOwnedFilesOfKind(Long userId, List<Long> fileIds, String expectedKind,
                                     String refType, Long refId, int maxCount) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        int linked = 0;
        for (Long fileId : fileIds) {
            if (linked >= maxCount) {
                break;
            }
            if (fileId == null) {
                continue;
            }
            linked += fileAssetMapper.updateRefIfOwnedAndUnlinked(
                    fileId, userId, expectedKind, refType, refId);
        }
    }

    /** 특정 ref(refType/refId)에 연결된 파일 메타 목록. 첨부 렌더용. */
    public List<FileAsset> findLinkedFiles(String refType, Long refId) {
        return fileAssetMapper.findByRef(refType, refId);
    }

    /** 단일 대기 파일을 소유자·종류·선언 용도를 모두 재검증해 도메인 row에 귀속한다. */
    @Transactional
    public FileAsset claimOwnedPendingFile(Long userId, Long fileId, String expectedKind,
                                           String expectedRefType, Long refId) {
        if (fileId == null || refId == null || fileAssetMapper.claimOwnedPendingFile(
                fileId, userId, expectedKind, expectedRefType, refId) != 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "전송 대기 중인 본인 파일을 확인할 수 없습니다.");
        }
        FileAsset linked = fileAssetMapper.findById(fileId);
        if (linked == null
                || !Objects.equals(userId, linked.getOwnerUserId())
                || !Objects.equals(expectedKind, linked.getKind())
                || !Objects.equals(expectedRefType, linked.getRefType())
                || !Objects.equals(refId, linked.getRefId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "파일 연결 상태를 확인할 수 없습니다.");
        }
        return linked;
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

    /** 호출 측에서 도메인 접근 권한을 이미 확인한 파일을 내려받는다. */
    public Download downloadAfterAccessCheck(Long fileId) {
        FileAsset asset = fileAssetMapper.findById(fileId);
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        return new Download(asset, storageService.read(asset.getStorageKey()));
    }

    /**
     * 소유자 본인의 아직 연결되지 않은 업로드만 메타데이터와 실제 저장 파일에서 삭제한다.
     * ref_type은 업로드 목적을 미리 기록할 수 있으므로 ref_id=null을 실제 미연결 기준으로 삼는다.
     */
    @Transactional
    public void deleteOwnedUnlinked(Long userId, Long fileId) {
        FileAsset asset = fileAssetMapper.findById(fileId);
        assertOwner(userId, asset);
        if (asset.getRefId() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연결된 파일은 해당 기능에서 삭제해 주세요.");
        }
        if (fileAssetMapper.deleteByIdAndOwnerIfPending(fileId, userId) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "파일이 이미 연결되었거나 삭제되었습니다.");
        }
        deleteStored(asset);
    }

    /**
     * 클라이언트 강제종료·오프라인으로 삭제 요청을 못 보낸 메신저 첨부를 제한 배치로 회수한다.
     * 최소 TTL 24시간과 최대 100건을 강제해 다른 장기 편집 파일 및 대량 삭제를 막는다.
     */
    public int cleanupStalePendingCollaborationAttachments(int olderThanHours, int requestedLimit) {
        int safeHours = Math.max(24, olderThanHours);
        int limit = Math.max(1, Math.min(100, requestedLimit));
        LocalDateTime cutoff = LocalDateTime.now().minusHours(safeHours);
        int deleted = 0;
        for (FileAsset asset : fileAssetMapper.findStalePendingCollaborationAttachments(cutoff, limit)) {
            try {
                if (pendingFileCleanupWorker.deleteStaleCollaborationAttachment(asset, cutoff)) {
                    deleted++;
                }
            } catch (RuntimeException ex) {
                log.warn("전송 대기 메신저 첨부 정리에 실패했습니다. fileId={}", asset.getId(), ex);
            }
        }
        return deleted;
    }

    /** 강제 종료 등으로 실행에 소비되지 못한 AutoPrep 입력 첨부를 제한 배치로 회수한다. */
    public int cleanupStalePendingAutoPrepAttachments(int olderThanHours, int requestedLimit) {
        int safeHours = Math.max(24, olderThanHours);
        int limit = Math.max(1, Math.min(100, requestedLimit));
        LocalDateTime cutoff = LocalDateTime.now().minusHours(safeHours);
        int deleted = 0;
        for (FileAsset asset : fileAssetMapper.findStalePendingAutoPrepAttachments(cutoff, limit)) {
            try {
                if (pendingFileCleanupWorker.deleteStaleAutoPrepAttachment(asset, cutoff)) {
                    deleted++;
                }
            } catch (RuntimeException ex) {
                log.warn("전송 대기 AutoPrep 첨부 정리에 실패했습니다. fileId={}", asset.getId(), ex);
            }
        }
        return deleted;
    }

    /** 앱 강제 종료 등으로 답변에 연결되지 못한 음성·영상 원본만 제한 배치로 회수한다. */
    public int cleanupStalePendingInterviewMedia(int olderThanHours, int requestedLimit) {
        int safeHours = Math.max(24, olderThanHours);
        int limit = Math.max(1, Math.min(100, requestedLimit));
        LocalDateTime cutoff = LocalDateTime.now().minusHours(safeHours);
        int deleted = 0;
        for (FileAsset asset : fileAssetMapper.findStalePendingInterviewMedia(cutoff, limit)) {
            try {
                if (pendingFileCleanupWorker.deleteStaleInterviewMedia(asset, cutoff)) {
                    deleted++;
                }
            } catch (RuntimeException ex) {
                log.warn("전송 대기 면접 원본 정리에 실패했습니다. fileId={}", asset.getId(), ex);
            }
        }
        return deleted;
    }

    /** cascade 삭제된 답변을 계속 가리키는 과거 음성·영상 원본의 DB 행과 실제 바이트를 함께 회수한다. */
    public int cleanupStaleOrphanedInterviewMedia(int olderThanHours, int requestedLimit) {
        int safeHours = Math.max(24, olderThanHours);
        int limit = Math.max(1, Math.min(100, requestedLimit));
        LocalDateTime cutoff = LocalDateTime.now().minusHours(safeHours);
        int deleted = 0;
        for (FileAsset asset : fileAssetMapper.findStaleOrphanedInterviewMedia(cutoff, limit)) {
            try {
                if (pendingFileCleanupWorker.deleteStaleOrphanedInterviewMedia(asset, cutoff)) {
                    deleted++;
                }
            } catch (RuntimeException ex) {
                log.warn("고아 면접 원본 정리에 실패했습니다. fileId={}", asset.getId(), ex);
            }
        }
        return deleted;
    }

    /** 도메인 서비스가 확정한 소유자·종류·참조가 모두 유지된 경우에만 연결 파일을 삭제한다. */
    @Transactional
    public void deleteOwnedLinked(Long userId, Long fileId, String expectedKind,
                                  String refType, Long refId) {
        FileAsset asset = fileAssetMapper.findById(fileId);
        assertOwner(userId, asset);
        if (!Objects.equals(expectedKind, asset.getKind())
                || !Objects.equals(refType, asset.getRefType())
                || !Objects.equals(refId, asset.getRefId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        if (fileAssetMapper.deleteByIdAndOwnerAndRef(
                fileId, userId, expectedKind, refType, refId) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        deleteStored(asset);
    }

    /**
     * 회원 개인정보 삭제 트랜잭션에서 이력서·포트폴리오 메타데이터와 실제 바이트를 함께 제거한다.
     * 공개 게시물 첨부나 면접 미디어는 각 도메인의 보존 정책을 따르므로 이 범위에 포함하지 않는다.
     */
    @Transactional
    public int deleteOwnedProfileFiles(Long userId) {
        int deleted = 0;
        for (FileAsset asset : fileAssetMapper.findProfileFilesByOwner(userId)) {
            if (fileAssetMapper.deleteProfileFileByIdAndOwner(asset.getId(), userId) == 1) {
                deleteStored(asset);
                deleted++;
            }
        }
        return deleted;
    }

    /** 배포 전에 탈퇴한 계정에 남아 있던 프로필 원본을 제한 배치로 회수한다. */
    @Transactional
    public int cleanupDeletedAccountProfileFiles(int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        int deleted = 0;
        for (FileAsset asset : fileAssetMapper.findDeletedOwnerProfileFiles(limit)) {
            if (fileAssetMapper.deleteProfileFileByIdAndOwner(asset.getId(), asset.getOwnerUserId()) == 1) {
                deleteStored(asset);
                deleted++;
            }
        }
        return deleted;
    }

    private void assertOwner(Long userId, FileAsset asset) {
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        if (!asset.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "파일을 삭제할 권한이 없습니다.");
        }
    }

    private void deleteStored(FileAsset asset) {
        byte[] rollbackBytes = storageService.snapshotIfExists(asset.getStorageKey());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        storageService.restore(asset.getStorageKey(), rollbackBytes);
                    }
                }
            });
        }
        // 물리 삭제 실패는 현재 DB 트랜잭션을 롤백하고, 이후 동기화가 삭제 전 바이트를 복원한다.
        storageService.delete(asset.getStorageKey());
    }

    private void registerUploadRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    storageService.delete(storageKey);
                }
            }
        });
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
