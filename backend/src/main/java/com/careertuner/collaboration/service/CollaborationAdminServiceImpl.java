package com.careertuner.collaboration.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.collaboration.domain.CollaborationConversation;
import com.careertuner.collaboration.domain.ConversationSummaryRow;
import com.careertuner.collaboration.dto.AdminConversationDetailResponse;
import com.careertuner.collaboration.dto.AdminConversationRoomResponse;
import com.careertuner.collaboration.dto.ConversationAuditResponse;
import com.careertuner.collaboration.dto.ConversationBanResponse;
import com.careertuner.collaboration.dto.ConversationMemberDetailResponse;
import com.careertuner.collaboration.dto.ConversationPermissionResponse;
import com.careertuner.collaboration.mapper.CollaborationMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 채팅방 오버사이트 구현.
 * 사용자 방 설정과 달리 권한 게이팅 없이 운영자 관점으로 전체를 조회하고,
 * 신고/분쟁 대응 목적의 강제 밴 해제만 쓰기 작업으로 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollaborationAdminServiceImpl implements CollaborationAdminService {

    private static final int MAX_ROOM_LIMIT = 100;
    private static final int MAX_AUDIT_LIMIT = 100;

    private final CollaborationMapper mapper;

    @Override
    public List<AdminConversationRoomResponse> listRooms(String keyword, int limit) {
        String q = keyword == null ? "" : keyword.trim();
        int capped = Math.max(1, Math.min(limit, MAX_ROOM_LIMIT));
        return mapper.findRoomsForAdmin(q, capped).stream()
                .map(this::toRoomResponse)
                .toList();
    }

    @Override
    public AdminConversationDetailResponse getRoomDetail(Long conversationId) {
        CollaborationConversation conversation = requireRoom(conversationId);
        return buildDetail(conversation);
    }

    @Override
    @Transactional
    public AdminConversationDetailResponse unban(Long adminUserId, Long conversationId, Long targetUserId) {
        CollaborationConversation conversation = requireRoom(conversationId);
        if (mapper.deleteBan(conversationId, targetUserId) > 0) {
            try {
                mapper.insertAudit(conversationId, adminUserId, targetUserId, "ADMIN_UNBANNED", "운영자 강제 해제");
            } catch (RuntimeException ex) {
                // 감사 로그 실패는 무시
            }
        }
        return buildDetail(conversation);
    }

    private CollaborationConversation requireRoom(Long conversationId) {
        CollaborationConversation conversation = mapper.findConversationById(conversationId);
        if (conversation == null || "DIRECT".equals(conversation.getType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "채팅방을 찾을 수 없습니다.");
        }
        return conversation;
    }

    private AdminConversationDetailResponse buildDetail(CollaborationConversation conversation) {
        Long id = conversation.getId();
        List<ConversationMemberDetailResponse> members = mapper.findMemberDetails(id).stream()
                .map(this::toMemberResponse)
                .toList();
        List<ConversationBanResponse> bans = mapper.findBans(id).stream()
                .map(row -> new ConversationBanResponse(
                        row.getUserId(),
                        row.getName() == null || row.getName().isBlank() ? row.getEmail() : row.getName(),
                        row.getReason(),
                        row.getBannedBy(),
                        row.getCreatedAt()))
                .toList();
        List<ConversationAuditResponse> audits = mapper.findAudits(id, MAX_AUDIT_LIMIT).stream()
                .map(row -> new ConversationAuditResponse(
                        row.getId(), row.getActorId(), row.getActorName(),
                        row.getTargetUserId(), row.getTargetName(),
                        row.getAction(), row.getDetail(), row.getCreatedAt()))
                .toList();
        return new AdminConversationDetailResponse(
                id,
                conversation.getType(),
                conversation.getTitle(),
                conversation.getDescription(),
                conversation.getNotice(),
                "PRIVATE".equals(conversation.getType()) || conversation.getPasswordHash() != null,
                conversation.getInvitePolicy() == null ? "ALL_MEMBERS" : conversation.getInvitePolicy(),
                Boolean.TRUE.equals(conversation.getAllowAnonymous()),
                Boolean.TRUE.equals(conversation.getAnonymousOnly()),
                members,
                bans,
                audits);
    }

    private AdminConversationRoomResponse toRoomResponse(ConversationSummaryRow row) {
        return new AdminConversationRoomResponse(
                row.getId(),
                row.getType(),
                row.getTitle(),
                row.getDescription(),
                Boolean.TRUE.equals(row.getLocked()),
                row.getMemberCount() == null ? 0 : row.getMemberCount(),
                row.getUpdatedAt());
    }

    private ConversationMemberDetailResponse toMemberResponse(
            com.careertuner.collaboration.domain.ConversationMemberDetailRow row) {
        boolean owner = "OWNER".equals(row.getRole());
        boolean anonymous = Boolean.TRUE.equals(row.getAnonymous());
        // 운영자 관점에서도 익명 참가자의 실명·이메일은 노출하지 않는다(익명성 유지).
        String displayName = anonymous
                ? (row.getRoomNickname() == null || row.getRoomNickname().isBlank() ? "익명" : row.getRoomNickname())
                : (row.getName() == null || row.getName().isBlank() ? row.getEmail() : row.getName());
        ConversationPermissionResponse permission = new ConversationPermissionResponse(
                owner,
                owner || Boolean.TRUE.equals(row.getCanKick()),
                owner || Boolean.TRUE.equals(row.getCanBan()),
                owner || Boolean.TRUE.equals(row.getCanSetPassword()),
                owner || Boolean.TRUE.equals(row.getCanInvite()),
                owner || Boolean.TRUE.equals(row.getCanEditRoom()),
                owner || Boolean.TRUE.equals(row.getCanManageMembers()));
        return new ConversationMemberDetailResponse(
                row.getUserId(),
                displayName,
                anonymous ? null : row.getEmail(),
                row.getRole(),
                anonymous,
                row.getRoomProfileFileId(),
                row.getJoinedAt(),
                permission,
                Boolean.TRUE.equals(row.getBanned()));
    }
}
