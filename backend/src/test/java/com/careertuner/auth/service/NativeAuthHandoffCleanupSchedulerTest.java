package com.careertuner.auth.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.careertuner.auth.mapper.AuthMapper;

class NativeAuthHandoffCleanupSchedulerTest {

    @Test
    void deleteExpiredHandoffsRemovesRowsExpiredAtCurrentRun() {
        AuthMapper authMapper = mock(AuthMapper.class);
        NativeAuthHandoffCleanupScheduler scheduler = new NativeAuthHandoffCleanupScheduler(authMapper);

        scheduler.deleteExpiredHandoffs();

        verify(authMapper).deleteExpiredNativeAuthHandoffs();
    }
}
