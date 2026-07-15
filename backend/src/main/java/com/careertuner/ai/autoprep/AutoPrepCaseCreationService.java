package com.careertuner.ai.autoprep;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.ai.autoprep.mapper.AutoPrepCaseDedupeMapper;
import com.careertuner.applicationcase.dto.ApplicationCaseFromJobPostingResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseFromJobPostingRequest;
import com.careertuner.applicationcase.service.ApplicationCaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 공고 pending 파일 하나당 지원 건을 정확히 한 번 생성하고 재시도에는 같은 id를 반환한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPrepCaseCreationService {

    private final AutoPrepCaseDedupeMapper dedupeMapper;
    private final AutoPrepAttachmentLoader attachmentLoader;
    private final ApplicationCaseService applicationCaseService;

    /**
     * INSERT IGNORE의 유니크 키 잠금을 트랜잭션 경계로 사용한다. 첫 요청은 예약·생성·바인드를 한 번에
     * 커밋하고, 경쟁 요청은 그 커밋까지 기다린 뒤 이미 바인드된 id를 재사용한다.
     */
    @Transactional
    public Long createOrReuse(Long userId,
                              List<Long> jobPostingFileIds,
                              List<Long> attachmentFileIds) {
        Long fileId = firstFileId(jobPostingFileIds);
        if (fileId == null) {
            return null;
        }

        int reserved = dedupeMapper.insertReservation(userId, fileId);
        if (reserved == 0) {
            Long existingCaseId = dedupeMapper.findApplicationCaseIdForUpdate(userId, fileId);
            if (existingCaseId == null) {
                throw new IllegalStateException("AutoPrep 공고 예약에 지원 건이 연결되지 않았습니다.");
            }
            return existingCaseId;
        }

        String postingText = attachmentLoader.loadForRequest(
                        userId, jobPostingFileIds, jobPostingFileIds, attachmentFileIds).stream()
                .filter(PrepAttachment::hasText)
                .map(PrepAttachment::text)
                .findFirst()
                .orElse(null);
        if (postingText == null || postingText.isBlank()) {
            if (dedupeMapper.deleteUnboundReservation(userId, fileId) != 1) {
                throw new IllegalStateException("AutoPrep 공고의 미확정 예약을 해제하지 못했습니다.");
            }
            log.info("AutoPrep 공고 첨부에서 텍스트를 뽑지 못해 지원 건 생성 건너뜀(userId={}, fileId={})",
                    userId, fileId);
            return null;
        }

        ApplicationCaseFromJobPostingResponse created = applicationCaseService.createFromJobPosting(
                userId, new CreateApplicationCaseFromJobPostingRequest(
                        postingText, null, null, "TEXT", Boolean.FALSE));
        Long applicationCaseId = created != null && created.applicationCase() != null
                ? created.applicationCase().id()
                : null;
        if (applicationCaseId == null) {
            throw new IllegalStateException("AutoPrep 공고 지원 건 생성 결과에 id가 없습니다.");
        }
        if (dedupeMapper.bindApplicationCase(userId, fileId, applicationCaseId) != 1) {
            throw new IllegalStateException("AutoPrep 공고 예약에 지원 건을 연결하지 못했습니다.");
        }
        return applicationCaseId;
    }

    /**
     * 이미지/PDF 공고도 launcher가 먼저 올린 AUTO_PREP_PENDING fileId를 멱등키로 사용한다. 응답이 유실돼
     * 같은 fileId와 multipart를 재전송해도 새 지원 건을 만들지 않고 최초 id를 반환한다.
     */
    @Transactional
    public Long createOrReuseUpload(Long userId,
                                    Long pendingFileId,
                                    MultipartFile file,
                                    String sourceType) {
        attachmentLoader.validatePendingAutoPrepFile(userId, pendingFileId);
        int reserved = dedupeMapper.insertReservation(userId, pendingFileId);
        if (reserved == 0) {
            Long existingCaseId = dedupeMapper.findApplicationCaseIdForUpdate(userId, pendingFileId);
            if (existingCaseId == null) {
                throw new IllegalStateException("AutoPrep 공고 예약에 지원 건이 연결되지 않았습니다.");
            }
            return existingCaseId;
        }

        ApplicationCaseFromJobPostingResponse created = applicationCaseService.createFromJobPostingUpload(
                userId, file, sourceType, Boolean.FALSE, null, null, null);
        Long applicationCaseId = created != null && created.applicationCase() != null
                ? created.applicationCase().id()
                : null;
        if (applicationCaseId == null) {
            throw new IllegalStateException("AutoPrep 공고 지원 건 생성 결과에 id가 없습니다.");
        }
        if (dedupeMapper.bindApplicationCase(userId, pendingFileId, applicationCaseId) != 1) {
            throw new IllegalStateException("AutoPrep 공고 예약에 지원 건을 연결하지 못했습니다.");
        }
        return applicationCaseId;
    }

    private static Long firstFileId(List<Long> fileIds) {
        if (fileIds == null) {
            return null;
        }
        return fileIds.stream().filter(id -> id != null).findFirst().orElse(null);
    }
}
