package com.careertuner.ad.service;

import java.util.List;

import com.careertuner.ad.dto.AdCampaignRequest;
import com.careertuner.ad.dto.AdCampaignResponse;
import com.careertuner.common.security.AuthUser;

public interface AdCampaignService {

    List<AdCampaignResponse> visibleAds(Long userId, String surface);

    void recordEvent(Long userId, Long campaignId, String surface, String eventType);

    List<AdCampaignResponse> adminList(AuthUser authUser, String surface, Boolean active, String keyword, int limit);

    AdCampaignResponse adminCreate(AuthUser authUser, AdCampaignRequest request);

    AdCampaignResponse adminUpdate(AuthUser authUser, Long id, AdCampaignRequest request);
}
