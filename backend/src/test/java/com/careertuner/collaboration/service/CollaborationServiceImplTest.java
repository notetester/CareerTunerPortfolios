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
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.careertuner.collaboration.domain.CollaborationConversation;
import com.careertuner.collaboration.domain.CollaborationMessage;
import com.careertuner.collaboration.domain.CollaborationUserRow;
import com.careertuner.collaboration.domain.ConversationMemberRow;
import com.careertuner.collaboration.domain.ConversationSummaryRow;
import com.careertuner.collaboration.domain.DesktopPresenceRow;
import com.careertuner.collaboration.domain.FriendRequest;
import com.careertuner.collaboration.domain.FriendRequestRow;
import com.careertuner.collaboration.domain.MessageAttachmentRow;
import com.careertuner.collaboration.dto.CreateConversationRequest;
import com.careertuner.collaboration.dto.InviteMembersRequest;
import com.careertuner.collaboration.dto.JoinConversationRequest;
import com.careertuner.collaboration.dto.SendMessageRequest;
import com.careertuner.collaboration.mapper.CollaborationMapper;
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
    private final CollaborationServiceImpl service =
            new CollaborationServiceImpl(mapper, notificationService, notificationPreferenceService,
                    fileAssetMapper, fileService, passwordEncoder, privacyPolicyService);

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
                .originalName("portfolio.pdf")
                .sizeBytes(1200L)
                .build());
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
        verify(fileAssetMapper).updateRef(9L, "COLLAB_MESSAGE", 20L);
        verify(notificationService).notify(any(Notification.class));
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
        verify(mapper, never()).insertConversationMemberWithRole(30L, 2L, "MEMBER", 1L);
        verify(mapper).insertConversationInvite(30L, 1L, 3L, false);
        verify(mapper).insertConversationMemberWithRole(30L, 3L, "MEMBER", 1L);
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
