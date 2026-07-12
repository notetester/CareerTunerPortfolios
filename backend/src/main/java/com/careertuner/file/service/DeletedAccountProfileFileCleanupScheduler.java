package com.careertuner.file.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 탈퇴 전 구버전 서비스가 남긴 이력서·포트폴리오 원본을 실제 저장소까지 점진적으로 제거한다. */
@Component
@RequiredArgsConstructor
public class DeletedAccountProfileFileCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeletedAccountProfileFileCleanupScheduler.class);
    private final FileService fileService;

    @Scheduled(
            initialDelayString = "${careertuner.account-deletion.profile-file-cleanup-initial-delay-ms:30000}",
            fixedDelayString = "${careertuner.account-deletion.profile-file-cleanup-delay-ms:3600000}")
    public void cleanup() {
        try {
            int deleted = fileService.cleanupDeletedAccountProfileFiles(100);
            if (deleted > 0) {
                log.info("탈퇴 계정 프로필 원본 파일 {}건을 정리했습니다.", deleted);
            }
        } catch (RuntimeException ex) {
            log.warn("탈퇴 계정 프로필 원본 파일 정리에 실패했습니다.", ex);
        }
    }
}
