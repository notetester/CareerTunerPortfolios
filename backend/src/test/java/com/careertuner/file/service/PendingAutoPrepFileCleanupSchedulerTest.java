package com.careertuner.file.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class PendingAutoPrepFileCleanupSchedulerTest {

    @Test
    void schedulerUsesTwentyFourHourTtlAndBoundedBatch() {
        FileService fileService = mock(FileService.class);
        PendingAutoPrepFileCleanupScheduler scheduler =
                new PendingAutoPrepFileCleanupScheduler(fileService);

        scheduler.cleanup();

        verify(fileService).cleanupStalePendingAutoPrepAttachments(24, 100);
    }
}
