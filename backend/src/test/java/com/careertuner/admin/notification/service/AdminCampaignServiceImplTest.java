package com.careertuner.admin.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.admin.notification.dto.AdminCampaignRequest;
import com.careertuner.admin.notification.mapper.AdminCampaignRecipientMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

class AdminCampaignServiceImplTest {

    private static final AuthUser ADMIN = new AuthUser(1L, "admin@careertuner.dev", "ADMIN");

    private final AdminCampaignRecipientMapper recipientMapper = mock(AdminCampaignRecipientMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AdminCampaignServiceImpl service =
            new AdminCampaignServiceImpl(recipientMapper, notificationService);

    @Test
    void executableCampaignLinkIsRejectedBeforeFanout() {
        AdminCampaignRequest request =
                new AdminCampaignRequest("NOTICE", "공지", "내용", "javascript:alert(document.domain)");

        assertThatThrownBy(() -> service.sendCampaign(ADMIN, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(recipientMapper, never()).findActiveUserIds();
        verify(notificationService, never()).notify(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void internalCampaignLinkIsTrimmedAndDelivered() {
        when(recipientMapper.findActiveUserIds()).thenReturn(List.of(10L));
        AdminCampaignRequest request =
                new AdminCampaignRequest("notice", "공지", "내용", "  /support/notices/10  ");

        var result = service.sendCampaign(ADMIN, request);

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).notify(notification.capture());
        assertThat(notification.getValue().getLink()).isEqualTo("/support/notices/10");
        assertThat(result.recipients()).isEqualTo(1);
    }
}
