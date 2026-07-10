package com.careertuner.community.image;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 커뮤니티/공지/FAQ 리치텍스트 에디터 첨부 이미지 업로드.
 *
 * <p>{@code POST /api/community/images} — 인증 사용자만(공통 SecurityConfig 의 {@code anyRequest().authenticated()}).
 * scope 로 저장 폴더를 나누고, 관리자 전용 화면인 notice/faq scope 는 추가로 관리자 권한을 요구한다.
 * 이미지는 Cloudinary(public)에 올리고 반환한 secure_url 을 프런트가 본문 HTML 의 {@code <img src>}로 인라인 저장한다.
 */
@RestController
@RequestMapping("/api/community/images")
@RequiredArgsConstructor
public class CommunityImageController {

    private final CommunityImageService imageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> upload(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "scope", defaultValue = "community") String scope) {
        // 공지/FAQ 본문 이미지는 관리자 화면에서만 올린다 → 관리자 권한 강제(커뮤니티 scope 는 일반 인증 사용자).
        if ("notice".equalsIgnoreCase(scope) || "faq".equalsIgnoreCase(scope)) {
            AdminAccess.requireAdmin(authUser);
        }
        String url = imageService.upload(scope, authUser.id(), file);
        return ApiResponse.ok(Map.of("url", url));
    }
}
