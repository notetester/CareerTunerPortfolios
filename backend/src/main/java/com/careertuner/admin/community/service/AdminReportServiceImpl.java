package com.careertuner.admin.community.service;

import java.time.LocalDateTime;
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
import com.careertuner.admin.common.security.AdminAccountMutationGuard;
import com.careertuner.admin.common.security.AdminAccountState;
import com.careertuner.admin.permission.service.EffectivePermissionService;
import com.careertuner.admin.user.mapper.AdminUserMapper;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.community.domain.CommunityComment;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.domain.AiResultStatus;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.PostAiResult;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.community.moderation.service.ModerationSettingService;
import com.careertuner.community.moderation.service.PostModerationService;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportServiceImpl implements AdminReportService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportServiceImpl.class);

    private static final String ACTIVE = "ACTIVE";
    private static final String BLOCKED = "BLOCKED";

    private final AdminReportMapper reportMapper;
    private final PostAiResultMapper aiResultMapper;
    private final PostModerationService moderationService;
    private final CommunityCommentMapper commentMapper;
    private final CommunityPostMapper postMapper;
    private final ObjectMapper objectMapper;
    // 작성자 차단(BLOCK_AUTHOR): UserSanctionService 와 동일한 A 도메인 인프라를 재사용한다
    // (상태 변경 + 이력 + 세션 해지 + ACCOUNT_BLOCKED 알림). 임계 기반 sanctionIfNeeded 와 달리
    // 관리자 판단의 즉시 차단이므로 actor 에 처리 관리자를 기록한다.
    private final AdminUserMapper userMapper;
    private final AuthMapper authMapper;
    private final NotificationService notificationService;
    private final ModerationSettingService settingService;
    private final AdminAccountMutationGuard accountMutationGuard;
    private final EffectivePermissionService effectivePermissionService;

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
        if (request == null || request.action() == null || request.action().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신고 조치를 선택해 주세요.");
        }
        String action = request.action().trim().toUpperCase(java.util.Locale.ROOT);
        requireActionPermissions(authUser, action);
        boolean isComment = id >= 1_000_000L;
        Long targetId = isComment ? id - 1_000_000L : id;
        Long adminId = authUser.id();
        String type = isComment ? "comment" : "post";

        // 처리 결과 알림 대상(처리 직전 PENDING 신고자)을 상태 갱신 전에 확보한다.
        List<Long> reporterIds = reportMapper.findPendingReporterIds(targetId, type);

        if (!isComment) {
            switch (action) {
                case "HIDDEN" -> {
                    // 정책: DELETED는 종착(불가역) 상태 — DELETED 게시글은 HIDDEN으로 역행 불가.
                    guardPostNotDeleted(targetId);
                    reportMapper.updatePostReportStatus(targetId, "CONFIRMED", "HIDDEN", adminId);
                    reportMapper.updatePostStatus(targetId, "HIDDEN");
                }
                case "DELETED" -> {
                    // 정책: DELETED는 종착 상태 — 이미 DELETED면 재처리 거부(멱등성/감사 일관성).
                    guardPostNotDeleted(targetId);
                    reportMapper.updatePostReportStatus(targetId, "CONFIRMED", "DELETED", adminId);
                    reportMapper.updatePostStatus(targetId, "DELETED");
                }
                case "BLOCK_AUTHOR" -> {
                    // 콘텐츠는 유지하고 작성자 계정만 차단.
                    CommunityPost post = requirePost(targetId);
                    blockAuthor(authUser, post.getUserId());
                    reportMapper.updatePostReportStatus(targetId, "CONFIRMED", "BLOCK_AUTHOR", adminId);
                }
                case "DELETE_AND_BLOCK" -> {
                    guardPostNotDeleted(targetId);
                    CommunityPost post = requirePost(targetId);
                    blockAuthor(authUser, post.getUserId());
                    reportMapper.updatePostReportStatus(targetId, "CONFIRMED", "DELETE_AND_BLOCK", adminId);
                    reportMapper.updatePostStatus(targetId, "DELETED");
                }
                case "DISMISSED" -> reportMapper.updatePostReportStatus(targetId, "DISMISSED", "NONE", adminId);
                default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 조치입니다.");
            }
        } else {
            switch (action) {
                case "HIDDEN" -> {
                    reportMapper.updateCommentReportStatus(targetId, "CONFIRMED", "HIDDEN", adminId);
                    hideCommentWithCount(targetId);
                }
                case "DELETED" -> {
                    reportMapper.updateCommentReportStatus(targetId, "CONFIRMED", "DELETED", adminId);
                    deleteCommentWithCount(targetId);
                }
                case "BLOCK_AUTHOR" -> {
                    CommunityComment comment = requireComment(targetId);
                    blockAuthor(authUser, comment.getUserId());
                    reportMapper.updateCommentReportStatus(targetId, "CONFIRMED", "BLOCK_AUTHOR", adminId);
                }
                case "DELETE_AND_BLOCK" -> {
                    CommunityComment comment = requireComment(targetId);
                    blockAuthor(authUser, comment.getUserId());
                    reportMapper.updateCommentReportStatus(targetId, "CONFIRMED", "DELETE_AND_BLOCK", adminId);
                    deleteCommentWithCount(targetId);
                }
                case "RESTORE", "PUBLISHED" -> {
                    reportMapper.updateCommentReportStatus(targetId, "DISMISSED", "NONE", adminId);
                    restoreCommentWithCount(targetId);
                }
                case "DISMISSED" -> reportMapper.updateCommentReportStatus(targetId, "DISMISSED", "NONE", adminId);
                default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 조치입니다.");
            }
        }

        // 신고자 결과 알림 — best-effort(알림 실패가 처리 자체를 되돌리면 안 된다).
        notifyReporters(reporterIds, isComment, targetId, action);

        return getReportDetail(authUser, id);
    }

    @Override
    @Transactional
    public AdminReportDetailResponse reactivate(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        boolean isComment = id >= 1_000_000L;
        Long targetId = isComment ? id - 1_000_000L : id;
        String type = isComment ? "comment" : "post";

        if (reportMapper.findById(targetId, type) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다.");
        }
        int updated = isComment
                ? reportMapper.reactivateCommentReports(targetId)
                : reportMapper.reactivatePostReports(targetId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "재활성화할 종결(기각/취소) 신고가 없습니다.");
        }
        log.info("신고 재활성화: type={}, targetId={}, rows={}, adminId={}", type, targetId, updated, authUser.id());
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

    /**
     * 작성자 계정 차단 — UserSanctionService 의 차단 시퀀스를 관리자 즉시 조치 형태로 재사용.
     * 가드: 본인 금지, 관리자 계열(보호 role) 금지, ACTIVE 가 아니면(이미 차단/휴면/탈퇴) 재차단 금지.
     */
    private void blockAuthor(AuthUser authUser, Long authorId) {
        if (authorId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "작성자 정보를 찾을 수 없습니다.");
        }
        if (authorId.equals(authUser.id())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인 계정은 차단할 수 없습니다.");
        }
        AdminAccountState author = accountMutationGuard.validateStatusChangeOrSkipDeleted(
                authUser, authorId, BLOCKED);
        if (author == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "작성자 계정을 찾을 수 없습니다.");
        }
        if ("ADMIN".equals(author.role()) || "SUPER_ADMIN".equals(author.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 계정은 신고 콘솔에서 차단할 수 없습니다.");
        }
        if (!ACTIVE.equals(author.status())) {
            // 이미 차단/휴면/탈퇴 상태 — 재차단 대신 건너뛴다(수동 조치 보존, DELETE_AND_BLOCK 의 삭제는 계속 진행).
            log.info("작성자 차단 생략(이미 비활성): authorId={}, status={}", authorId, author.status());
            return;
        }

        int blockDays = settingService.getBlockDays();
        // blockedUntil 은 DB NOW()(serverTimezone=Asia/Seoul)와 비교되므로 KST 벽시계로 저장한다(UserSanctionService 와 동일).
        LocalDateTime blockedUntil = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(blockDays);
        String reason = "신고 처리(작성자 차단) — 관리자 조치";

        userMapper.updateStatus(authorId, BLOCKED, reason, blockedUntil, authUser.id());
        userMapper.insertStatusHistory(authorId, authUser.id(), ACTIVE, BLOCKED, reason, null, blockedUntil);
        authMapper.revokeAllForUser(authorId);

        // 차단 통보 — 기존 등록 타입(ACCOUNT_BLOCKED) 재사용. 실패해도 차단 자체는 유지(best-effort).
        try {
            notificationService.notify(Notification.builder()
                    .userId(authorId)
                    .type("ACCOUNT_BLOCKED")
                    .targetType("USER")
                    .targetId(authorId)
                    .title("커뮤니티 활동이 제한되었습니다")
                    .message("신고 처리 결과에 따라 " + blockDays + "일간 이용이 제한됩니다.")
                    .link("/support/contact")
                    .build());
        } catch (Exception ex) {
            log.warn("작성자 차단 알림 발송 실패: authorId={}", authorId, ex);
        }

        log.warn("신고 처리 작성자 차단: authorId={}, adminId={}, blockedUntil={}", authorId, authUser.id(), blockedUntil);
    }

    private void requireActionPermissions(AuthUser authUser, String action) {
        if ("SUPER_ADMIN".equals(authUser.role())) {
            return;
        }
        String contentPermission = "DELETED".equals(action) || "DELETE_AND_BLOCK".equals(action)
                ? "CONTENT_DELETE"
                : "CONTENT_UPDATE";
        requirePermission(authUser, contentPermission);
        if ("BLOCK_AUTHOR".equals(action) || "DELETE_AND_BLOCK".equals(action)) {
            requirePermission(authUser, "USER_UPDATE");
        }
    }

    private void requirePermission(AuthUser authUser, String required) {
        if (!effectivePermissionService.hasAny(authUser.id(), required)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "이 신고 조치를 수행할 세부 권한이 없습니다. (필요 권한: " + required + ")");
        }
    }

    /** 신고자에게 처리 결과 알림. 기존 등록 타입(NOTICE 계열) 사용 — 새 타입 등록 금지 규칙 준수. */
    private void notifyReporters(List<Long> reporterIds, boolean isComment, Long targetId, String action) {
        if (reporterIds == null || reporterIds.isEmpty()) {
            return;
        }
        boolean dismissed = "DISMISSED".equals(action);
        String actionLabel = switch (action) {
            case "HIDDEN" -> "숨김 처리";
            case "DELETED" -> "삭제";
            case "BLOCK_AUTHOR" -> "작성자 차단";
            case "DELETE_AND_BLOCK" -> "삭제 및 작성자 차단";
            case "RESTORE", "PUBLISHED" -> "복원(기각)";
            default -> null;
        };
        String title = dismissed ? "신고가 반려되었습니다" : "신고가 처리되었습니다";
        String message = dismissed
                ? "접수하신 신고를 검토했으나 조치 없이 종결되었습니다."
                : "접수하신 신고가 처리되었습니다." + (actionLabel != null ? " (조치: " + actionLabel + ")" : "");

        for (Long reporterId : reporterIds) {
            try {
                notificationService.notify(Notification.builder()
                        .userId(reporterId)
                        .type("NOTICE")
                        .targetType(isComment ? "COMMENT" : "POST")
                        .targetId(targetId)
                        .title(title)
                        .message(message)
                        .link("/community")
                        .build());
            } catch (Exception ex) {
                // 알림 실패가 신고 처리를 되돌리면 안 된다 — 개별 실패는 기록만.
                log.warn("신고 결과 알림 발송 실패: reporterId={}, targetId={}", reporterId, targetId, ex);
            }
        }
    }

    /**
     * 관리자 댓글 숨김 + comment_count 대칭 조정.
     * PUBLISHED 경계를 통과할 때(affected-rows>0)만 -1 → AI 검열과의 경합 시 이중감소 없음.
     */
    private void hideCommentWithCount(Long commentId) {
        CommunityComment c = commentMapper.findById(commentId);
        if (c == null) return;
        if (commentMapper.hideCommentIfPublished(commentId) > 0) {
            postMapper.decrementCommentCount(c.getPostId());
        }
    }

    /** 관리자 댓글 삭제 + count 조정. 이미 HIDDEN(=count에서 이미 빠짐)이면 DELETED 로만 전환(이중감소 없음). */
    private void deleteCommentWithCount(Long commentId) {
        CommunityComment c = commentMapper.findById(commentId);
        if (c == null) return;
        if (commentMapper.deleteCommentIfPublished(commentId) > 0) {
            postMapper.decrementCommentCount(c.getPostId());
        } else {
            commentMapper.updateStatus(commentId, "DELETED");
        }
    }

    /** 오탐 복원 HIDDEN→PUBLISHED + count +1(경계 통과 시에만). */
    private void restoreCommentWithCount(Long commentId) {
        CommunityComment c = commentMapper.findById(commentId);
        if (c == null) return;
        if (commentMapper.restoreCommentIfHidden(commentId) > 0) {
            postMapper.incrementCommentCount(c.getPostId());
        }
    }

    /**
     * 게시글 상태전이 가드.
     * 정책: DELETED는 종착(불가역) 상태이므로 DELETED→HIDDEN 역행이나 DELETED 재처리를 막는다.
     * 사전 status 조회로 거부하고, updatePostStatus 매퍼의 status &lt;&gt; 'DELETED' 가드가 경합을 2차 방어한다.
     */
    private void guardPostNotDeleted(Long postId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        if ("DELETED".equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 삭제된 게시글은 상태를 변경할 수 없습니다.");
        }
    }

    private CommunityPost requirePost(Long postId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        return post;
    }

    private CommunityComment requireComment(Long commentId) {
        CommunityComment comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        return comment;
    }

    private void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }
}
