package com.careertuner.collaboration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.careertuner.collaboration.domain.CollaborationConversation;
import com.careertuner.collaboration.domain.CollaborationMessage;
import com.careertuner.collaboration.domain.CollaborationUserRow;
import com.careertuner.collaboration.domain.ConversationMemberRow;
import com.careertuner.collaboration.domain.ConversationSummaryRow;
import com.careertuner.collaboration.domain.FriendRequest;
import com.careertuner.collaboration.domain.FriendRequestRow;
import com.careertuner.collaboration.domain.MessageAttachmentRow;
import com.careertuner.collaboration.dto.CreateConversationRequest;
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

class CollaborationServiceImplTest {

    private final CollaborationMapper mapper = mock(CollaborationMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationPreferenceService notificationPreferenceService =
            mock(NotificationPreferenceService.class);
    private final FileAssetMapper fileAssetMapper = mock(FileAssetMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final CollaborationServiceImpl service =
            new CollaborationServiceImpl(mapper, notificationService, notificationPreferenceService,
                    fileAssetMapper, fileService, passwordEncoder);

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
