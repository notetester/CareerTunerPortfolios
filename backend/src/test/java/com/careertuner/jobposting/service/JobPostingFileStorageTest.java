package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

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
        JobPostingFileStorage storage = new JobPostingFileStorage(properties, JobPostingUploadLimitPolicy.fromProperties(properties));
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
        JobPostingFileStorage storage = new JobPostingFileStorage(properties, JobPostingUploadLimitPolicy.fromProperties(properties));
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
        JobPostingFileStorage storage = new JobPostingFileStorage(properties, JobPostingUploadLimitPolicy.fromProperties(properties));
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
        JobPostingFileStorage storage = new JobPostingFileStorage(properties, JobPostingUploadLimitPolicy.fromProperties(properties));
        Path outside = tempDir.resolve("secret.pdf");
        Files.write(outside, new byte[]{9});

        assertThatThrownBy(() -> storage.load(10L, "local:application-postings/10/../../secret.pdf", "PDF"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void loadStoredJobPostingFileRejectsReferenceForDifferentCase() {
        JobPostingUploadProperties properties = new JobPostingUploadProperties();
        properties.setJobPostingDir(tempDir.toString());
        JobPostingFileStorage storage = new JobPostingFileStorage(properties, JobPostingUploadLimitPolicy.fromProperties(properties));

        assertThatThrownBy(() -> storage.load(10L, "local:application-postings/11/posting.pdf", "PDF"))
                .isInstanceOf(BusinessException.class);
    }
}
