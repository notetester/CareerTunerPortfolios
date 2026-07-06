package com.careertuner.ads.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.ads.domain.Advertisement;
import com.careertuner.ads.dto.AdClickResponse;
import com.careertuner.ads.dto.AdResponse;
import com.careertuner.ads.mapper.AdvertisementMapper;
import com.careertuner.ads.service.AdService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.file.service.FileService;

import lombok.RequiredArgsConstructor;

/**
 * 광고 공개 노출 API. 로그인/비로그인 모두 접근하며(권한 없음), 유료플랜 사용자에게는 서비스가 빈 응답을 준다.
 * <p>비로그인 접근 허용은 SecurityConfig(공통 영역) 배선이 필요 — 오케스트레이터에 permitAll 요청으로 보고한다.</p>
 */
@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;
    private final AdvertisementMapper adMapper;
    private final FileService fileService;

    /**
     * 배치·플랫폼 매치 광고 조회. userId 는 로그인 시에만 채워지고, 비로그인이면 null → 무료 취급(노출).
     *
     * @param placement HOME_BANNER/FEED_INLINE/SIDEBAR/INTERSTITIAL
     * @param platform  WEB/APP/DESKTOP(미지정 시 WEB)
     * @param limit     최대 노출 수(기본 1)
     */
    @GetMapping
    public ApiResponse<List<AdResponse>> serve(@AuthenticationPrincipal AuthUser authUser,
                                               @RequestParam String placement,
                                               @RequestParam(required = false) String platform,
                                               @RequestParam(required = false, defaultValue = "1") int limit) {
        Long userId = authUser != null ? authUser.id() : null;
        return ApiResponse.ok(adService.serve(userId, placement, platform, limit));
    }

    /** 노출 집계 +1(가시성 도달 시 프런트가 1회 발사). best-effort. */
    @PostMapping("/{id}/impression")
    public ApiResponse<Void> impression(@PathVariable Long id) {
        adService.recordImpression(id);
        return ApiResponse.ok();
    }

    /** 클릭 집계 +1 후 이동 URL 반환. SPA 라 302 대신 URL 을 프런트가 이동. */
    @PostMapping("/{id}/click")
    public ApiResponse<AdClickResponse> click(@PathVariable Long id) {
        return ApiResponse.ok(adService.recordClick(id));
    }

    /** 광고 이미지 바이트. 해당 광고가 참조하는 file_asset 만 서빙(임의 파일 접근 방지). */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        Advertisement ad = adMapper.findById(id);
        if (ad == null || ad.getImageFileId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "광고 이미지를 찾을 수 없습니다.");
        }
        FileService.Download download = fileService.downloadAfterAccessCheck(ad.getImageFileId());
        String contentType = download.asset().getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        contentType == null || contentType.isBlank()
                                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(download.bytes());
    }
}
