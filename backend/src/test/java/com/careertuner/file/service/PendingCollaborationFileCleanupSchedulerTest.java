package com.careertuner.file.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class PendingCollaborationFileCleanupSchedulerTest {

    @Test
    void schedulerUsesTwentyFourHourTtlAndBoundedBatch() {
        FileService fileService = mock(FileService.class);
        PendingCollaborationFileCleanupScheduler scheduler =
                new PendingCollaborationFileCleanupScheduler(fileService);

        scheduler.cleanup();

        verify(fileService).cleanupStalePendingCollaborationAttachments(24, 100);
    }
}
