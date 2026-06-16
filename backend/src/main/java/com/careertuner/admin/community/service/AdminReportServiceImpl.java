package com.careertuner.admin.community.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.community.dto.AdminReportActionRequest;
import com.careertuner.admin.community.dto.AdminReportDetailResponse;
import com.careertuner.admin.community.dto.AdminReportListResponse;
import com.careertuner.admin.community.dto.AiOpinion;
import com.careertuner.admin.community.mapper.AdminReportMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.community.moderation.domain.AiResultStatus;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.PostAiResult;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.community.moderation.service.PostModerationService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportServiceImpl implements AdminReportService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportServiceImpl.class);

    private final AdminReportMapper reportMapper;
    private final PostAiResultMapper aiResultMapper;
    private final PostModerationService moderationService;
    private final ObjectMapper objectMapper;

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

        AiOpinion aiOpinion = null;
        if (!isComment) {
            PostAiResult aiResult = aiResultMapper.findByPostIdAndTaskType(
                    targetId, AiTaskType.REPORT);
            if (aiResult != null) {
                aiOpinion = buildAiOpinion(aiResult);
            }
        }

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
                .aiOpinion(aiOpinion)
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

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminReportDetailResponse reclassify(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        boolean isComment = id >= 1_000_000L;
        if (isComment) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글 신고는 AI 재검토를 지원하지 않습니다.");
        }

        Long postId = id;

        // 신고가 존재하는지 확인
        AdminReportListResponse base = reportMapper.findById(postId, "post");
        if (base == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다.");
        }

        // 비동기가 아닌 동기 호출 — 관리자가 결과를 바로 확인할 수 있도록
        moderationService.classify(postId);

        return getReportDetail(authUser, id);
    }

    private AiOpinion buildAiOpinion(PostAiResult aiResult) {
        Long elapsedMs = null;
        if (aiResult.getCreatedAt() != null && aiResult.getCompletedAt() != null) {
            elapsedMs = java.time.Duration.between(
                    aiResult.getCreatedAt(), aiResult.getCompletedAt()).toMillis();
        }

        AiOpinion.AiOpinionBuilder builder = AiOpinion.builder()
                .status(aiResult.getStatus().name())
                .model(aiResult.getModel())
                .completedAt(aiResult.getCompletedAt() != null
                        ? aiResult.getCompletedAt().toString() : null)
                .errorMessage(aiResult.getErrorMessage())
                .elapsedMs(elapsedMs);

        if (aiResult.getStatus() == AiResultStatus.COMPLETED
                && aiResult.getResultJson() != null) {
            try {
                ModerationResult result = objectMapper.readValue(
                        aiResult.getResultJson(), ModerationResult.class);
                builder.toxic(result.toxic())
                       .category(result.category())
                       .confidence(result.confidence());
            } catch (Exception e) {
                log.warn("AI 결과 JSON 파싱 실패: postId={}", aiResult.getPostId(), e);
            }
        }

        return builder.build();
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
