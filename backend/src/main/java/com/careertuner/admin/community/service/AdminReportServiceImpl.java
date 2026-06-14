package com.careertuner.admin.community.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.community.dto.AdminReportActionRequest;
import com.careertuner.admin.community.dto.AdminReportDetailResponse;
import com.careertuner.admin.community.dto.AdminReportListResponse;
import com.careertuner.admin.community.mapper.AdminReportMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportServiceImpl implements AdminReportService {

    private final AdminReportMapper reportMapper;

    @Override
    public List<AdminReportListResponse> getReports(AuthUser authUser, String status) {
        requireAdmin(authUser);
        return reportMapper.findAll(status);
    }

    @Override
    public AdminReportDetailResponse getReportDetail(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        // id >= 1000000 이면 댓글 신고, 아니면 게시글 신고
        boolean isComment = id >= 1_000_000L;
        String type = isComment ? "comment" : "post";
        Long targetId = isComment ? id - 1_000_000L : id;

        AdminReportListResponse base = reportMapper.findById(targetId, type);
        if (base == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다.");
        }
        var reasons = reportMapper.findReasonCounts(targetId, type);
        return AdminReportDetailResponse.builder()
                .id(base.getId())
                .reason(base.getReason())
                .type(base.getType())
                .cnt(base.getCnt())
                .title(base.getTitle())
                .excerpt(base.getExcerpt())
                .cat(base.getCat())
                .catKey(base.getCatKey())
                .author(base.getAuthor())
                .time(base.getTime())
                .status(base.getStatus())
                .action(base.getAction())
                .reasons(reasons)
                .build();
    }

    @Override
    @Transactional
    public AdminReportDetailResponse takeAction(AuthUser authUser, Long id, AdminReportActionRequest request) {
        requireAdmin(authUser);
        boolean isComment = id >= 1_000_000L;
        Long targetId = isComment ? id - 1_000_000L : id;

        String action = request.action().toUpperCase();
        if (!isComment) {
            switch (action) {
                case "HIDDEN" -> {
                    reportMapper.updatePostReportStatus(targetId, "CONFIRMED", "HIDDEN");
                    reportMapper.updatePostStatus(targetId, "HIDDEN");
                }
                case "DELETED" -> {
                    reportMapper.updatePostReportStatus(targetId, "CONFIRMED", "DELETED");
                    reportMapper.updatePostStatus(targetId, "DELETED");
                }
                case "DISMISSED" -> reportMapper.updatePostReportStatus(targetId, "DISMISSED", "NONE");
                default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 조치입니다.");
            }
        } else {
            switch (action) {
                case "HIDDEN" -> {
                    reportMapper.updateCommentReportStatus(targetId, "CONFIRMED", "HIDDEN");
                    reportMapper.updateCommentStatus(targetId, "HIDDEN");
                }
                case "DELETED" -> {
                    reportMapper.updateCommentReportStatus(targetId, "CONFIRMED", "DELETED");
                    reportMapper.updateCommentStatus(targetId, "DELETED");
                }
                case "DISMISSED" -> reportMapper.updateCommentReportStatus(targetId, "DISMISSED", "NONE");
                default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 조치입니다.");
            }
        }
        return getReportDetail(authUser, id);
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
