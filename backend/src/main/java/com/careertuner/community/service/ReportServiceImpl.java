package com.careertuner.community.service;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommunityComment;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.CommentReport;
import com.careertuner.community.domain.PostReport;
import com.careertuner.community.dto.CreateReportRequest;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.ReportMapper;
import com.careertuner.community.moderation.event.ReportClassifyRequiredEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void createReport(CreateReportRequest request, Long userId) {
        switch (request.targetType()) {
            case POST -> reportPost(request, userId);
            case COMMENT -> reportComment(request, userId);
        }
    }

    private void reportPost(CreateReportRequest request, Long userId) {
        CommunityPost post = postMapper.findById(request.targetId());
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        if (Objects.equals(post.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인이 작성한 게시글은 신고할 수 없습니다.");
        }
        if (reportMapper.findPostReport(userId, request.targetId()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 신고한 게시글입니다.");
        }
        reportMapper.insertPostReport(PostReport.builder()
                .reporterId(userId)
                .postId(request.targetId())
                .reason(request.reason().name())
                .detail(request.detail())
                .status("PENDING")
                .build());
        eventPublisher.publishEvent(new ReportClassifyRequiredEvent(request.targetId()));
        log.info("게시글 신고 postId={} reporterId={}", request.targetId(), userId);
    }

    private void reportComment(CreateReportRequest request, Long userId) {
        CommunityComment comment = commentMapper.findById(request.targetId());
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        if (Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인이 작성한 댓글은 신고할 수 없습니다.");
        }
        if (reportMapper.findCommentReport(userId, request.targetId()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 신고한 댓글입니다.");
        }
        reportMapper.insertCommentReport(CommentReport.builder()
                .reporterId(userId)
                .commentId(request.targetId())
                .reason(request.reason().name())
                .detail(request.detail())
                .status("PENDING")
                .build());
        log.info("댓글 신고 commentId={} reporterId={}", request.targetId(), userId);
    }
}
