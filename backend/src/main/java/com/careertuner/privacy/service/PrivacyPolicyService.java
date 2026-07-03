package com.careertuner.privacy.service;

import java.util.Map;
import java.util.Set;

import com.careertuner.privacy.dto.ConversationBlockRequest;
import com.careertuner.privacy.dto.ConversationBlockResponse;
import com.careertuner.privacy.dto.IpBlockResponse;
import com.careertuner.privacy.dto.PrivacyPolicyResponse;
import com.careertuner.privacy.dto.PrivacyPolicyUpdateRequest;
import com.careertuner.privacy.dto.UserBlockRequest;
import com.careertuner.privacy.dto.UserBlockResponse;
import com.careertuner.privacy.dto.UserBlockUpdateRequest;

import java.util.List;

/**
 * 개인 차단/허용 정책 (docs/PERSONAL_BLOCK_POLICY.md).
 * 평가 우선순위: 개별 계정 명시 설정 → IP 차단 → 채팅방 차단 파생 → 관계 정책(상속) → 기본값.
 */
public interface PrivacyPolicyService {

    /* ── 평가 (집행 지점에서 호출) ── */

    /** viewer 기준으로 actor 의 해당 표면 행위가 허용되는지. actor 가 null/본인/운영자면 허용. */
    boolean allows(Long viewerId, Long actorId, String surface);

    /**
     * 초대 허용 판정 — 관계 정책(방유형×개설/속함×익명) + 채팅방 차단 파생 규칙까지 반영.
     *
     * @param inviterIsCreator 초대자가 그 방의 개설자인지(참여자인지)
     */
    boolean allowsInvite(Long inviteeId, Long inviterId, Long conversationId,
                         String roomType, boolean inviterIsCreator, boolean anonymous);

    /** 뷰어가 이 방을 차단했는지(목록 숨김용). */
    boolean isConversationBlocked(Long viewerId, Long conversationId);

    /**
     * 콘텐츠 목록 필터용 벌크 판정 — authorIds 각각에 대해 해당 표면이 차단인 작성자 id 집합.
     * (익명 여부는 표면 키로 구분: content.post vs content.post.anonymous)
     */
    Set<Long> blockedAuthorsAmong(Long viewerId, Set<Long> authorIds, String surface);

    /** 알림 억제용 — 발신자가 차단 계정/차단 IP 관계인지. */
    boolean isBlockedSender(Long recipientId, Long actorId);

    /** viewer 와 actor 의 관계 클래스(stranger/friend/company/blockedAccount/blockedIp/operator). */
    String relationOf(Long viewerId, Long actorId);

    /* ── 설정 API ── */

    PrivacyPolicyResponse getPolicy(Long userId);

    PrivacyPolicyResponse updatePolicy(Long userId, PrivacyPolicyUpdateRequest request);

    List<UserBlockResponse> listUserBlocks(Long userId);

    UserBlockResponse blockUser(Long userId, UserBlockRequest request);

    UserBlockResponse updateUserBlock(Long userId, Long blockId, UserBlockUpdateRequest request);

    void unblockUser(Long userId, Long blockId);

    List<IpBlockResponse> listIpBlocks(Long userId);

    void deleteIpBlock(Long userId, Long ipBlockId);

    List<ConversationBlockResponse> listConversationBlocks(Long userId);

    ConversationBlockResponse blockConversation(Long userId, ConversationBlockRequest request);

    ConversationBlockResponse updateConversationBlock(Long userId, Long blockId, Map<String, String> flags);

    void unblockConversation(Long userId, Long blockId);
}
