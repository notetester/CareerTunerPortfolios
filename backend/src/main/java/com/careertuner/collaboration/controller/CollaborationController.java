package com.careertuner.collaboration.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.collaboration.dto.CollaborationUserResponse;
import com.careertuner.collaboration.dto.ConversationSummaryResponse;
import com.careertuner.collaboration.dto.CreateConversationRequest;
import com.careertuner.collaboration.dto.DirectConversationRequest;
import com.careertuner.collaboration.dto.FriendRequestCreateRequest;
import com.careertuner.collaboration.dto.FriendRequestResponse;
import com.careertuner.collaboration.dto.FriendResponse;
import com.careertuner.collaboration.dto.InviteMembersRequest;
import com.careertuner.collaboration.dto.JoinConversationRequest;
import com.careertuner.collaboration.dto.MessageResponse;
import com.careertuner.collaboration.dto.SendMessageRequest;
import com.careertuner.collaboration.service.CollaborationService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.file.service.FileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/collaboration")
@RequiredArgsConstructor
@Validated
public class CollaborationController {

    private final CollaborationService collaborationService;

    @GetMapping("/users/search")
    public ApiResponse<List<CollaborationUserResponse>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") @Min(1) @Max(30) int limit,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.searchUsers(authUser.id(), keyword, limit));
    }

    @GetMapping("/friends")
    public ApiResponse<List<FriendResponse>> friends(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.listFriends(authUser.id()));
    }

    @DeleteMapping("/friends/{friendUserId}")
    public ApiResponse<Void> removeFriend(@PathVariable Long friendUserId,
                                          @AuthenticationPrincipal AuthUser authUser) {
        collaborationService.removeFriend(authUser.id(), friendUserId);
        return ApiResponse.ok();
    }

    @GetMapping("/friend-requests/incoming")
    public ApiResponse<List<FriendRequestResponse>> incomingRequests(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.listIncomingRequests(authUser.id()));
    }

    @GetMapping("/friend-requests/outgoing")
    public ApiResponse<List<FriendRequestResponse>> outgoingRequests(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.listOutgoingRequests(authUser.id()));
    }

    @PostMapping("/friend-requests")
    public ApiResponse<FriendRequestResponse> requestFriend(
            @Validated @RequestBody FriendRequestCreateRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.sendFriendRequest(authUser.id(), request.targetUserId()));
    }

    @PostMapping("/friend-requests/{requestId}/accept")
    public ApiResponse<FriendRequestResponse> acceptRequest(@PathVariable Long requestId,
                                                            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.acceptFriendRequest(authUser.id(), requestId));
    }

    @PostMapping("/friend-requests/{requestId}/decline")
    public ApiResponse<FriendRequestResponse> declineRequest(@PathVariable Long requestId,
                                                             @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.declineFriendRequest(authUser.id(), requestId));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationSummaryResponse>> conversations(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.listConversations(authUser.id()));
    }

    @GetMapping("/conversations/discover")
    public ApiResponse<List<ConversationSummaryResponse>> discoverConversations(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "30") @Min(1) @Max(50) int limit,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.discoverConversations(authUser.id(), keyword, limit));
    }

    @PostMapping("/conversations/direct")
    public ApiResponse<ConversationSummaryResponse> openDirectConversation(
            @Validated @RequestBody DirectConversationRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.openDirectConversation(authUser.id(), request.targetUserId()));
    }

    @PostMapping("/conversations")
    public ApiResponse<ConversationSummaryResponse> createConversation(
            @Validated @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.createConversation(authUser.id(), request));
    }

    @PostMapping("/conversations/{conversationId}/join")
    public ApiResponse<ConversationSummaryResponse> joinConversation(
            @PathVariable Long conversationId,
            @Validated @RequestBody JoinConversationRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.joinConversation(authUser.id(), conversationId, request));
    }

    @PostMapping("/conversations/{conversationId}/invites")
    public ApiResponse<ConversationSummaryResponse> inviteMembers(
            @PathVariable Long conversationId,
            @Validated @RequestBody InviteMembersRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.inviteMembers(authUser.id(), conversationId, request));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<MessageResponse>> messages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "80") @Min(1) @Max(120) int limit,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.listMessages(authUser.id(), conversationId, limit));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<MessageResponse> sendMessage(
            @PathVariable Long conversationId,
            @Validated @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(collaborationService.sendMessage(authUser.id(), conversationId, request));
    }

    @GetMapping("/files/{fileId}/content")
    public ResponseEntity<byte[]> attachment(@PathVariable Long fileId,
                                             @AuthenticationPrincipal AuthUser authUser) {
        FileService.Download download = collaborationService.downloadAttachment(authUser.id(), fileId);
        String contentType = download.asset().getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        contentType == null || contentType.isBlank()
                                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeName(download.asset().getOriginalName(), fileId) + "\"")
                .body(download.bytes());
    }

    private String safeName(String originalName, Long id) {
        if (originalName == null || originalName.isBlank()) {
            return "file-" + id;
        }
        return originalName.replaceAll("[\"\\r\\n]", "_");
    }
}
