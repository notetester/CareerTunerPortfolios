package com.careertuner.admin.ads.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.ads.dto.AdminAdRequest;
import com.careertuner.admin.ads.dto.AdminAdResponse;
import com.careertuner.admin.common.AdminAccess;
import com.careertuner.ads.domain.Advertisement;
import com.careertuner.ads.mapper.AdvertisementMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 광고 관리. role(AdminAccess) 검사 + 컨트롤러의 {@code @RequireAdminPermission} 이중 방어.
 */
@Service
@RequiredArgsConstructor
public class AdminAdServiceImpl implements AdminAdService {

    private static final Set<String> PLACEMENTS =
            Set.of("HOME_BANNER", "FEED_INLINE", "SIDEBAR", "INTERSTITIAL");
    private static final Set<String> PLATFORMS = Set.of("WEB", "APP", "DESKTOP", "ALL");

    private final AdvertisementMapper adMapper;

    @Override
    public List<AdminAdResponse> list(AuthUser authUser, String placement, String platform, boolean activeOnly) {
        AdminAccess.requireAdmin(authUser);
        return adMapper.findForAdmin(normalizeFilter(placement, PLACEMENTS),
                        normalizeFilter(platform, PLATFORMS), activeOnly)
                .stream()
                .map(AdminAdResponse::from)
                .toList();
    }

    @Override
    public AdminAdResponse get(AuthUser authUser, Long id) {
        AdminAccess.requireAdmin(authUser);
        return AdminAdResponse.from(require(id));
    }

    @Override
    @Transactional
    public AdminAdResponse create(AuthUser authUser, AdminAdRequest request) {
        AdminAccess.requireAdmin(authUser);
        Advertisement ad = Advertisement.builder()
                .title(trimRequired(request.title(), "제목"))
                .imageFileId(request.imageFileId())
                .linkUrl(blankToNull(request.linkUrl()))
                .placement(requirePlacement(request.placement()))
                .targetPlatform(normalizePlatform(request.targetPlatform()))
                .startAt(request.startAt())
                .endAt(request.endAt())
                .active(request.active() == null || request.active())
                .priority(request.priority() != null ? request.priority() : 0)
                .weight(normalizeWeight(request.weight()))
                .createdBy(authUser.id())
                .build();
        validatePeriod(ad.getStartAt(), ad.getEndAt());
        adMapper.insert(ad);
        return AdminAdResponse.from(adMapper.findById(ad.getId()));
    }

    @Override
    @Transactional
    public AdminAdResponse update(AuthUser authUser, Long id, AdminAdRequest request) {
        AdminAccess.requireAdmin(authUser);
        Advertisement ad = require(id);
        ad.setTitle(trimRequired(request.title(), "제목"));
        ad.setImageFileId(request.imageFileId());
        ad.setLinkUrl(blankToNull(request.linkUrl()));
        ad.setPlacement(requirePlacement(request.placement()));
        ad.setTargetPlatform(normalizePlatform(request.targetPlatform()));
        ad.setStartAt(request.startAt());
        ad.setEndAt(request.endAt());
        ad.setActive(request.active() == null || request.active());
        ad.setPriority(request.priority() != null ? request.priority() : 0);
        ad.setWeight(normalizeWeight(request.weight()));
        validatePeriod(ad.getStartAt(), ad.getEndAt());
        adMapper.update(ad);
        return AdminAdResponse.from(adMapper.findById(id));
    }

    @Override
    @Transactional
    public AdminAdResponse toggleActive(AuthUser authUser, Long id, boolean active) {
        AdminAccess.requireAdmin(authUser);
        require(id);
        adMapper.updateActive(id, active);
        return AdminAdResponse.from(adMapper.findById(id));
    }

    @Override
    @Transactional
    public void delete(AuthUser authUser, Long id) {
        AdminAccess.requireAdmin(authUser);
        require(id);
        adMapper.delete(id);
    }

    // ── 내부 ──

    private Advertisement require(Long id) {
        Advertisement ad = adMapper.findById(id);
        if (ad == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "광고를 찾을 수 없습니다.");
        }
        return ad;
    }

    private String requirePlacement(String placement) {
        String upper = placement == null ? "" : placement.trim().toUpperCase(Locale.ROOT);
        if (!PLACEMENTS.contains(upper)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 광고 배치입니다.");
        }
        return upper;
    }

    private String normalizePlatform(String platform) {
        String upper = platform == null ? "" : platform.trim().toUpperCase(Locale.ROOT);
        if (upper.isBlank()) {
            return "ALL";
        }
        if (!PLATFORMS.contains(upper)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 타겟 플랫폼입니다.");
        }
        return upper;
    }

    private int normalizeWeight(Integer weight) {
        return weight != null && weight > 0 ? weight : 1;
    }

    private void validatePeriod(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "게재 종료는 시작보다 뒤여야 합니다.");
        }
    }

    private String trimRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, field + "은(는) 필수입니다.");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 필터 파라미터 정규화 — 화이트리스트 밖이면 null(무시). */
    private String normalizeFilter(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(upper) ? upper : null;
    }
}
