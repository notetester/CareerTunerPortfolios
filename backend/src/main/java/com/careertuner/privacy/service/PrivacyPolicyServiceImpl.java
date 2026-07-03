package com.careertuner.privacy.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.privacy.domain.ContentAuthorRow;
import com.careertuner.privacy.domain.ConversationBlock;
import com.careertuner.privacy.domain.UserBlock;
import com.careertuner.privacy.domain.UserIpBlock;
import com.careertuner.privacy.domain.UserPrivacyPolicy;
import com.careertuner.privacy.domain.UserRoleRow;
import com.careertuner.privacy.dto.ConversationBlockRequest;
import com.careertuner.privacy.dto.ConversationBlockResponse;
import com.careertuner.privacy.dto.IpBlockResponse;
import com.careertuner.privacy.dto.PrivacyPolicyResponse;
import com.careertuner.privacy.dto.PrivacyPolicyUpdateRequest;
import com.careertuner.privacy.dto.UserBlockByContentRequest;
import com.careertuner.privacy.dto.UserBlockRequest;
import com.careertuner.privacy.dto.UserBlockResponse;
import com.careertuner.privacy.dto.UserBlockUpdateRequest;
import com.careertuner.privacy.mapper.PrivacyMapper;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class PrivacyPolicyServiceImpl implements PrivacyPolicyService {

    private final PrivacyMapper mapper;
    private final ObjectMapper objectMapper;
    /** IP 해시 솔트 — 운영에서는 반드시 교체(PRIVACY_IP_SALT). */
    private final String ipSalt;

    public PrivacyPolicyServiceImpl(PrivacyMapper mapper,
                                    ObjectMapper objectMapper,
                                    @Value("${careertuner.privacy.ip-salt:careertuner-dev-ip-salt}") String ipSalt) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.ipSalt = ipSalt;
    }

    /* ─────────────────────────── 평가 ─────────────────────────── */

    @Override
    public boolean allows(Long viewerId, Long actorId, String surface) {
        if (viewerId == null || actorId == null || viewerId.equals(actorId)) {
            return true;
        }
        try {
            // ① 개별 계정 차단의 명시 설정이 최우선 (허용 완화 포함)
            UserBlock block = mapper.findBlock(viewerId, actorId);
            if (block != null) {
                String explicit = PrivacySurfaces.resolve(parseFlags(block.getFlagsJson()), surface);
                if (explicit != null) {
                    return PrivacySurfaces.ALLOW.equals(explicit);
                }
            }
            String relation = relationOfInternal(viewerId, actorId, block);
            if (PrivacySurfaces.OPERATOR.equals(relation)) {
                return true;
            }
            return decideByRelationPolicy(viewerId, relation, surface);
        } catch (RuntimeException ex) {
            // 정책 평가 실패가 기능을 죽이면 안 된다 — 차단 관계 미상으로 보고 허용
            return true;
        }
    }

    @Override
    public boolean allowsInvite(Long inviteeId, Long inviterId, Long conversationId,
                                String roomType, boolean inviterIsCreator, boolean anonymous) {
        if (inviteeId == null || inviterId == null || inviteeId.equals(inviterId)) {
            return true;
        }
        try {
            // 차단한 방으로의 재초대
            ConversationBlock room = conversationId != null
                    ? mapper.findConversationBlock(inviteeId, conversationId) : null;
            if (room != null && isRoomFlagBlocked(room, PrivacySurfaces.ROOM_INVITE_FROM_ROOM, anonymous)) {
                return false;
            }
            // 차단한 방 구성원/개설자가 보내는 다른 방 초대 (연속 초대 테러 방지)
            for (ConversationBlock cb : mapper.findConversationBlocksWhereCreator(inviteeId, inviterId)) {
                if (isRoomFlagBlocked(cb, PrivacySurfaces.ROOM_MEMBER_CREATED_INVITE, anonymous)) {
                    return false;
                }
            }
            for (ConversationBlock cb : mapper.findConversationBlocksWhereMember(inviteeId, inviterId)) {
                if (isRoomFlagBlocked(cb, PrivacySurfaces.ROOM_MEMBER_JOINED_INVITE, anonymous)) {
                    return false;
                }
            }
        } catch (RuntimeException ex) {
            // 파생 규칙 평가 실패는 관계 정책으로 폴백
        }
        String type = roomType == null ? "GROUP" : roomType;
        return allows(inviteeId, inviterId, PrivacySurfaces.inviteSurface(type, inviterIsCreator, anonymous));
    }

    @Override
    public boolean isConversationBlocked(Long viewerId, Long conversationId) {
        if (viewerId == null || conversationId == null) {
            return false;
        }
        try {
            return mapper.findConversationBlock(viewerId, conversationId) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public Set<Long> blockedAuthorsAmong(Long viewerId, Set<Long> authorIds, String surface) {
        Set<Long> hidden = new HashSet<>();
        if (viewerId == null || authorIds == null || authorIds.isEmpty()) {
            return hidden;
        }
        try {
            Set<Long> targets = new HashSet<>(authorIds);
            targets.remove(viewerId);
            if (targets.isEmpty()) {
                return hidden;
            }

            Map<Long, UserBlock> blocks = new LinkedHashMap<>();
            for (UserBlock b : mapper.findBlocksAmong(viewerId, targets)) {
                blocks.put(b.getBlockedUserId(), b);
            }
            Set<Long> ipBlocked = new HashSet<>(mapper.findIpBlockedAmong(viewerId, targets, ipSalt));
            Set<Long> friends = new HashSet<>(mapper.findFriendsAmong(viewerId, targets));
            Map<Long, String> roles = new LinkedHashMap<>();
            for (UserRoleRow row : mapper.findRolesAmong(targets)) {
                roles.put(row.getId(), row.getRole());
            }
            Map<String, Map<String, String>> policy = parsePolicy(mapper.findPolicy(viewerId));

            for (Long authorId : targets) {
                UserBlock block = blocks.get(authorId);
                if (block != null) {
                    String explicit = PrivacySurfaces.resolve(parseFlags(block.getFlagsJson()), surface);
                    if (explicit != null) {
                        if (PrivacySurfaces.BLOCK.equals(explicit)) {
                            hidden.add(authorId);
                        }
                        continue;
                    }
                }
                String role = roles.get(authorId);
                String relation;
                if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
                    continue; // 운영자 콘텐츠는 개인 정책 대상이 아님
                } else if (block != null) {
                    relation = PrivacySurfaces.BLOCKED_ACCOUNT;
                } else if (ipBlocked.contains(authorId)) {
                    relation = PrivacySurfaces.BLOCKED_IP;
                } else if ("COMPANY".equals(role)) {
                    relation = PrivacySurfaces.COMPANY;
                } else if (friends.contains(authorId)) {
                    relation = PrivacySurfaces.FRIEND;
                } else {
                    relation = PrivacySurfaces.STRANGER;
                }
                String value = PrivacySurfaces.resolve(policy.get(relation), surface);
                if (value == null) {
                    value = PrivacySurfaces.defaultValue(relation);
                }
                if (PrivacySurfaces.BLOCK.equals(value)) {
                    hidden.add(authorId);
                }
            }
        } catch (RuntimeException ex) {
            // 필터 실패 시 숨기지 않는다(콘텐츠 소실 방지)
        }
        return hidden;
    }

    @Override
    public boolean isBlockedSender(Long recipientId, Long actorId) {
        String relation = relationOf(recipientId, actorId);
        return PrivacySurfaces.isBlockedRelation(relation);
    }

    @Override
    public String relationOf(Long viewerId, Long actorId) {
        if (viewerId == null || actorId == null || viewerId.equals(actorId)) {
            return PrivacySurfaces.FRIEND; // 본인/미상은 가장 관대한 취급(필터 없음)
        }
        try {
            return relationOfInternal(viewerId, actorId, mapper.findBlock(viewerId, actorId));
        } catch (RuntimeException ex) {
            return PrivacySurfaces.STRANGER;
        }
    }

    private String relationOfInternal(Long viewerId, Long actorId, UserBlock block) {
        if (block != null) {
            return PrivacySurfaces.BLOCKED_ACCOUNT;
        }
        String role = mapper.findUserRole(actorId);
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
            return PrivacySurfaces.OPERATOR;
        }
        if (mapper.countIpBlockMatch(viewerId, actorId, ipSalt) > 0) {
            return PrivacySurfaces.BLOCKED_IP;
        }
        if ("COMPANY".equals(role)) {
            return PrivacySurfaces.COMPANY;
        }
        if (mapper.countFriendship(viewerId, actorId) > 0) {
            return PrivacySurfaces.FRIEND;
        }
        return PrivacySurfaces.STRANGER;
    }

    private boolean decideByRelationPolicy(Long viewerId, String relation, String surface) {
        Map<String, Map<String, String>> policy = parsePolicy(mapper.findPolicy(viewerId));
        String value = PrivacySurfaces.resolve(policy.get(relation), surface);
        if (value == null) {
            value = PrivacySurfaces.defaultValue(relation);
        }
        return PrivacySurfaces.ALLOW.equals(value);
    }

    private boolean isRoomFlagBlocked(ConversationBlock block, String flag, boolean anonymous) {
        Map<String, String> flags = parseFlags(block.getFlagsJson());
        String key = anonymous ? flag + ".anonymous" : flag;
        String value = PrivacySurfaces.resolve(flags, key);
        if (value == null) {
            value = PrivacySurfaces.roomFlagDefault(flag);
        }
        return PrivacySurfaces.BLOCK.equals(value);
    }

    /* ─────────────────────────── 설정 API ─────────────────────────── */

    @Override
    public PrivacyPolicyResponse getPolicy(Long userId) {
        Map<String, Map<String, String>> overrides = parsePolicy(mapper.findPolicy(userId));
        Map<String, Map<String, String>> effective = new LinkedHashMap<>();
        for (String relation : PrivacySurfaces.RELATIONS) {
            Map<String, String> row = new LinkedHashMap<>();
            for (String surface : PrivacySurfaces.BASE_SURFACES) {
                String value = PrivacySurfaces.resolve(overrides.get(relation), surface);
                row.put(surface, value != null ? value : PrivacySurfaces.defaultValue(relation));
            }
            effective.put(relation, row);
        }
        return new PrivacyPolicyResponse(
                PrivacySurfaces.RELATIONS, PrivacySurfaces.BASE_SURFACES, overrides, effective);
    }

    @Override
    @Transactional
    public PrivacyPolicyResponse updatePolicy(Long userId, PrivacyPolicyUpdateRequest request) {
        Map<String, Map<String, String>> overrides = parsePolicy(mapper.findPolicy(userId));
        if (request.relations() != null) {
            for (Map.Entry<String, Map<String, String>> entry : request.relations().entrySet()) {
                String relation = entry.getKey();
                if (!PrivacySurfaces.RELATIONS.contains(relation) || entry.getValue() == null) {
                    continue;
                }
                Map<String, String> row = overrides.computeIfAbsent(relation, r -> new LinkedHashMap<>());
                for (Map.Entry<String, String> value : entry.getValue().entrySet()) {
                    String surface = value.getKey();
                    if (!PrivacySurfaces.SURFACE_KEY.matcher(surface).matches()) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 정책 항목입니다: " + surface);
                    }
                    applyFlag(row, surface, value.getValue());
                }
            }
        }
        mapper.upsertPolicy(UserPrivacyPolicy.builder()
                .userId(userId)
                .policyJson(toJson(Map.of("relations", overrides)))
                .build());
        return getPolicy(userId);
    }

    @Override
    public List<UserBlockResponse> listUserBlocks(Long userId) {
        return mapper.findBlocksByUser(userId).stream().map(this::toBlockResponse).toList();
    }

    @Override
    @Transactional
    public UserBlockResponse blockUser(Long userId, UserBlockRequest request) {
        return createBlock(userId, request.targetUserId(), request.blockIp(), request.memo(), null);
    }

    @Override
    @Transactional
    public UserBlockResponse blockUserByContent(Long userId, UserBlockByContentRequest request) {
        boolean isPost = "POST".equals(request.contentType());
        ContentAuthorRow author = isPost
                ? mapper.findPostAuthor(request.contentId())
                : mapper.findCommentAuthor(request.contentId());
        if (author == null || author.getUserId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "차단할 콘텐츠를 찾을 수 없습니다.");
        }
        if (userId.equals(author.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인이 작성한 콘텐츠의 작성자는 차단할 수 없습니다.");
        }
        // 익명 콘텐츠면 차단 목록에 실명 대신 표시할 라벨을 저장한다(비익명은 기존과 동일하게 마스킹 없음).
        String maskedLabel = author.isAnonymous()
                ? "익명 작성자 (" + (isPost ? "게시글" : "댓글") + " #" + request.contentId() + ")"
                : null;
        return createBlock(userId, author.getUserId(), request.blockIp(), request.memo(), maskedLabel);
    }

    /** 계정 차단 생성 공통 — 대상 검증(본인/미존재/운영자) 후 신규 생성, 이미 차단이면 기존 항목 반환. */
    private UserBlockResponse createBlock(Long userId, Long targetUserId, Boolean blockIp, String memo, String maskedLabel) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인은 차단할 수 없습니다.");
        }
        String targetRole = mapper.findUserRole(targetUserId);
        if (targetRole == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "차단할 사용자를 찾을 수 없습니다.");
        }
        if ("ADMIN".equals(targetRole) || "SUPER_ADMIN".equals(targetRole)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "운영자 계정은 차단할 수 없습니다.");
        }
        UserBlock existing = mapper.findBlock(userId, targetUserId);
        if (existing != null) {
            return toBlockResponse(existing);
        }
        UserBlock block = UserBlock.builder()
                .userId(userId)
                .blockedUserId(targetUserId)
                .blockIp(Boolean.TRUE.equals(blockIp))
                .memo(memo)
                .maskedLabel(maskedLabel)
                .build();
        mapper.insertBlock(block);
        if (block.isBlockIp()) {
            deriveIpBlock(userId, targetUserId);
        }
        return toBlockResponse(mapper.findBlockById(block.getId()));
    }

    @Override
    @Transactional
    public UserBlockResponse updateUserBlock(Long userId, Long blockId, UserBlockUpdateRequest request) {
        UserBlock block = requireOwnBlock(userId, blockId);
        Map<String, String> flags = parseFlags(block.getFlagsJson());
        if (request.flags() != null) {
            for (Map.Entry<String, String> entry : request.flags().entrySet()) {
                if (!PrivacySurfaces.SURFACE_KEY.matcher(entry.getKey()).matches()) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 차단 항목입니다: " + entry.getKey());
                }
                applyFlag(flags, entry.getKey(), entry.getValue());
            }
        }
        boolean blockIp = request.blockIp() != null ? request.blockIp() : block.isBlockIp();
        block.setFlagsJson(flags.isEmpty() ? null : toJson(flags));
        block.setMemo(request.memo() != null ? request.memo() : block.getMemo());
        if (blockIp != block.isBlockIp()) {
            block.setBlockIp(blockIp);
            if (blockIp) {
                deriveIpBlock(userId, block.getBlockedUserId());
            } else {
                mapper.deleteIpBlocksBySource(userId, block.getBlockedUserId());
            }
        }
        mapper.updateBlock(block);
        return toBlockResponse(mapper.findBlockById(blockId));
    }

    @Override
    @Transactional
    public void unblockUser(Long userId, Long blockId) {
        UserBlock block = requireOwnBlock(userId, blockId);
        mapper.deleteIpBlocksBySource(userId, block.getBlockedUserId());
        mapper.deleteBlock(blockId);
    }

    @Override
    public List<IpBlockResponse> listIpBlocks(Long userId) {
        return mapper.findIpBlocksByUser(userId).stream()
                .map(b -> new IpBlockResponse(
                        b.getId(),
                        b.getLabel() != null ? b.getLabel() : "차단 IP #" + b.getId(),
                        b.getSourceUserId(),
                        // 파생 원본이 익명 콘텐츠 기반 차단이면 실명 대신 masked_label 표시(익명성 유지)
                        b.getSourceMaskedLabel() != null ? b.getSourceMaskedLabel() : b.getSourceUserName(),
                        countMatchedAccounts(b),
                        b.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteIpBlock(Long userId, Long ipBlockId) {
        mapper.deleteIpBlock(ipBlockId, userId);
    }

    @Override
    public List<ConversationBlockResponse> listConversationBlocks(Long userId) {
        return mapper.findConversationBlocksByUser(userId).stream().map(this::toConversationResponse).toList();
    }

    @Override
    @Transactional
    public ConversationBlockResponse blockConversation(Long userId, ConversationBlockRequest request) {
        ConversationBlock block = ConversationBlock.builder()
                .userId(userId)
                .conversationId(request.conversationId())
                .flagsJson(request.flags() == null || request.flags().isEmpty()
                        ? null : toJson(validateRoomFlags(request.flags())))
                .build();
        mapper.insertConversationBlock(block);
        ConversationBlock saved = mapper.findConversationBlock(userId, request.conversationId());
        if (saved == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "차단할 대화방을 찾을 수 없습니다.");
        }
        return toConversationResponse(saved);
    }

    @Override
    @Transactional
    public ConversationBlockResponse updateConversationBlock(Long userId, Long blockId, Map<String, String> flags) {
        ConversationBlock block = mapper.findConversationBlockById(blockId);
        if (block == null || !userId.equals(block.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "차단 항목을 찾을 수 없습니다.");
        }
        Map<String, String> merged = parseFlags(block.getFlagsJson());
        if (flags != null) {
            for (Map.Entry<String, String> entry : validateRoomFlags(flags).entrySet()) {
                applyFlag(merged, entry.getKey(), entry.getValue());
            }
        }
        block.setFlagsJson(merged.isEmpty() ? null : toJson(merged));
        mapper.updateConversationBlock(block);
        return toConversationResponse(mapper.findConversationBlockById(blockId));
    }

    @Override
    @Transactional
    public void unblockConversation(Long userId, Long blockId) {
        ConversationBlock block = mapper.findConversationBlockById(blockId);
        if (block == null || !userId.equals(block.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "차단 항목을 찾을 수 없습니다.");
        }
        mapper.deleteConversationBlock(blockId);
    }

    /* ─────────────────────────── 내부 ─────────────────────────── */

    private void deriveIpBlock(Long userId, Long targetUserId) {
        String hash = mapper.findLatestLoginIpHash(targetUserId, ipSalt);
        if (hash == null) {
            return; // 로그인 IP 기록이 없으면 파생 불가 — 계정 차단만 유지
        }
        mapper.insertIpBlock(UserIpBlock.builder()
                .userId(userId)
                .ipHash(hash)
                .sourceUserId(targetUserId)
                .label(null)
                .build());
    }

    private int countMatchedAccounts(UserIpBlock block) {
        try {
            return mapper.countAccountsMatchingIpHash(block.getIpHash(), ipSalt);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private UserBlock requireOwnBlock(Long userId, Long blockId) {
        UserBlock block = mapper.findBlockById(blockId);
        if (block == null || !userId.equals(block.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "차단 항목을 찾을 수 없습니다.");
        }
        return block;
    }

    /** "allow"/"block" 은 설정, 빈 문자열/null 은 명시값 제거(상위 따름). */
    private static void applyFlag(Map<String, String> flags, String key, String value) {
        if (PrivacySurfaces.ALLOW.equals(value) || PrivacySurfaces.BLOCK.equals(value)) {
            flags.put(key, value);
        } else if (value == null || value.isBlank()) {
            flags.remove(key);
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정책 값은 allow/block/빈값만 가능합니다.");
        }
    }

    private Map<String, String> validateRoomFlags(Map<String, String> flags) {
        for (String key : flags.keySet()) {
            if (!PrivacySurfaces.ROOM_FLAG_KEY.matcher(key).matches()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 채팅방 차단 항목입니다: " + key);
            }
        }
        return flags;
    }

    private UserBlockResponse toBlockResponse(UserBlock block) {
        // 익명 콘텐츠 기반 차단 — 실명/이메일 대신 masked_label 을 노출해 익명성을 지킨다.
        boolean masked = block.getMaskedLabel() != null && !block.getMaskedLabel().isBlank();
        return new UserBlockResponse(
                block.getId(),
                block.getBlockedUserId(),
                masked ? block.getMaskedLabel() : block.getBlockedUserName(),
                masked ? null : block.getBlockedUserEmail(),
                masked,
                parseFlags(block.getFlagsJson()),
                block.isBlockIp(),
                block.getMemo(),
                block.getCreatedAt());
    }

    private ConversationBlockResponse toConversationResponse(ConversationBlock block) {
        return new ConversationBlockResponse(
                block.getId(),
                block.getConversationId(),
                block.getConversationTitle(),
                block.getConversationType(),
                parseFlags(block.getFlagsJson()),
                block.getCreatedAt());
    }

    private Map<String, String> parseFlags(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (RuntimeException ex) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Map<String, String>> parsePolicy(UserPrivacyPolicy policy) {
        if (policy == null || policy.getPolicyJson() == null || policy.getPolicyJson().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Map<String, Map<String, String>>> doc = objectMapper.readValue(
                    policy.getPolicyJson(),
                    new TypeReference<LinkedHashMap<String, Map<String, Map<String, String>>>>() {
                    });
            Map<String, Map<String, String>> relations = doc.get("relations");
            return relations != null ? new LinkedHashMap<>(relations) : new LinkedHashMap<>();
        } catch (RuntimeException ex) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
