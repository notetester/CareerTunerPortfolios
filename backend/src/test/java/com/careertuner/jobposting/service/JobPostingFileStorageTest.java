package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;

class JobPostingFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultMaxFileSizeIsTenMegabytes() {
        assertThat(new JobPostingUploadProperties().getMaxFileSizeBytes()).isEqualTo(10L * 1024 * 1024);
    }

    @Test
    void storeAcceptsPdfLargerThanFiveMegabytesUnderTenMegabyteLimit() {
        JobPostingUploadProperties properties = new JobPostingUploadProperties();
        properties.setJobPostingDir(tempDir.toString());
        JobPostingFileStorage storage = new JobPostingFileStorage(properties,
                JobPostingUploadLimitPolicy.fromProperties(properties),
                List.of(new LocalJobPostingStorageProvider(properties)));
        byte[] sevenMb = new byte[7 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "posting.pdf", "application/pdf", sevenMb);

        StoredJobPostingFile stored = storage.store(10L, file, "PDF");

        assertThat(stored.sourceType()).isEqualTo("PDF");
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        assertThat(stored.bytes()).hasSize(sevenMb.length);
    }

    @Test
    void storeRejectsPdfLargerThanTenMegabytes() {
        JobPostingUploadProperties properties = new JobPostingUploadProperties();
        properties.setJobPostingDir(tempDir.toString());
        JobPostingFileStorage storage = new JobPostingFileStorage(properties,
                JobPostingUploadLimitPolicy.fromProperties(properties),
                List.of(new LocalJobPostingStorageProvider(properties)));
        byte[] overLimit = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "posting.pdf", "application/pdf", overLimit);

        assertThatThrownBy(() -> storage.store(10L, file, "PDF"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void loadStoredJobPostingFileReadsLocalReferenceUnderUploadRoot() throws Exception {
        JobPostingUploadProperties properties = new JobPostingUploadProperties();
        properties.setJobPostingDir(tempDir.toString());
        JobPostingFileStorage storage = new JobPostingFileStorage(properties,
                JobPostingUploadLimitPolicy.fromProperties(properties),
                List.of(new LocalJobPostingStorageProvider(properties)));
        Path caseDir = tempDir.resolve("10");
        Files.createDirectories(caseDir);
        Path storedPath = caseDir.resolve("posting.pdf");
        Files.write(storedPath, new byte[]{1, 2, 3});

        StoredJobPostingFile loaded = storage.load(10L, "local:application-postings/10/posting.pdf", "PDF");

        assertThat(loaded.sourceType()).isEqualTo("PDF");
        assertThat(loaded.fileReference()).isEqualTo("local:application-postings/10/posting.pdf");
        assertThat(loaded.originalFilename()).isEqualTo("posting.pdf");
        assertThat(loaded.contentType()).isEqualTo("application/pdf");
        assertThat(loaded.path()).isEqualTo(storedPath.toAbsolutePath().normalize());
        assertThat(loaded.bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void loadStoredJobPostingFileRejectsTraversalOutsideUploadRoot() throws Exception {
        JobPostingUploadProperties properties = new JobPostingUploadProperties();
        properties.setJobPostingDir(tempDir.resolve("uploads").toString());
        JobPostingFileStorage storage = new JobPostingFileStorage(properties,
                JobPostingUploadLimitPolicy.fromProperties(properties),
                List.of(new LocalJobPostingStorageProvider(properties)));
        Path outside = tempDir.resolve("secret.pdf");
        Files.write(outside, new byte[]{9});

        assertThatThrownBy(() -> storage.load(10L, "local:application-postings/10/../../secret.pdf", "PDF"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void loadStoredJobPostingFileRejectsReferenceForDifferentCase() {
        JobPostingUploadProperties properties = new JobPostingUploadProperties();
        properties.setJobPostingDir(tempDir.toString());
        JobPostingFileStorage storage = new JobPostingFileStorage(properties,
                JobPostingUploadLimitPolicy.fromProperties(properties),
                List.of(new LocalJobPostingStorageProvider(properties)));

        assertThatThrownBy(() -> storage.load(10L, "local:application-postings/11/posting.pdf", "PDF"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void loadRejectsReferenceWithUnknownScheme() {
        JobPostingUploadProperties properties = new JobPostingUploadProperties();
        properties.setJobPostingDir(tempDir.toString());
        JobPostingFileStorage storage = new JobPostingFileStorage(properties,
                JobPostingUploadLimitPolicy.fromProperties(properties),
                List.of(new LocalJobPostingStorageProvider(properties)));

        assertThatThrownBy(() -> storage.load(10L, "s3:application-postings/10/posting.pdf", "PDF"))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * production 추출 실패 재현: Cloudinary provider 빈이 등록되지 않은 런타임(예: 로컬 provider 만 배선된
     * 다른 백엔드 런타임)이 {@code cloudinary:} 공고 참조를 load 하면, 이 참조를 라우팅할 provider 가 없어
     * "저장된 공고 파일 참조가 올바르지 않습니다"로 실패한다. store-provider 설정값과 무관하며(load 는 참조 scheme 으로 라우팅),
     * 오직 "런타임에 cloudinary provider 가 있느냐"가 성패를 가른다.
     */
    @Test
    void loadCloudinaryReferenceFailsWhenCloudinaryProviderNotRegistered() {
        JobPostingUploadProperties properties = new JobPostingUploadProperties();
        properties.setJobPostingDir(tempDir.toString());
        // 로컬 provider 만 배선 — cloudinary provider 는 자격증명 미설정 시 빈 미등록(CloudinaryStorageConfig).
        JobPostingFileStorage storage = new JobPostingFileStorage(properties,
                JobPostingUploadLimitPolicy.fromProperties(properties),
                List.of(new LocalJobPostingStorageProvider(properties)));

        String cloudinaryReference = "cloudinary:image/authenticated/pdf/application-postings/10/posting";
        assertThatThrownBy(() -> storage.load(10L, cloudinaryReference, "PDF"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("저장된 공고 파일 참조가 올바르지 않습니다");
    }
}
