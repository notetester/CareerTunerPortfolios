package com.careertuner.admin.notice.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.notice.dto.AdminNoticeRequest;
import com.careertuner.admin.notice.dto.AdminNoticeResponse;
import com.careertuner.admin.notice.mapper.AdminNoticeMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminNoticeServiceImpl implements AdminNoticeService {

    private final AdminNoticeMapper noticeMapper;

    @Override
    public List<AdminNoticeResponse> getNotices(AuthUser authUser) {
        requireAdmin(authUser);
        return noticeMapper.findAll();
    }

    @Override
    @Transactional
    public AdminNoticeResponse createNotice(AuthUser authUser, AdminNoticeRequest request) {
        requireAdmin(authUser);
        String status = request.status() != null ? request.status().toUpperCase() : "DRAFT";
        boolean pinned = request.isPinned() != null && request.isPinned();
        boolean setPublishedAt = "PUBLISHED".equals(status);
        // SCHEDULED 면 예약 시각을 저장(없으면 거부), 그 외(PUBLISHED/DRAFT)는 NULL.
        LocalDateTime scheduledAt = resolveScheduledAt(status, request.scheduledAt(), null);
        noticeMapper.insert(
                request.title(),
                request.content(),
                request.category(),
                status,
                pinned,
                request.thumbnailUrl(),
                authUser.id(),
                scheduledAt,
                setPublishedAt
        );
        Long newId = noticeMapper.lastInsertId();
        return noticeMapper.findById(newId);
    }

    @Override
    @Transactional
    public AdminNoticeResponse updateNotice(AuthUser authUser, Long id, AdminNoticeRequest request) {
        requireAdmin(authUser);
        AdminNoticeResponse existing = noticeMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공지사항을 찾을 수 없습니다.");
        }
        String status = request.status() != null ? request.status().toUpperCase() : existing.getStatus();
        boolean pinned = request.isPinned() != null ? request.isPinned() : existing.isPinned();
        boolean setPublishedAt = "PUBLISHED".equals(status) && existing.getPublishedAt() == null;
        // SCHEDULED 유지/전환이면 새 시각(없으면 기존 보존), 다른 상태로 바뀌면 예약 시각을 비운다.
        LocalDateTime scheduledAt = resolveScheduledAt(status, request.scheduledAt(), existing.getScheduledAt());
        noticeMapper.update(
                id,
                request.title() != null ? request.title() : existing.getTitle(),
                request.content() != null ? request.content() : existing.getContent(),
                request.category() != null ? request.category() : existing.getCategory(),
                status,
                pinned,
                request.thumbnailUrl() != null ? request.thumbnailUrl() : existing.getThumbnailUrl(),
                scheduledAt,
                setPublishedAt
        );
        return noticeMapper.findById(id);
    }

    @Override
    @Transactional
    public void deleteNotice(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminNoticeResponse existing = noticeMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공지사항을 찾을 수 없습니다.");
        }
        noticeMapper.delete(id);
    }

    /**
     * 예약 발행 시각 결정. status=SCHEDULED 일 때만 시각을 둔다(조회 시점 판정용).
     * 그 외 상태는 NULL(예약 시각 비움). SCHEDULED 인데 새 값이 없으면 기존 값을 보존하고,
     * 그것도 없으면(생성/시각 누락) 거부한다.
     */
    private LocalDateTime resolveScheduledAt(String status, String requestedAt, LocalDateTime existing) {
        if (!"SCHEDULED".equals(status)) {
            return null;
        }
        LocalDateTime parsed = parseScheduledAt(requestedAt);
        if (parsed != null) {
            return parsed;
        }
        if (existing != null) {
            return existing;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "예약 발행은 예약 시각을 지정해야 합니다.");
    }

    /** ISO LocalDateTime 문자열("2026-07-01T09:00") 파싱. 빈 값은 null, 형식 오류는 INVALID_INPUT. */
    private LocalDateTime parseScheduledAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "예약 시각 형식이 올바르지 않습니다.");
        }
    }

    private void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }
}
