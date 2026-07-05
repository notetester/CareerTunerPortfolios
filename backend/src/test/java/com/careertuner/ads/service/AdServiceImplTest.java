package com.careertuner.ads.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.ads.domain.Advertisement;
import com.careertuner.ads.dto.AdClickResponse;
import com.careertuner.ads.dto.AdResponse;
import com.careertuner.ads.mapper.AdvertisementMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

class AdServiceImplTest {

    private final AdvertisementMapper mapper = mock(AdvertisementMapper.class);
    private final AdServiceImpl service = new AdServiceImpl(mapper);

    private Advertisement ad(long id, int priority, int weight) {
        return Advertisement.builder()
                .id(id).title("광고" + id).placement("HOME_BANNER").targetPlatform("ALL")
                .active(true).priority(priority).weight(weight)
                .impressionCount(0L).clickCount(0L)
                .build();
    }

    @Test
    void paidPlanUserGetsNoAds() {
        when(mapper.findUserPlan(7L)).thenReturn("PRO");

        List<AdResponse> result = service.serve(7L, "HOME_BANNER", "WEB", 1);

        assertThat(result).isEmpty();
        // 유료플랜은 후보 조회 자체를 하지 않는다(단락).
        verify(mapper, never()).findServable(anyString(), anyString(), any());
    }

    @Test
    void freePlanUserGetsAds() {
        when(mapper.findUserPlan(7L)).thenReturn("FREE");
        when(mapper.findServable(eq("HOME_BANNER"), eq("WEB"), any(LocalDateTime.class)))
                .thenReturn(List.of(ad(1L, 0, 1)));

        List<AdResponse> result = service.serve(7L, "HOME_BANNER", "WEB", 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void anonymousUserGetsAds() {
        when(mapper.findServable(eq("SIDEBAR"), eq("WEB"), any(LocalDateTime.class)))
                .thenReturn(List.of(ad(2L, 0, 1)));

        List<AdResponse> result = service.serve(null, "SIDEBAR", "WEB", 1);

        assertThat(result).hasSize(1);
        verify(mapper, never()).findUserPlan(any());
    }

    @Test
    void onlyHighestPriorityGroupIsEligible() {
        // priority 10 광고만 후보에 남아야 한다(priority 0 은 제외).
        when(mapper.findServable(anyString(), anyString(), any()))
                .thenReturn(List.of(ad(1L, 10, 1), ad(2L, 10, 1), ad(3L, 0, 100)));

        List<AdResponse> result = service.serve(null, "HOME_BANNER", "WEB", 5);

        assertThat(result).extracting(AdResponse::id).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result).extracting(AdResponse::id).doesNotContain(3L);
    }

    @Test
    void serveDoesNotReturnDuplicates() {
        when(mapper.findServable(anyString(), anyString(), any()))
                .thenReturn(List.of(ad(1L, 0, 1), ad(2L, 0, 1)));

        List<AdResponse> result = service.serve(null, "FEED_INLINE", "APP", 5);

        assertThat(result).extracting(AdResponse::id).doesNotHaveDuplicates();
        assertThat(result).hasSize(2);
    }

    @Test
    void invalidPlacementRejected() {
        assertThatThrownBy(() -> service.serve(null, "UNKNOWN", "WEB", 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void unknownPlatformFallsBackToWeb() {
        // 알 수 없는 플랫폼은 WEB 으로 정규화된다 → WEB 매치 mock 이 응답하면 통과.
        when(mapper.findServable(eq("HOME_BANNER"), eq("WEB"), any(LocalDateTime.class)))
                .thenReturn(List.of(ad(1L, 0, 1)));

        List<AdResponse> result = service.serve(null, "HOME_BANNER", "ios-garbage", 1);

        assertThat(result).hasSize(1);
        verify(mapper).findServable(eq("HOME_BANNER"), eq("WEB"), any(LocalDateTime.class));
    }

    @Test
    void clickIncrementsAndReturnsLink() {
        Advertisement ad = ad(5L, 0, 1);
        ad.setLinkUrl("https://example.com");
        when(mapper.findById(5L)).thenReturn(ad);

        AdClickResponse response = service.recordClick(5L);

        assertThat(response.linkUrl()).isEqualTo("https://example.com");
        verify(mapper).increaseClick(5L);
    }

    @Test
    void clickOnMissingAdThrows() {
        when(mapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.recordClick(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(mapper, never()).increaseClick(any());
    }
}
