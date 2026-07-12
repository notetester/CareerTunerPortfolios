package com.careertuner.admin.community.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.admin.community.dto.AdminPostStatusRequest;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.mapper.CommunityPostMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/community/posts")
@RequireAdminPermission({"CONTENT_READ"})
@RequiredArgsConstructor
public class AdminCommunityPostController {

    private static final Set<String> UPDATE_STATUSES = Set.of("PUBLISHED", "HIDDEN");

    private final CommunityPostMapper postMapper;
    private final AdminActionLogService actionLogService;

    @PatchMapping("/{postId}/status")
    @RequireAdminPermission({"CONTENT_UPDATE"})
    public ApiResponse<Void> updateStatus(@AuthenticationPrincipal AuthUser authUser,
                                          @PathVariable Long postId,
                                          @RequestBody AdminPostStatusRequest request) {
        AdminAccess.requireAdmin(authUser);
        CommunityPost post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        String status = request.status() == null ? "" : request.status().trim().toUpperCase();
        if (!UPDATE_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "상태 변경은 PUBLISHED 또는 HIDDEN만 허용됩니다. 삭제는 DELETE endpoint를 사용해 주세요.");
        }
        if ("DELETED".equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "삭제된 게시글은 일반 상태 변경으로 복구할 수 없습니다.");
        }
        changeStatus(authUser, post, status, request.reason());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{postId}")
    @RequireAdminPermission({"CONTENT_DELETE"})
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long postId,
                                    @RequestBody(required = false) AdminPostStatusRequest request) {
        AdminAccess.requireAdmin(authUser);
        CommunityPost post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        if (!"DELETED".equals(post.getStatus())) {
            changeStatus(authUser, post, "DELETED", request == null ? null : request.reason());
        }
        return ApiResponse.ok();
    }

    private void changeStatus(AuthUser authUser, CommunityPost post, String status, String reason) {
        postMapper.updateStatus(post.getId(), status);
        actionLogService.record(authUser, post.getUserId(),
                "COMMUNITY_POST_" + status, "COMMUNITY_POST",
                Map.of("postId", post.getId(), "status", post.getStatus()),
                Map.of("postId", post.getId(), "status", status), reason);
    }
}
