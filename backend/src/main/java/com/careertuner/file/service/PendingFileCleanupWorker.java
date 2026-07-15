package com.careertuner.file.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.mapper.FileAssetMapper;

import lombok.RequiredArgsConstructor;

/** 고아 파일 한 건의 DB·물리 저장소 삭제를 독립 트랜잭션으로 제한한다. */
@Service
@RequiredArgsConstructor
public class PendingFileCleanupWorker {

    private final FileAssetMapper fileAssetMapper;
    private final FileStorageService storageService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteStaleCollaborationAttachment(FileAsset asset, LocalDateTime cutoff) {
        if (fileAssetMapper.deleteStalePendingCollaborationAttachment(
                asset.getId(), asset.getOwnerUserId(), cutoff) != 1) {
            return false;
        }
        deleteStored(asset);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteStaleAutoPrepAttachment(FileAsset asset, LocalDateTime cutoff) {
        if (fileAssetMapper.deleteStalePendingAutoPrepAttachment(
                asset.getId(), asset.getOwnerUserId(), cutoff) != 1) {
            return false;
        }
        deleteStored(asset);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteStaleProfileImport(FileAsset asset, LocalDateTime cutoff) {
        if (fileAssetMapper.deleteStalePendingProfileImport(
                asset.getId(), asset.getOwnerUserId(), cutoff) != 1) {
            return false;
        }
        deleteStored(asset);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteStaleInterviewMedia(FileAsset asset, LocalDateTime cutoff) {
        if (fileAssetMapper.deleteStalePendingInterviewMedia(
                asset.getId(), asset.getOwnerUserId(), cutoff) != 1) {
            return false;
        }
        deleteStored(asset);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteStaleOrphanedInterviewMedia(FileAsset asset, LocalDateTime cutoff) {
        if (fileAssetMapper.deleteStaleOrphanedInterviewMedia(
                asset.getId(), asset.getOwnerUserId(), cutoff) != 1) {
            return false;
        }
        deleteStored(asset);
        return true;
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
        storageService.delete(asset.getStorageKey());
    }
}
