package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;

class JobPostingFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void loadStoredJobPostingFileReadsLocalReferenceUnderUploadRoot() throws Exception {
        JobPostingUploadProperties properties = new JobPostingUploadProperties();
        properties.setJobPostingDir(tempDir.toString());
        JobPostingFileStorage storage = new JobPostingFileStorage(properties);
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
        JobPostingFileStorage storage = new JobPostingFileStorage(properties);
        Path outside = tempDir.resolve("secret.pdf");
        Files.write(outside, new byte[]{9});

        assertThatThrownBy(() -> storage.load(10L, "local:application-postings/10/../../secret.pdf", "PDF"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void loadStoredJobPostingFileRejectsReferenceForDifferentCase() {
        JobPostingUploadProperties properties = new JobPostingUploadProperties();
        properties.setJobPostingDir(tempDir.toString());
        JobPostingFileStorage storage = new JobPostingFileStorage(properties);

        assertThatThrownBy(() -> storage.load(10L, "local:application-postings/11/posting.pdf", "PDF"))
                .isInstanceOf(BusinessException.class);
    }
}
