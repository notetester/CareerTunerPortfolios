package com.careertuner.ad.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.ad.domain.AdCampaign;
import com.careertuner.ad.dto.AdCampaignRequest;
import com.careertuner.ad.dto.AdCampaignResponse;
import com.careertuner.ad.mapper.AdCampaignMapper;
import com.careertuner.admin.common.AdminAccess;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdCampaignServiceImpl implements AdCampaignService {

    private static final Set<String> PAID_AD_FREE_PLANS = Set.of("BASIC", "PRO", "PREMIUM");
    private static final List<String> DEFAULT_VISIBLE_PLANS = List.of("FREE");

    private final AdCampaignMapper adMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<AdCampaignResponse> visibleAds(Long userId, String surface) {
        String plan = "FREE";
        if (userId != null) {
            User user = userMapper.findById(userId);
            if (user != null && user.getPlan() != null) {
                plan = user.getPlan().toUpperCase(Locale.ROOT);
            }
        }
        if (PAID_AD_FREE_PLANS.contains(plan)) {
            return List.of();
        }
        String normalizedSurface = normalizeSurface(surface);
        String finalPlan = plan;
        return adMapper.findVisible(normalizedSurface, 20).stream()
                .filter(campaign -> visiblePlans(campaign).contains(finalPlan))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void recordEvent(Long userId, Long campaignId, String surface, String eventType) {
        AdCampaign campaign = adMapper.findById(campaignId);
        if (campaign == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "광고 캠페인을 찾을 수 없습니다.");
        }
        adMapper.insertEvent(campaignId, userId, normalizeSurface(surface), normalizeEvent(eventType));
    }

    @Override
    public List<AdCampaignResponse> adminList(AuthUser authUser, String surface, Boolean active, String keyword, int limit) {
        AdminAccess.requireAdmin(authUser);
        return adMapper.findAdmin(blankToNull(surface) == null ? null : normalizeSurface(surface), active,
                        blankToNull(keyword), Math.max(1, Math.min(limit, 200)))
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public AdCampaignResponse adminCreate(AuthUser authUser, AdCampaignRequest request) {
        AdminAccess.requireAdmin(authUser);
        AdCampaign campaign = new AdCampaign();
        apply(campaign, request);
        campaign.setCreatedBy(authUser.id());
        campaign.setUpdatedBy(authUser.id());
        adMapper.insert(campaign);
        return toResponse(adMapper.findById(campaign.getId()));
    }

    @Override
    @Transactional
    public AdCampaignResponse adminUpdate(AuthUser authUser, Long id, AdCampaignRequest request) {
        AdminAccess.requireAdmin(authUser);
        AdCampaign campaign = adMapper.findById(id);
        if (campaign == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "광고 캠페인을 찾을 수 없습니다.");
        }
        apply(campaign, request);
        campaign.setUpdatedBy(authUser.id());
        adMapper.update(campaign);
        return toResponse(adMapper.findById(id));
    }

    private void apply(AdCampaign campaign, AdCampaignRequest request) {
        campaign.setTitle(requireText(request.title(), "광고 제목을 입력해 주세요."));
        campaign.setBody(blankToNull(request.body()));
        campaign.setSurface(normalizeSurface(request.surface()));
        campaign.setPlacement(blankToNull(request.placement()) == null ? "GLOBAL_TOP" : request.placement().trim());
        campaign.setCreativeType(normalizeCreative(request.creativeType()));
        campaign.setImageUrl(blankToNull(request.imageUrl()));
        campaign.setTargetUrl(blankToNull(request.targetUrl()));
        campaign.setVisibleToPlansJson(toJson(request.visibleToPlans() == null || request.visibleToPlans().isEmpty()
                ? DEFAULT_VISIBLE_PLANS : request.visibleToPlans().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList()));
        campaign.setStartsAt(request.startsAt());
        campaign.setEndsAt(request.endsAt());
        campaign.setPriority(request.priority() == null ? 100 : Math.max(1, request.priority()));
        campaign.setActive(request.active() == null || request.active());
    }

    private AdCampaignResponse toResponse(AdCampaign c) {
        return new AdCampaignResponse(c.getId(), c.getTitle(), c.getBody(), c.getSurface(), c.getPlacement(),
                c.getCreativeType(), c.getImageUrl(), c.getTargetUrl(), visiblePlans(c), c.getStartsAt(),
                c.getEndsAt(), c.getPriority(), c.isActive(), c.getCreatedAt(), c.getUpdatedAt());
    }

    @SuppressWarnings("unchecked")
    private List<String> visiblePlans(AdCampaign campaign) {
        String json = campaign.getVisibleToPlansJson();
        if (json == null || json.isBlank()) {
            return DEFAULT_VISIBLE_PLANS;
        }
        try {
            Object value = objectMapper.readValue(json, Object.class);
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).map(s -> s.toUpperCase(Locale.ROOT)).toList();
            }
        } catch (Exception ignored) {
            return DEFAULT_VISIBLE_PLANS;
        }
        return DEFAULT_VISIBLE_PLANS;
    }

    private String normalizeSurface(String value) {
        String surface = value == null || value.isBlank() ? "WEB" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("WEB", "MOBILE", "DESKTOP", "ALL").contains(surface)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "광고 surface 값이 올바르지 않습니다.");
        }
        return surface;
    }

    private String normalizeCreative(String value) {
        String creative = value == null || value.isBlank() ? "BANNER" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("BANNER", "CARD", "TEXT").contains(creative)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "광고 소재 유형이 올바르지 않습니다.");
        }
        return creative;
    }

    private String normalizeEvent(String value) {
        String event = value == null || value.isBlank() ? "IMPRESSION" : value.trim().toUpperCase(Locale.ROOT);
        return "CLICK".equals(event) ? "CLICK" : "IMPRESSION";
    }

    private String requireText(String value, String message) {
        String text = blankToNull(value);
        if (text == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return text;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "광고 노출 플랜 JSON을 저장할 수 없습니다.");
        }
    }
}
