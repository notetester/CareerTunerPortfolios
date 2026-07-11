package com.careertuner.file.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class PendingInterviewMediaCleanupSchedulerTest {

    @Test
    void schedulerUsesTwentyFourHourTtlAndBoundedBatch() {
        FileService fileService = mock(FileService.class);
        PendingInterviewMediaCleanupScheduler scheduler =
                new PendingInterviewMediaCleanupScheduler(fileService);

        scheduler.cleanup();

        verify(fileService).cleanupStalePendingInterviewMedia(24, 100);
    }
}
