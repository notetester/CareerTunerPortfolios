package com.careertuner.collaboration.service;

import java.util.List;

import com.careertuner.collaboration.dto.AdminConversationDetailResponse;
import com.careertuner.collaboration.dto.AdminConversationRoomResponse;

/** 관리자 채팅방 오버사이트 — 운영자 관점 방 목록/상세 조회 및 강제 밴 해제. */
public interface CollaborationAdminService {

    List<AdminConversationRoomResponse> listRooms(String keyword, int limit);

    AdminConversationDetailResponse getRoomDetail(Long conversationId);

    /** 운영자 강제 밴 해제 — 방 관리자 권한 없이도 처리하고 감사 로그를 운영자 이름으로 남긴다. */
    AdminConversationDetailResponse unban(Long adminUserId, Long conversationId, Long targetUserId);
}
