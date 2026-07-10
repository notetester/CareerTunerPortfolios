package com.careertuner.file.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storedFileCanBeDeletedAndRestoredForTransactionCompensation() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMediaDir(tempDir.toString());
        FileStorageService storage = new FileStorageService(properties);
        FileStorageService.Stored stored = storage.store(7L, new MockMultipartFile(
                "file", "resume.txt", "text/plain", "profile".getBytes(StandardCharsets.UTF_8)));

        byte[] snapshot = storage.snapshotIfExists(stored.storageKey());
        storage.delete(stored.storageKey());

        assertThat(storage.snapshotIfExists(stored.storageKey())).isNull();
        storage.restore(stored.storageKey(), snapshot);
        assertThat(storage.read(stored.storageKey())).isEqualTo("profile".getBytes(StandardCharsets.UTF_8));
    }
}
