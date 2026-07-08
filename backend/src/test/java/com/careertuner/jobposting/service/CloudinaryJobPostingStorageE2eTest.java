package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.careertuner.jobposting.service.JobPostingStorageProvider.Loaded;
import com.careertuner.jobposting.service.JobPostingStorageProvider.Written;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

/**
 * Cloudinary 실 네트워크 e2e — {@code CAREERTUNER_UPLOADS_CLOUDINARY_API_SECRET} env 가 있을 때만 실행(없으면 <b>skip</b>).
 * CI/일반 빌드에선 skip. 3개 중 일부만 설정되면 assertion 으로 실패해 설정 누락을 잡는다.
 *
 * <p>실행(세션 env 세팅 후, 값은 로그/커밋에 넣지 않음 — System.getenv 로만 읽음):
 * <pre>
 *   $env:CAREERTUNER_UPLOADS_CLOUDINARY_CLOUD_NAME="..."
 *   $env:CAREERTUNER_UPLOADS_CLOUDINARY_API_KEY="..."
 *   $env:CAREERTUNER_UPLOADS_CLOUDINARY_API_SECRET="..."
 *   .\gradlew.bat test --tests "*CloudinaryJobPostingStorageE2eTest" --rerun-tasks --no-daemon
 * </pre>
 *
 * <p>검증: write(실 업로드) → read(서명 authenticated URL 다운로드) → 원본 바이트 정확 복원(OCR 요건).
 * 업로드한 테스트 asset 은 finally 에서 best-effort 삭제(잔존 방지).
 */
@EnabledIfEnvironmentVariable(named = "CAREERTUNER_UPLOADS_CLOUDINARY_API_SECRET", matches = ".+")
class CloudinaryJobPostingStorageE2eTest {

    private CloudinaryProperties propsFromEnv() {
        CloudinaryProperties p = new CloudinaryProperties();
        p.setCloudName(System.getenv("CAREERTUNER_UPLOADS_CLOUDINARY_CLOUD_NAME"));
        p.setApiKey(System.getenv("CAREERTUNER_UPLOADS_CLOUDINARY_API_KEY"));
        p.setApiSecret(System.getenv("CAREERTUNER_UPLOADS_CLOUDINARY_API_SECRET"));
        return p;
    }

    private static byte[] tinyPng() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);
        img.setRGB(1, 1, 0x00FF00);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void uploadThenReadRoundTripReturnsSameBytes() throws Exception {
        CloudinaryProperties props = propsFromEnv();
        assertThat(props.getCloudName()).as("CLOUD_NAME env").isNotBlank();
        assertThat(props.getApiKey()).as("API_KEY env").isNotBlank();
        assertThat(props.getApiSecret()).as("API_SECRET env").isNotBlank();

        Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", props.getCloudName(),
                "api_key", props.getApiKey(),
                "api_secret", props.getApiSecret(),
                "secure", true));
        CloudinaryJobPostingStorageProvider provider =
                new CloudinaryJobPostingStorageProvider(cloudinary, props);

        byte[] original = tinyPng();
        long caseId = 999_999L;
        String storedName = "e2e-" + UUID.randomUUID() + ".png";

        Written written = provider.write(caseId, storedName, original, "image/png");
        try {
            assertThat(written.reference()).startsWith("cloudinary:");
            assertThat(written.path()).isNull();

            Loaded loaded = provider.read(caseId, written.reference());
            assertThat(loaded.path()).isNull();
            assertThat(loaded.bytes()).as("원본 바이트 정확 복원(OCR 요건)").isEqualTo(original);
        } finally {
            try {
                CloudinaryJobPostingStorageProvider.Ref ref =
                        CloudinaryJobPostingStorageProvider.parseReference(written.reference());
                cloudinary.uploader().destroy(ref.publicId(), ObjectUtils.asMap(
                        "resource_type", ref.resourceType(),
                        "type", ref.type(),
                        "invalidate", true));
            } catch (Exception ignored) {
                // 정리 실패는 테스트 결과에 영향 주지 않음(best-effort)
            }
        }
    }
}
