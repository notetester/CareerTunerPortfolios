package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

/**
 * 실 통합 검증 — 앱을 <b>cloudinary 설정으로 부팅</b>했을 때, 공고 파일 업로드가 실제로 Cloudinary 로
 * 라우팅되는지. provider 단독 e2e 와 달리 <b>Spring 이 배선한 facade</b>({@link JobPostingFileStorage})로
 * store/load 하므로 "앱이 켜진 상태에서 올리면 Cloudinary 에 저장"을 검증한다.
 *
 * <p>env 없으면 <b>skip</b>(클래스 전체 disable → 컨텍스트 부팅도 안 함). 실행(세션 env):
 * <pre>
 *   $env:JOB_POSTING_STORAGE_PROVIDER="cloudinary"
 *   $env:CAREERTUNER_UPLOADS_CLOUDINARY_CLOUD_NAME / _API_KEY / _API_SECRET = ...
 *   .\gradlew.bat test --tests "*CloudinaryJobPostingUploadIntegrationTest" --rerun-tasks --no-daemon
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CAREERTUNER_UPLOADS_CLOUDINARY_API_SECRET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JOB_POSTING_STORAGE_PROVIDER", matches = "(?i)cloudinary")
class CloudinaryJobPostingUploadIntegrationTest {

    @Autowired
    private JobPostingFileStorage fileStorage;

    @Autowired
    private Cloudinary cloudinary;

    private static byte[] tinyPng() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0x3366FF);
        img.setRGB(1, 1, 0x33AA55);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void jobPostingUploadIsStoredInCloudinaryAndReadBack() throws Exception {
        byte[] png = tinyPng();
        MockMultipartFile file = new MockMultipartFile(
                "file", "posting-" + UUID.randomUUID() + ".png", "image/png", png);
        long caseId = 999_998L;

        StoredJobPostingFile stored = fileStorage.store(caseId, file, "IMAGE");
        try {
            // 업로드가 Cloudinary 로 라우팅됐는가: reference 가 cloudinary: 로 시작 + 로컬 경로 없음.
            assertThat(stored.fileReference()).startsWith("cloudinary:");
            assertThat(stored.path()).isNull();
            System.out.println("[integration] 업로드 저장 reference = " + stored.fileReference());

            // 다시 읽어 원본 바이트가 복원되는가(= 실제로 Cloudinary 에 있고 조회 가능).
            StoredJobPostingFile loaded = fileStorage.load(caseId, stored.fileReference(), "IMAGE");
            assertThat(loaded.bytes()).isEqualTo(png);
        } finally {
            try {
                CloudinaryJobPostingStorageProvider.Ref ref =
                        CloudinaryJobPostingStorageProvider.parseReference(stored.fileReference());
                cloudinary.uploader().destroy(ref.publicId(), ObjectUtils.asMap(
                        "resource_type", ref.resourceType(), "type", ref.type(), "invalidate", true));
            } catch (Exception ignored) {
                // 정리 실패는 검증 결과에 영향 없음(best-effort)
            }
        }
    }
}
