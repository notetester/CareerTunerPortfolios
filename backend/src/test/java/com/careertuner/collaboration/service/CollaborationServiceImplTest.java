package com.careertuner.collaboration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.careertuner.collaboration.domain.CollaborationConversation;
import com.careertuner.collaboration.domain.CollaborationMessage;
import com.careertuner.collaboration.domain.CollaborationUserRow;
import com.careertuner.collaboration.domain.ConversationMemberDetailRow;
import com.careertuner.collaboration.domain.ConversationMemberRow;
import com.careertuner.collaboration.domain.ConversationPermissionRow;
import com.careertuner.collaboration.domain.ConversationSummaryRow;
import com.careertuner.collaboration.domain.DesktopPresenceRow;
import com.careertuner.collaboration.domain.FriendRequest;
import com.careertuner.collaboration.domain.FriendRequestRow;
import com.careertuner.collaboration.domain.MessageAttachmentRow;
import com.careertuner.collaboration.dto.ConversationBanRequest;
import com.careertuner.collaboration.dto.ConversationPermissionUpdateRequest;
import com.careertuner.collaboration.dto.ConversationSettingsUpdateRequest;
import com.careertuner.collaboration.dto.CreateConversationRequest;
import com.careertuner.collaboration.dto.InviteMembersRequest;
import com.careertuner.collaboration.dto.JoinConversationRequest;
import com.careertuner.collaboration.dto.SendMessageRequest;
import com.careertuner.collaboration.mapper.CollaborationMapper;
import com.careertuner.nickname.service.NicknameProfileService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.mapper.FileAssetMapper;
import com.careertuner.file.service.FileService;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationPreferenceService;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.privacy.service.PrivacyPolicyService;
import com.careertuner.privacy.service.PrivacySurfaces;

class CollaborationServiceImplTest {

    private final CollaborationMapper mapper = mock(CollaborationMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationPreferenceService notificationPreferenceService =
            mock(NotificationPreferenceService.class);
    private final FileAssetMapper fileAssetMapper = mock(FileAssetMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final PrivacyPolicyService privacyPolicyService = mock(PrivacyPolicyService.class);
    private final NicknameProfileService nicknameProfileService = mock(NicknameProfileService.class);
    private final CollaborationServiceImpl service =
            new CollaborationServiceImpl(mapper, notificationService, notificationPreferenceService,
                    fileAssetMapper, fileService, passwordEncoder, privacyPolicyService, nicknameProfileService);

    /**
     * 개인 차단 정책 기본 스텁 — 전부 허용.
     * Mockito 기본값(false/빈 컬렉션)으로 두면 allows()=false 가 되어 기존 테스트가 전부 차단돼 깨진다.
     */
    @BeforeEach
    void allowAllPrivacyByDefault() {
        when(privacyPolicyService.allows(any(), any(), any())).thenReturn(true);
        when(privacyPolicyService.allowsInvite(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);
        when(privacyPolicyService.isConversationBlocked(any(), any())).thenReturn(false);
        when(privacyPolicyService.blockedAuthorsAmong(any(), any(), any())).thenReturn(Set.of());
        when(privacyPolicyService.listConversationBlocks(any())).thenReturn(List.of());
        // 표시명 해석은 빈 맵으로 스텁 → 발신자는 저장된 senderName 으로 폴백(기존 단언 유지).
        when(nicknameProfileService.bulkResolveDisplayNames(any())).thenReturn(Map.of());
    }

    @Test
    void sendFriendRequest_createsPendingRequestAndNotifiesReceiver() {
        when(mapper.findActiveUserById(2L)).thenReturn(user(2L, "민지", "minji@example.com"));
        doAnswer(invocation -> {
            FriendRequest request = invocation.getArgument(0);
            request.setId(10L);
            return null;
        }).when(mapper).insertFriendRequest(any());
        when(mapper.findRequestRowById(10L)).thenReturn(requestRow(10L, 1L, 2L, "PENDING"));

        var response = service.sendFriendRequest(1L, 2L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("PENDING");
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).notify(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUserId()).isEqualTo(2L);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("FRIEND_REQUEST");
    }

    @Test
    void sendFriendRequest_acceptsReversePendingRequest() {
        FriendRequest incoming = FriendRequest.builder()
                .id(7L)
                .requesterId(2L)
                .receiverId(1L)
                .status("PENDING")
                .build();
        when(mapper.findActiveUserById(2L)).thenReturn(user(2L, "민지", "minji@example.com"));
        when(mapper.findPendingRequest(2L, 1L)).thenReturn(incoming);
        when(mapper.findRequestById(7L)).thenReturn(incoming);
        when(mapper.findRequestRowById(7L)).thenReturn(requestRow(7L, 2L, 1L, "ACCEPTED"));

        var response = service.sendFriendRequest(1L, 2L);

        assertThat(response.status()).isEqualTo("ACCEPTED");
        verify(mapper).updateFriendRequestStatus(7L, "ACCEPTED");
        verify(mapper).insertFriendship(2L, 1L, 1L);
        verify(mapper).insertFriendship(1L, 2L, 1L);
    }

    @Test
    void sendMessage_requiresContentOrAttachment() {
        when(mapper.countConversationMember(5L, 1L)).thenReturn(1);

        assertThatThrownBy(() -> service.sendMessage(1L, 5L,
                new SendMessageRequest("CHAT", "   ", List.of(), null, null, List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
        verify(mapper, never()).insertMessage(any());
    }

    @Test
    void sendMessage_attachesOwnedFilesAndNotifiesPeer() {
        when(mapper.countConversationMember(5L, 1L)).thenReturn(1);
        doAnswer(invocation -> {
            CollaborationMessage message = invocation.getArgument(0);
            message.setId(20L);
            return null;
        }).when(mapper).insertMessage(any());
        when(fileAssetMapper.findById(9L)).thenReturn(FileAsset.builder()
                .id(9L)
                .ownerUserId(1L)
                .kind("ATTACHMENT")
                .refType("COLLAB_MESSAGE")
                .originalName("portfolio.pdf")
                .sizeBytes(1200L)
                .build());
        when(fileAssetMapper.claimPendingCollaborationAttachment(9L, 1L, 20L)).thenReturn(1);
        when(mapper.findConversationMembersForNotify(5L)).thenReturn(List.of(
                ConversationMemberRow.builder().userId(1L).name("나").muted(false).build(),
                ConversationMemberRow.builder().userId(2L).name("민지").muted(false).build()));
        when(mapper.findMessageById(20L)).thenReturn(CollaborationMessage.builder()
                .id(20L)
                .conversationId(5L)
                .senderId(1L)
                .senderName("나")
                .senderEmail("me@example.com")
                .kind("NOTE")
                .content("검토 부탁해")
                .build());
        when(mapper.findAttachmentsByMessageId(20L)).thenReturn(List.of(MessageAttachmentRow.builder()
                .id(100L)
                .messageId(20L)
                .fileAssetId(9L)
                .originalName("portfolio.pdf")
                .shareMode("TEMPORARY")
                .sizeBytes(1200L)
                .build()));
        when(mapper.findPostingsByMessageId(20L)).thenReturn(List.of());

        var response = service.sendMessage(1L, 5L,
                new SendMessageRequest("NOTE", " 검토 부탁해 ", Arrays.asList(9L, 9L, null),
                        "TEMPORARY", 24, List.of()));

        assertThat(response.kind()).isEqualTo("NOTE");
        assertThat(response.content()).isEqualTo("검토 부탁해");
        assertThat(response.attachments()).hasSize(1);
        verify(mapper).insertMessageAttachment(org.mockito.Mockito.eq(20L), org.mockito.Mockito.eq(9L),
                org.mockito.Mockito.eq("TEMPORARY"), org.mockito.Mockito.any());
        verify(fileAssetMapper).claimPendingCollaborationAttachment(9L, 1L, 20L);
        verify(notificationService).notify(any(Notification.class));
    }

    @Test
    void sendMessageRejectsAlreadySentAttachmentBeforeCreatingMessage() {
        when(mapper.countConversationMember(5L, 1L)).thenReturn(1);
        when(fileAssetMapper.findById(9L)).thenReturn(FileAsset.builder()
                .id(9L)
                .ownerUserId(1L)
                .kind("ATTACHMENT")
                .refType("COLLAB_MESSAGE")
                .refId(88L)
                .build());

        assertThatThrownBy(() -> service.sendMessage(1L, 5L,
                new SendMessageRequest("CHAT", null, List.of(9L), "TEMPORARY", 24, List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(mapper, never()).insertMessage(any());
        verify(fileAssetMapper, never()).claimPendingCollaborationAttachment(any(), any(), any());
    }

    @Test
    void sendMessageRollsBackWhenPendingAttachmentWasDeletedConcurrently() {
        when(mapper.countConversationMember(5L, 1L)).thenReturn(1);
        doAnswer(invocation -> {
            CollaborationMessage message = invocation.getArgument(0);
            message.setId(21L);
            return null;
        }).when(mapper).insertMessage(any());
        when(fileAssetMapper.findById(10L)).thenReturn(FileAsset.builder()
                .id(10L)
                .ownerUserId(1L)
                .kind("ATTACHMENT")
                .refType("COLLAB_MESSAGE")
                .build());
        when(fileAssetMapper.claimPendingCollaborationAttachment(10L, 1L, 21L)).thenReturn(0);

        assertThatThrownBy(() -> service.sendMessage(1L, 5L,
                new SendMessageRequest("CHAT", null, List.of(10L), "TEMPORARY", 24, List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));

        verify(mapper, never()).insertMessageAttachment(any(), any(), any(), any());
    }

    @Test
    void createConversation_hashesPrivatePasswordAndAddsFriends() {
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(mapper.countFriendship(1L, 2L)).thenReturn(1);
        when(mapper.findActiveUserById(2L)).thenReturn(user(2L, "민지", "minji@example.com"));
        doAnswer(invocation -> {
            CollaborationConversation conversation = invocation.getArgument(0);
            conversation.setId(30L);
            return null;
        }).when(mapper).insertConversation(any());
        when(mapper.findConversationSummary(1L, 30L)).thenReturn(ConversationSummaryRow.builder()
                .id(30L)
                .type("PRIVATE")
                .title("비공개 스터디")
                .locked(true)
                .joined(true)
                .memberCount(2)
                .build());

        var response = service.createConversation(1L,
                new CreateConversationRequest("PRIVATE", " 비공개 스터디 ", null, " secret ", List.of(2L)));

        assertThat(response.type()).isEqualTo("PRIVATE");
        assertThat(response.locked()).isTrue();
        verify(mapper).insertConversationMemberWithRole(30L, 1L, "OWNER", 1L);
        verify(mapper).insertConversationMemberWithRole(30L, 2L, "MEMBER", 1L);
    }

    @Test
    void joinPrivateConversation_allowsInvitedUserWithoutPassword() {
        when(mapper.findConversationById(40L)).thenReturn(CollaborationConversation.builder()
                .id(40L)
                .type("PRIVATE")
                .title("비공개 방")
                .build());
        when(mapper.countPendingInvite(40L, 2L)).thenReturn(1);
        when(mapper.findConversationSummary(2L, 40L)).thenReturn(ConversationSummaryRow.builder()
                .id(40L)
                .type("PRIVATE")
                .title("비공개 방")
                .locked(false)
                .joined(true)
                .memberCount(2)
                .build());

        var response = service.joinConversation(2L, 40L, new JoinConversationRequest(null));

        assertThat(response.joined()).isTrue();
        verify(mapper).insertConversationMemberWithRole(40L, 2L, "MEMBER", null);
        verify(mapper).acceptInvite(40L, 2L);
    }

    // ══════════════ 개인 차단 정책 집행 지점 (docs/PERSONAL_BLOCK_POLICY.md §5) ══════════════

    // ── 상대 정책이 뷰어의 dm 을 차단 → 일반 문구(FORBIDDEN)로 조용히 거부, 방 생성 없음 ──
    @Test
    void openDirectConversation_deniedSilently_whenTargetPolicyBlocksDm() {
        when(mapper.findActiveUserById(2L)).thenReturn(user(2L, "민지", "minji@example.com"));
        when(mapper.countFriendship(1L, 2L)).thenReturn(1);
        when(privacyPolicyService.allows(2L, 1L, PrivacySurfaces.DM)).thenReturn(false);

        assertThatThrownBy(() -> service.openDirectConversation(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("지금은 이 사용자와 대화를 시작할 수 없습니다.") // 차단 사실 비특정 문구
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(mapper, never()).insertConversation(any());
    }

    // ── 초대 대상 한 명의 정책이 차단 → 그 대상만 조용히 스킵(예외 없음), 나머지는 초대 ──
    @Test
    void inviteMembers_skipsBlockedInviteeSilently() {
        when(mapper.findConversationById(30L)).thenReturn(CollaborationConversation.builder()
                .id(30L)
                .type("GROUP")
                .title("스터디")
                .createdBy(1L)
                .build());
        when(mapper.countConversationMember(30L, 1L)).thenReturn(1);
        when(mapper.findActiveUserById(2L)).thenReturn(user(2L, "민지", "minji@example.com"));
        when(mapper.findActiveUserById(3L)).thenReturn(user(3L, "철수", "chulsoo@example.com"));
        when(mapper.countFriendship(1L, 2L)).thenReturn(1);
        when(mapper.countFriendship(1L, 3L)).thenReturn(1);
        when(privacyPolicyService.allowsInvite(eq(2L), eq(1L), eq(30L), eq("GROUP"), anyBoolean(), anyBoolean()))
                .thenReturn(false); // 2번 사용자만 초대 차단
        when(mapper.findConversationSummary(1L, 30L)).thenReturn(ConversationSummaryRow.builder()
                .id(30L)
                .type("GROUP")
                .title("스터디")
                .joined(true)
                .memberCount(2)
                .build());

        var response = service.inviteMembers(1L, 30L, new InviteMembersRequest(List.of(2L, 3L), false));

        assertThat(response.id()).isEqualTo(30L);
        verify(mapper, never()).insertConversationInvite(eq(30L), eq(1L), eq(2L), anyBoolean());
        verify(mapper, never())
                .insertConversationMemberWithAnonymous(eq(30L), eq(2L), eq("MEMBER"), eq(1L), anyBoolean());
        verify(mapper).insertConversationInvite(30L, 1L, 3L, false);
        verify(mapper).insertConversationMemberWithAnonymous(30L, 3L, "MEMBER", 1L, false);
    }

    // ── 차단 발신자 메시지는 톰스톤 처리(본문 교체·첨부/공고 비움·blocked=true), 첨부 조회 생략 ──
    @Test
    void listMessages_tombstonesBlockedSenderMessages() {
        when(mapper.countConversationMember(5L, 1L)).thenReturn(1);
        when(mapper.findMessages(eq(5L), anyInt())).thenReturn(List.of(
                CollaborationMessage.builder()
                        .id(21L).conversationId(5L).senderId(2L)
                        .senderName("민지").senderEmail("minji@example.com")
                        .kind("CHAT").content("차단 전 메시지").build(),
                CollaborationMessage.builder()
                        .id(22L).conversationId(5L).senderId(3L)
                        .senderName("철수").senderEmail("chulsoo@example.com")
                        .kind("CHAT").content("정상 메시지").build()));
        when(privacyPolicyService.blockedAuthorsAmong(eq(1L), eq(Set.of(2L, 3L)),
                eq(PrivacySurfaces.CONTENT_ROOM_MESSAGE))).thenReturn(Set.of(2L));
        when(mapper.findLatestMessageId(5L)).thenReturn(22L);
        when(mapper.findAttachmentsByMessageId(22L)).thenReturn(List.of());
        when(mapper.findPostingsByMessageId(22L)).thenReturn(List.of());

        var responses = service.listMessages(1L, 5L, 50);

        assertThat(responses).hasSize(2);
        var blocked = responses.get(0);
        assertThat(blocked.id()).isEqualTo(21L);
        assertThat(blocked.blocked()).isTrue();
        assertThat(blocked.content()).isEqualTo("차단한 사용자의 메시지입니다.");
        assertThat(blocked.attachments()).isEmpty();
        assertThat(blocked.sharedPostings()).isEmpty();
        var normal = responses.get(1);
        assertThat(normal.blocked()).isFalse();
        assertThat(normal.content()).isEqualTo("정상 메시지");
        // 차단 메시지는 첨부/공고 조회 자체를 생략한다
        verify(mapper, never()).findAttachmentsByMessageId(21L);
        verify(mapper, never()).findPostingsByMessageId(21L);
    }

    @Test
    void listMessages_keepsDeletedUsersMessageButRemovesSenderIdentityLinks() {
        when(mapper.countConversationMember(5L, 1L)).thenReturn(1);
        when(mapper.findMessages(eq(5L), anyInt())).thenReturn(List.of(
                CollaborationMessage.builder()
                        .id(90L).conversationId(5L).senderId(2L)
                        .senderName("탈퇴한 사용자").senderEmail(null).senderStatus("DELETED")
                        .kind("CHAT").content("보존된 메시지")
                        .build()));
        when(mapper.findAttachmentsByMessageId(90L)).thenReturn(List.of());
        when(mapper.findPostingsByMessageId(90L)).thenReturn(List.of());

        var response = service.listMessages(1L, 5L, 50).getFirst();

        assertThat(response.content()).isEqualTo("보존된 메시지");
        assertThat(response.sender().name()).isEqualTo("탈퇴한 사용자");
        assertThat(response.sender().id()).isNull();
        assertThat(response.sender().email()).isNull();
    }

    // ══════════════ LOCAL 파일 공유 — 데스크톱 presence 게이트 ══════════════

    // ── 소유자 데스크톱 온라인(heartbeat 90초 이내) → 다운로드 허용 ──
    @Test
    void downloadAttachment_allowsLocalShare_whenOwnerDesktopOnline() {
        when(mapper.findAttachmentForDownload(1L, 9L)).thenReturn(MessageAttachmentRow.builder()
                .fileAssetId(9L)
                .shareMode("LOCAL")
                .ownerUserId(2L)
                .build());
        when(mapper.findDesktopLastSeenAt(2L)).thenReturn(LocalDateTime.now().minusSeconds(10));
        FileService.Download download = new FileService.Download(
                FileAsset.builder().id(9L).originalName("dataset.zip").build(), new byte[] {1});
        when(fileService.downloadAfterAccessCheck(9L)).thenReturn(download);

        assertThat(service.downloadAttachment(1L, 9L)).isSameAs(download);
        verify(fileService).downloadAfterAccessCheck(9L);
    }

    // ── 소유자 데스크톱 오프라인(heartbeat 없음/오래됨) → CONFLICT, 파일 서빙 안 함 ──
    @Test
    void downloadAttachment_conflicts_whenLocalOwnerDesktopOffline() {
        when(mapper.findAttachmentForDownload(1L, 9L)).thenReturn(MessageAttachmentRow.builder()
                .fileAssetId(9L)
                .shareMode("LOCAL")
                .ownerUserId(2L)
                .build());
        when(mapper.findDesktopLastSeenAt(2L)).thenReturn(LocalDateTime.now().minusSeconds(300));

        assertThatThrownBy(() -> service.downloadAttachment(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("파일 소유자의 데스크톱이 온라인이 아닙니다 — 온라인이 되면 다운로드할 수 있습니다.")
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
        verify(fileService, never()).downloadAfterAccessCheck(any());
    }

    // ── heartbeat 기록이 아예 없는 소유자도 오프라인으로 본다 ──
    @Test
    void downloadAttachment_conflicts_whenLocalOwnerHasNoHeartbeat() {
        when(mapper.findAttachmentForDownload(1L, 9L)).thenReturn(MessageAttachmentRow.builder()
                .fileAssetId(9L)
                .shareMode("LOCAL")
                .ownerUserId(2L)
                .build());
        when(mapper.findDesktopLastSeenAt(2L)).thenReturn(null);

        assertThatThrownBy(() -> service.downloadAttachment(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
        verify(fileService, never()).downloadAfterAccessCheck(any());
    }

    // ── 첨부 목록 — LOCAL 소유자 presence 를 1쿼리 벌크 조회해 ownerDesktopOnline 을 세팅 ──
    @Test
    void listMessages_marksLocalAttachmentOwnerPresence_inSingleBulkQuery() {
        when(mapper.countConversationMember(5L, 1L)).thenReturn(1);
        when(mapper.findMessages(eq(5L), anyInt())).thenReturn(List.of(
                CollaborationMessage.builder()
                        .id(21L).conversationId(5L).senderId(2L)
                        .senderName("민지").senderEmail("minji@example.com")
                        .kind("CHAT").content("로컬 공유 파일이에요").build()));
        when(mapper.findAttachmentsByMessageId(21L)).thenReturn(List.of(
                MessageAttachmentRow.builder()
                        .id(100L).messageId(21L).fileAssetId(9L)
                        .originalName("online.zip").shareMode("LOCAL").ownerUserId(2L).sizeBytes(10L).build(),
                MessageAttachmentRow.builder()
                        .id(101L).messageId(21L).fileAssetId(10L)
                        .originalName("offline.zip").shareMode("LOCAL").ownerUserId(3L).sizeBytes(10L).build()));
        when(mapper.findPostingsByMessageId(21L)).thenReturn(List.of());
        when(mapper.findLatestMessageId(5L)).thenReturn(21L);
        when(mapper.findDesktopPresenceByUserIds(any())).thenReturn(List.of(
                DesktopPresenceRow.builder().userId(2L).lastSeenAt(LocalDateTime.now()).build()));

        var responses = service.listMessages(1L, 5L, 50);

        var attachments = responses.get(0).attachments();
        assertThat(attachments).hasSize(2);
        assertThat(attachments.get(0).ownerDesktopOnline()).isTrue();
        assertThat(attachments.get(0).downloadUrl()).isEqualTo("/api/collaboration/files/9/content");
        assertThat(attachments.get(1).ownerDesktopOnline()).isFalse();
        assertThat(attachments.get(1).downloadUrl()).isNull();
        // presence 는 소유자 집합으로 1번만 벌크 조회한다
        verify(mapper).findDesktopPresenceByUserIds(any());
    }

    // ── 데스크톱 heartbeat upsert ──
    @Test
    void touchDesktopPresence_upsertsHeartbeat() {
        service.touchDesktopPresence(1L);
        verify(mapper).upsertDesktopPresence(1L);
    }

    // ══════════════ 방 설정 / 관리자 위임 (W5) ══════════════

    // ── 일반 멤버(MEMBER)는 방 설정 시트에 진입 불가 (FORBIDDEN) ──
    @Test
    void getConversationSettings_forbidsPlainMember() {
        when(mapper.findConversationById(30L)).thenReturn(CollaborationConversation.builder()
                .id(30L).type("GROUP").title("스터디").build());
        when(mapper.findMemberRole(30L, 9L)).thenReturn("MEMBER");

        assertThatThrownBy(() -> service.getConversationSettings(9L, 30L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ── OWNER 는 방 메타를 수정할 수 있고 감사 로그를 남긴다 ──
    @Test
    void updateConversationSettings_ownerUpdatesTitleAndNotice() {
        when(mapper.findConversationById(30L)).thenReturn(CollaborationConversation.builder()
                .id(30L).type("GROUP").title("옛 제목").invitePolicy("ALL_MEMBERS").build());
        when(mapper.findMemberRole(30L, 1L)).thenReturn("OWNER");
        when(mapper.findMemberDetails(30L)).thenReturn(List.of());
        when(mapper.findBans(30L)).thenReturn(List.of());
        when(mapper.findInviteAllowUserIds(30L)).thenReturn(List.of());
        when(mapper.findAudits(eq(30L), anyInt())).thenReturn(List.of());

        var response = service.updateConversationSettings(1L, 30L,
                new ConversationSettingsUpdateRequest("새 제목", null, "새 공지", null, null,
                        null, null, null, "MANAGERS", null, null));

        assertThat(response.conversationId()).isEqualTo(30L);
        ArgumentCaptor<CollaborationConversation> patchCaptor =
                ArgumentCaptor.forClass(CollaborationConversation.class);
        verify(mapper).updateConversationSettings(patchCaptor.capture());
        assertThat(patchCaptor.getValue().getTitle()).isEqualTo("새 제목");
        assertThat(patchCaptor.getValue().getNotice()).isEqualTo("새 공지");
        assertThat(patchCaptor.getValue().getInvitePolicy()).isEqualTo("MANAGERS");
        verify(mapper).insertAudit(eq(30L), eq(1L), eq(null), eq("ROOM_UPDATED"), any());
    }

    // ── 방 관리자 지정은 OWNER 만 가능. MANAGER 가 위임 시 FORBIDDEN ──
    @Test
    void updateMemberPermission_forbidsManagerFromDelegating() {
        when(mapper.findConversationById(30L)).thenReturn(CollaborationConversation.builder()
                .id(30L).type("GROUP").title("스터디").build());
        when(mapper.findMemberRole(30L, 5L)).thenReturn("MANAGER");
        when(mapper.findPermission(30L, 5L)).thenReturn(ConversationPermissionRow.builder()
                .conversationId(30L).userId(5L).canManageMembers(true).build());

        assertThatThrownBy(() -> service.updateMemberPermission(5L, 30L, 7L,
                new ConversationPermissionUpdateRequest(true, true, false, false, false, false, false)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(mapper, never()).updateMemberRole(eq(30L), eq(7L), any());
    }

    // ── OWNER 가 멤버를 MANAGER 로 승격하고 세부 권한을 부여 ──
    @Test
    void updateMemberPermission_ownerGrantsManagerWithFlags() {
        when(mapper.findConversationById(30L)).thenReturn(CollaborationConversation.builder()
                .id(30L).type("GROUP").title("스터디").build());
        when(mapper.findMemberRole(30L, 1L)).thenReturn("OWNER");
        when(mapper.findMemberDetail(30L, 7L)).thenReturn(ConversationMemberDetailRow.builder()
                .userId(7L).role("MEMBER").status("ACTIVE").build());
        when(mapper.findMemberDetails(30L)).thenReturn(List.of());
        when(mapper.findBans(30L)).thenReturn(List.of());
        when(mapper.findInviteAllowUserIds(30L)).thenReturn(List.of());
        when(mapper.findAudits(eq(30L), anyInt())).thenReturn(List.of());

        service.updateMemberPermission(1L, 30L, 7L,
                new ConversationPermissionUpdateRequest(true, true, false, false, true, false, false));

        verify(mapper).updateMemberRole(30L, 7L, "MANAGER");
        ArgumentCaptor<ConversationPermissionRow> permCaptor =
                ArgumentCaptor.forClass(ConversationPermissionRow.class);
        verify(mapper).upsertPermission(permCaptor.capture());
        assertThat(permCaptor.getValue().getCanKick()).isTrue();
        assertThat(permCaptor.getValue().getCanInvite()).isTrue();
        assertThat(permCaptor.getValue().getCanBan()).isFalse();
    }

    // ── ban 된 유저는 재입장(join) 불가 (FORBIDDEN) ──
    @Test
    void joinConversation_deniesBannedUser() {
        when(mapper.findConversationById(30L)).thenReturn(CollaborationConversation.builder()
                .id(30L).type("PUBLIC").title("공개방").build());
        when(mapper.countConversationMember(30L, 2L)).thenReturn(0);
        when(mapper.countBan(30L, 2L)).thenReturn(1);

        assertThatThrownBy(() -> service.joinConversation(2L, 30L, new JoinConversationRequest(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(mapper, never()).insertConversationMemberWithRole(eq(30L), eq(2L), any(), any());
    }

    // ── OWNER_ONLY 정책에서 일반 멤버 초대 시 FORBIDDEN ──
    @Test
    void inviteMembers_deniesWhenPolicyOwnerOnlyAndCallerIsMember() {
        when(mapper.findConversationById(30L)).thenReturn(CollaborationConversation.builder()
                .id(30L).type("GROUP").title("스터디").createdBy(1L).invitePolicy("OWNER_ONLY").build());
        when(mapper.countConversationMember(30L, 5L)).thenReturn(1);
        when(mapper.findMemberRole(30L, 5L)).thenReturn("MEMBER");

        assertThatThrownBy(() -> service.inviteMembers(5L, 30L, new InviteMembersRequest(List.of(7L), false)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(mapper, never()).insertConversationInvite(any(), any(), any(), anyBoolean());
    }

    // ── 재입장불가 강퇴(ban): 명단 등록 + 멤버 제거 + 감사 로그 ──
    @Test
    void banMember_ownerBansMemberAndRecordsAudit() {
        when(mapper.findConversationById(30L)).thenReturn(CollaborationConversation.builder()
                .id(30L).type("GROUP").title("스터디").build());
        when(mapper.findMemberRole(30L, 1L)).thenReturn("OWNER");
        when(mapper.findMemberDetail(30L, 8L)).thenReturn(ConversationMemberDetailRow.builder()
                .userId(8L).role("MEMBER").status("ACTIVE").build());
        when(mapper.findMemberDetails(30L)).thenReturn(List.of());
        when(mapper.findBans(30L)).thenReturn(List.of());
        when(mapper.findInviteAllowUserIds(30L)).thenReturn(List.of());
        when(mapper.findAudits(eq(30L), anyInt())).thenReturn(List.of());

        service.banMember(1L, 30L, 8L, new ConversationBanRequest("스팸"));

        verify(mapper).insertBan(30L, 8L, 1L, "스팸");
        verify(mapper).updateMemberStatus(30L, 8L, "REMOVED");
        verify(mapper).insertAudit(30L, 1L, 8L, "MEMBER_BANNED", "스팸");
    }

    private CollaborationUserRow user(Long id, String name, String email) {
        return CollaborationUserRow.builder()
                .id(id)
                .name(name)
                .email(email)
                .build();
    }

    private FriendRequestRow requestRow(Long id, Long requesterId, Long receiverId, String status) {
        return FriendRequestRow.builder()
                .id(id)
                .requesterId(requesterId)
                .requesterName("요청자")
                .requesterEmail("requester@example.com")
                .receiverId(receiverId)
                .receiverName("수신자")
                .receiverEmail("receiver@example.com")
                .status(status)
                .build();
    }
}
