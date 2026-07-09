package com.careertuner.jobposting.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/**
 * Cloudinary 기반 공고 파일 저장 provider. 사적 원본이라 <b>authenticated delivery + 백엔드 서명 업로드</b>.
 *
 * <p>reference 포맷: {@code cloudinary:{resourceType}/{deliveryType}/{format}/{publicId}}
 * <ul>
 *   <li>{@code publicId = {folder}/{caseId}/{name}} (슬래시 포함) → 파싱은 4-way split(마지막이 publicId).</li>
 *   <li>format 없음(raw)은 {@code '-'} 로 표기. resourceType/type/format 은 read 때 delivery URL 복원용.</li>
 * </ul>
 *
 * <p>{@code path()=null} → OCR 은 sendBytes(base64) 로만 성립({@code JOB_POSTING_AI_WORKER_SEND_BYTES=true}).
 * <p><b>주의</b>: 실 업로드/다운로드 e2e 는 자격증명 준비 후 검증. 현재는 컴파일(SDK API)·순수 로직 단위테스트만.
 */
public class CloudinaryJobPostingStorageProvider implements JobPostingStorageProvider {

    static final String SCHEME = "cloudinary";
    private static final String NO_FORMAT = "-";

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;
    private final HttpClient httpClient;

    public CloudinaryJobPostingStorageProvider(Cloudinary cloudinary, CloudinaryProperties properties) {
        this(cloudinary, properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    CloudinaryJobPostingStorageProvider(Cloudinary cloudinary, CloudinaryProperties properties, HttpClient httpClient) {
        this.cloudinary = cloudinary;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public String scheme() {
        return SCHEME;
    }

    @Override
    public Written write(Long applicationCaseId, String storedName, byte[] bytes, String contentType) {
        String publicId = "%s/%d/%s".formatted(properties.getFolder(), applicationCaseId, stripExtension(storedName));
        try {
            Map result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "resource_type", "auto",
                    "type", properties.getDeliveryType(),
                    "public_id", publicId,
                    // dynamic folder mode 계정에선 public_id 의 슬래시가 Media Library 폴더로 이어지지 않으므로,
                    // 표시 폴더를 asset_folder 로 명시해 공고 파일을 한 폴더(properties.folder)로 묶는다.
                    // public_id 는 그대로라 delivery URL·교차접근 방지 prefix 검증은 영향 없음.
                    "asset_folder", properties.getFolder(),
                    "overwrite", true,
                    "use_filename", false,
                    "unique_filename", false));
            String resourceType = string(result.get("resource_type"), "image");
            String type = string(result.get("type"), properties.getDeliveryType());
            String returnedPublicId = string(result.get("public_id"), publicId);
            String format = string(result.get("format"), NO_FORMAT);
            return new Written(buildReference(resourceType, type, format, returnedPublicId), null);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "공고문 파일을 저장하지 못했습니다.");
        }
    }

    @Override
    public Loaded read(Long applicationCaseId, String fileReference) {
        Ref ref = parseReference(fileReference);
        // 지원건 교차접근 방지: publicId 가 {folder}/{caseId}/ 로 시작해야 한다(로컬 provider 의 caseId 검증과 동일 취지).
        String expectedPrefix = "%s/%d/".formatted(properties.getFolder(), applicationCaseId);
        if (!ref.publicId().startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원 건과 공고 파일 참조가 일치하지 않습니다.");
        }
        String deliveryId = NO_FORMAT.equals(ref.format()) ? ref.publicId() : ref.publicId() + "." + ref.format();
        String url = cloudinary.url()
                .resourceType(ref.resourceType())
                .type(ref.type())
                .secure(true)
                .signed(true)
                .generate(deliveryId);
        byte[] bytes = httpGet(url);
        String base = lastSegment(ref.publicId());
        String storedName = NO_FORMAT.equals(ref.format()) ? base : base + "." + ref.format();
        return new Loaded(bytes, null, storedName);
    }

    private byte[] httpGet(String url) {
        try {
            HttpResponse<byte[]> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "저장된 공고 파일을 찾을 수 없습니다.");
            }
            return resp.body();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "저장된 공고 파일을 찾을 수 없습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "공고 파일 조회가 중단되었습니다.");
        }
    }

    static String buildReference(String resourceType, String type, String format, String publicId) {
        String fmt = (format == null || format.isBlank()) ? NO_FORMAT : format;
        return "%s:%s/%s/%s/%s".formatted(SCHEME, resourceType, type, fmt, publicId);
    }

    static Ref parseReference(String fileReference) {
        if (fileReference == null || !fileReference.startsWith(SCHEME + ":")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장된 공고 파일 참조가 올바르지 않습니다.");
        }
        String body = fileReference.substring((SCHEME + ":").length());
        String[] parts = body.split("/", 4);
        if (parts.length != 4 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank() || parts[3].isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장된 공고 파일 참조가 올바르지 않습니다.");
        }
        if (parts[3].contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장된 공고 파일 참조가 올바르지 않습니다.");
        }
        return new Ref(parts[0], parts[1], parts[2], parts[3]);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String lastSegment(String publicId) {
        int slash = publicId.lastIndexOf('/');
        return slash >= 0 ? publicId.substring(slash + 1) : publicId;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    record Ref(String resourceType, String type, String format, String publicId) {
    }
}
