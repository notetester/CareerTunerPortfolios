package com.careertuner.admin.notice.service;

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
        noticeMapper.insert(
                request.title(),
                request.content(),
                request.category(),
                status,
                pinned,
                request.thumbnailUrl(),
                authUser.id(),
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
        noticeMapper.update(
                id,
                request.title() != null ? request.title() : existing.getTitle(),
                request.content() != null ? request.content() : existing.getContent(),
                request.category() != null ? request.category() : existing.getCategory(),
                status,
                pinned,
                request.thumbnailUrl() != null ? request.thumbnailUrl() : existing.getThumbnailUrl(),
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

    private void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }
}
