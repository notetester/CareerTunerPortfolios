package com.careertuner.collaboration.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.collaboration.dto.AdminConversationDetailResponse;
import com.careertuner.collaboration.dto.AdminConversationRoomResponse;
import com.careertuner.collaboration.service.CollaborationAdminService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 채팅방 오버사이트 — /api/admin/collaboration/**. AdminAccess 패턴 적용. */
@RestController
@RequestMapping("/api/admin/collaboration")
@RequiredArgsConstructor
@Validated
public class CollaborationAdminController {

    private final CollaborationAdminService collaborationAdminService;

    @GetMapping("/rooms")
    public ApiResponse<List<AdminConversationRoomResponse>> rooms(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(collaborationAdminService.listRooms(keyword, limit));
    }

    @GetMapping("/rooms/{conversationId}")
    public ApiResponse<AdminConversationDetailResponse> roomDetail(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(collaborationAdminService.getRoomDetail(conversationId));
    }

    /** 운영자 강제 밴 해제(신고/분쟁 대응). */
    @DeleteMapping("/rooms/{conversationId}/bans/{targetUserId}")
    public ApiResponse<AdminConversationDetailResponse> unban(
            @PathVariable Long conversationId,
            @PathVariable Long targetUserId,
            @AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(collaborationAdminService.unban(authUser.id(), conversationId, targetUserId));
    }
}
