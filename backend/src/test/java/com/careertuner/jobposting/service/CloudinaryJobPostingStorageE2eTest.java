package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.careertuner.applicationcase.service.OpenAiResponsesClient;
import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;
import com.careertuner.jobposting.service.JobPostingStorageProvider.Loaded;
import com.careertuner.jobposting.service.JobPostingStorageProvider.Written;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

/**
 * Cloudinary 실 네트워크 e2e — 세 env(CLOUD_NAME/API_KEY/API_SECRET)가 <b>모두</b> 있을 때만 실행(하나라도 없으면 <b>skip</b>).
 * {@code @EnabledIfEnvironmentVariable} 를 반복(@Repeatable)해 AND 게이팅한다 → CI/일반 빌드에선 skip, 부분 설정도 skip.
 * (공백-only 값은 아래 assertion 이 잡는다.)
 *
 * <p>실행(세션 env 세팅 후, 값은 로그/커밋에 넣지 않음 — System.getenv 로만 읽음):
 * <pre>
 *   $env:CAREERTUNER_UPLOADS_CLOUDINARY_CLOUD_NAME="..."
 *   $env:CAREERTUNER_UPLOADS_CLOUDINARY_API_KEY="..."
 *   $env:CAREERTUNER_UPLOADS_CLOUDINARY_API_SECRET="..."
 *   .\gradlew.bat test --tests "*CloudinaryJobPostingStorageE2eTest" --rerun-tasks --no-daemon
 * </pre>
 *
 * <p>검증: write(실 업로드) → read(서명 authenticated URL 다운로드) → 원본 바이트 정확 복원(OCR 요건) +
 * 텍스트 PDF 업로드→read→PDFBox 추출까지. 업로드한 테스트 asset 은 finally 에서 best-effort 삭제(잔존 방지).
 */
@EnabledIfEnvironmentVariable(named = "CAREERTUNER_UPLOADS_CLOUDINARY_CLOUD_NAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CAREERTUNER_UPLOADS_CLOUDINARY_API_KEY", matches = ".+")
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

    /** 임베디드 텍스트가 든 유효한 1페이지 PDF — PDFBox 로 추출 가능(OCR/AI 불필요, ASCII marker 만 사용). */
    private static byte[] textPdf(String marker) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(marker);
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
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
            destroyQuietly(cloudinary, written.reference());
        }
    }

    /**
     * afiogw64 실서버로 "Cloudinary 저장 → signed read → 추출" 전 경로 실증.
     * 텍스트 PDF 는 PDFBox 만으로 추출되므로 AI/워커 키 없이도 "현재 테스트 런타임 설정에선 cloudinary 공고 추출이 정상"임을 증명한다
     * (production 추출 실패가 Cloudinary 저장·읽기·텍스트 PDF 추출 경로 자체보다는, 다른 런타임의 provider 설정 불일치/공유큐 선점 쪽이라는 가설을 뒷받침 — 운영 전체 설정 문제를 배제하는 건 아님).
     */
    @Test
    void uploadTextPdfThenExtractReturnsText() throws Exception {
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

        String marker = "CareerTunerOCRe2e" + UUID.randomUUID().toString().replace("-", "");
        byte[] pdf = textPdf(marker);
        long caseId = 999_998L;
        String storedName = "e2e-extract-" + UUID.randomUUID() + ".pdf";

        Written written = provider.write(caseId, storedName, pdf, "application/pdf");
        try {
            Loaded loaded = provider.read(caseId, written.reference());
            StoredJobPostingFile stored = new StoredJobPostingFile(
                    "PDF", written.reference(), storedName, "application/pdf", loaded.path(), loaded.bytes());

            // 텍스트 PDF → extractFile 은 PDFBox 경로만 탄다(OpenAI/워커 미호출).
            JobPostingTextExtractor extractor = new JobPostingTextExtractor(mock(OpenAiResponsesClient.class));
            ExtractedPosting extracted = extractor.extractFile(stored);

            assertThat(extracted.extractedText())
                    .as("Cloudinary 저장 PDF에서 추출한 텍스트에 marker 포함")
                    .contains(marker);
        } finally {
            destroyQuietly(cloudinary, written.reference());
        }
    }

    private static void destroyQuietly(Cloudinary cloudinary, String reference) {
        try {
            CloudinaryJobPostingStorageProvider.Ref ref =
                    CloudinaryJobPostingStorageProvider.parseReference(reference);
            cloudinary.uploader().destroy(ref.publicId(), ObjectUtils.asMap(
                    "resource_type", ref.resourceType(),
                    "type", ref.type(),
                    "invalidate", true));
        } catch (Exception ignored) {
            // 정리 실패는 테스트 결과에 영향 주지 않음(best-effort)
        }
    }
}
