package com.careertuner.applicationcase.service;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.domain.ApplicationCaseInitialRun;
import com.careertuner.applicationcase.mapper.ApplicationCaseInitialRunMapper;

class ApplicationCaseInitialRunReaperTest {

    @Test
    void reapsStaleRunningProfilesWithTheirObservedToken() {
        ApplicationCaseInitialRunMapper mapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseInitialRunReaper reaper = new ApplicationCaseInitialRunReaper(mapper);
        when(mapper.findStaleRunning(anyLong(), anyInt())).thenReturn(List.of(
                staleRun(10L, "tok-a"),
                staleRun(11L, "tok-b")));
        when(mapper.markFailed(anyLong(), anyString(), anyString())).thenReturn(1);

        reaper.reapStaleRuns();

        // 각 프로필이 관측된 execution_token 으로 FAILED 회수되는지(fencing) 잠근다.
        verify(mapper).markFailed(eq(10L), eq("tok-a"), anyString());
        verify(mapper).markFailed(eq(11L), eq("tok-b"), anyString());
    }

    @Test
    void doesNothingWhenNoStaleRuns() {
        ApplicationCaseInitialRunMapper mapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseInitialRunReaper reaper = new ApplicationCaseInitialRunReaper(mapper);
        when(mapper.findStaleRunning(anyLong(), anyInt())).thenReturn(List.of());

        reaper.reapStaleRuns();

        verify(mapper, never()).markFailed(anyLong(), anyString(), anyString());
    }

    @Test
    void continuesAcrossFencedNoOpAndPerRowFailure() {
        ApplicationCaseInitialRunMapper mapper = mock(ApplicationCaseInitialRunMapper.class);
        ApplicationCaseInitialRunReaper reaper = new ApplicationCaseInitialRunReaper(mapper);
        when(mapper.findStaleRunning(anyLong(), anyInt())).thenReturn(List.of(
                staleRun(10L, "tok-a"),
                staleRun(11L, "tok-b"),
                staleRun(12L, "tok-c")));
        // 10L: 그 사이 실제 실행이 끝나 토큰 불일치(0행) / 11L: DB 예외 / 12L: 정상 회수 — 셋 다 시도돼야 한다.
        when(mapper.markFailed(eq(10L), anyString(), anyString())).thenReturn(0);
        when(mapper.markFailed(eq(11L), anyString(), anyString())).thenThrow(new RuntimeException("db down"));
        when(mapper.markFailed(eq(12L), anyString(), anyString())).thenReturn(1);

        reaper.reapStaleRuns();

        verify(mapper).markFailed(eq(10L), eq("tok-a"), anyString());
        verify(mapper).markFailed(eq(11L), eq("tok-b"), anyString());
        verify(mapper).markFailed(eq(12L), eq("tok-c"), anyString());
    }

    private static ApplicationCaseInitialRun staleRun(Long applicationCaseId, String token) {
        return ApplicationCaseInitialRun.builder()
                .applicationCaseId(applicationCaseId)
                .state("RUNNING")
                .executionToken(token)
                .build();
    }
}
