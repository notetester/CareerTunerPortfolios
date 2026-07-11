package com.careertuner.admin.ads.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import com.careertuner.admin.ads.dto.AdminAdRequest;
import com.careertuner.admin.ads.dto.AdminAdResponse;
import com.careertuner.ads.domain.Advertisement;
import com.careertuner.ads.mapper.AdvertisementMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

class AdminAdServiceImplTest {

    private static final AuthUser ADMIN = new AuthUser(1L, "admin@careertuner.dev", "ADMIN");
    private static final AuthUser USER = new AuthUser(2L, "user@careertuner.dev", "USER");

    private final AdvertisementMapper mapper = mock(AdvertisementMapper.class);
    private final AdminAdServiceImpl service = new AdminAdServiceImpl(mapper);

    private AdminAdRequest request(String placement, String platform,
                                   LocalDateTime start, LocalDateTime end) {
        return new AdminAdRequest("여름 프로모션", null, "https://c.example",
                placement, platform, start, end, true, 5, 3);
    }

    @Test
    void nonAdminCannotCreate() {
        assertThatThrownBy(() -> service.create(USER, request("HOME_BANNER", "WEB", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(mapper, never()).insert(any());
    }

    @Test
    void createNormalizesAndPersists() {
        // insert 후 findById 로 재조회하므로 id 를 채워 반환하도록 스텁.
        when(mapper.findById(any())).thenAnswer((InvocationOnMock inv) ->
                Advertisement.builder().id(10L).title("여름 프로모션").placement("HOME_BANNER")
                        .targetPlatform("WEB").active(true).priority(5).weight(3)
                        .impressionCount(0L).clickCount(0L).build());

        AdminAdResponse result = service.create(ADMIN, request("home_banner", "web", null, null));

        // insert 파라미터가 대문자 정규화되었는지 확인
        verify(mapper).insert(org.mockito.ArgumentMatchers.argThat(ad ->
                "HOME_BANNER".equals(ad.getPlacement()) && "WEB".equals(ad.getTargetPlatform())
                        && ad.getCreatedBy().equals(1L)));
        assertThat(result.placement()).isEqualTo("HOME_BANNER");
    }

    @Test
    void blankPlatformDefaultsToAll() {
        when(mapper.findById(any())).thenReturn(
                Advertisement.builder().id(11L).title("t").placement("SIDEBAR")
                        .targetPlatform("ALL").active(true).priority(0).weight(1)
                        .impressionCount(0L).clickCount(0L).build());

        service.create(ADMIN, request("SIDEBAR", "  ", null, null));

        verify(mapper).insert(org.mockito.ArgumentMatchers.argThat(ad ->
                "ALL".equals(ad.getTargetPlatform())));
    }

    @Test
    void endBeforeStartRejected() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 10, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 5, 0, 0);

        assertThatThrownBy(() -> service.create(ADMIN, request("HOME_BANNER", "WEB", start, end)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(mapper, never()).insert(any());
    }

    @Test
    void invalidPlacementRejected() {
        assertThatThrownBy(() -> service.create(ADMIN, request("POPUP", "WEB", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void executableOrProtocolRelativeLinkIsRejectedBeforeInsert() {
        AdminAdRequest script = new AdminAdRequest("광고", null, "javascript:alert(1)",
                "HOME_BANNER", "WEB", null, null, true, 0, 1);
        AdminAdRequest protocolRelative = new AdminAdRequest("광고", null, "//evil.example",
                "HOME_BANNER", "WEB", null, null, true, 0, 1);

        assertThatThrownBy(() -> service.create(ADMIN, script))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> service.create(ADMIN, protocolRelative))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(mapper, never()).insert(any());
    }

    @Test
    void deleteMissingAdThrows() {
        when(mapper.findById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(ADMIN, 404L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(mapper, never()).delete(any());
    }
}
