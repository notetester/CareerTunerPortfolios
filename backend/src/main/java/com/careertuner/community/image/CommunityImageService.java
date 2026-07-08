package com.careertuner.community.image;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/**
 * 커뮤니티/공지/FAQ 본문 리치텍스트 에디터의 첨부 이미지를 Cloudinary(public)에 올린다.
 *
 * <p>저장 스택은 B(공고)가 도입한 {@link Cloudinary} 빈을 재사용한다. 다만 본문 이미지는 누구에게나 {@code <img>}로
 * 보여야 하므로 authenticated 가 아닌 <b>public(type=upload)</b> delivery 로 올리고, 반환한 {@code secure_url}을
 * 그대로 본문 HTML에 인라인 저장한다(별도 서빙 엔드포인트·이미지 테이블 없음).
 *
 * <p>Cloudinary 자격증명 미설정 환경은 {@code Cloudinary} 빈이 없어 업로드가 SERVICE_UNAVAILABLE 로 degrade 한다.
 */
@Service
public class CommunityImageService {

    /** 폴더 분리용 scope — B(application-postings)와 물리 분리되는 f/{scope} 하위. */
    public static final Set<String> SCOPES = Set.of("community", "notice", "faq");

    private static final Set<String> IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final ObjectProvider<Cloudinary> cloudinaryProvider;
    private final CommunityImageProperties properties;

    public CommunityImageService(ObjectProvider<Cloudinary> cloudinaryProvider,
                                 CommunityImageProperties properties) {
        this.cloudinaryProvider = cloudinaryProvider;
        this.properties = properties;
    }

    /**
     * 이미지를 public 으로 올리고 인라인 삽입용 {@code secure_url}(절대 https)을 돌려준다.
     *
     * @param scope   community/notice/faq — 폴더 분리 키
     * @param ownerId 업로더 사용자 id — 폴더 분할(감사/정리용)
     */
    public String upload(String scope, Long ownerId, MultipartFile file) {
        String normalizedScope = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        if (!SCOPES.contains(normalizedScope)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 저장 위치가 올바르지 않습니다.");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 이미지를 선택해 주세요.");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미지는 " + (properties.getMaxFileSizeBytes() / (1024 * 1024)) + "MB 이하만 올릴 수 있습니다.");
        }
        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "PNG, JPG, WEBP, GIF 이미지만 올릴 수 있습니다.");
        }

        Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
        if (cloudinary == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "이미지 업로드가 아직 구성되지 않았습니다. 관리자에게 문의해 주세요.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "이미지를 저장하지 못했습니다.");
        }

        String folder = "%s/%s".formatted(properties.getFolder(), normalizedScope);
        String publicId = "%d/%s".formatted(ownerId, UUID.randomUUID());
        try {
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "resource_type", "image",
                    "type", "upload",          // public delivery — <img src>로 바로 접근
                    "folder", folder,
                    "public_id", publicId,
                    "overwrite", true,
                    "use_filename", false,
                    "unique_filename", false));
            Object secureUrl = result.get("secure_url");
            if (secureUrl == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "이미지를 저장하지 못했습니다.");
            }
            return secureUrl.toString();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "이미지를 저장하지 못했습니다.");
        }
    }
}
