package com.careertuner.admin.community.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.community.dto.AdminPostStatusRequest;
import com.careertuner.admin.ops.dto.AdminActionLogCreate;
import com.careertuner.admin.ops.mapper.AdminActionLogMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.mapper.CommunityPostMapper;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/admin/community/posts")
@RequiredArgsConstructor
public class AdminCommunityPostController {

    private static final Set<String> ALLOWED = Set.of("PUBLISHED", "HIDDEN", "DELETED");

    private final CommunityPostMapper postMapper;
    private final AdminActionLogMapper actionLogMapper;
    private final ObjectMapper objectMapper;

    @PatchMapping("/{postId}/status")
    public ApiResponse<Void> updateStatus(@AuthenticationPrincipal AuthUser authUser,
                                          @PathVariable Long postId,
                                          @RequestBody AdminPostStatusRequest request) {
        AdminAccess.requireAdmin(authUser);
        CommunityPost post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        String status = request.status() == null ? "" : request.status().trim().toUpperCase();
        if (!ALLOWED.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "게시글 상태 값이 올바르지 않습니다.");
        }
        postMapper.updateStatus(postId, status);
        actionLogMapper.insert(new AdminActionLogCreate(authUser.id(), post.getUserId(),
                "COMMUNITY_POST_" + status, "COMMUNITY_POST",
                json(Map.of("postId", postId, "status", post.getStatus())),
                json(Map.of("postId", postId, "status", status)),
                request.reason(), null, null));
        return ApiResponse.ok();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return "{}";
        }
    }
}
