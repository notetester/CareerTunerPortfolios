package com.careertuner.auth.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.auth.mapper.AuthMapper;

import lombok.RequiredArgsConstructor;

/** 교환하지 않은 네이티브 OAuth 제공자 정보를 TTL 이후 제한된 배치로 제거한다. */
@Component
@RequiredArgsConstructor
public class NativeAuthHandoffCleanupScheduler {

    private final AuthMapper authMapper;

    @Scheduled(
            initialDelayString = "${careertuner.auth.native-handoff-cleanup.initial-delay-ms:60000}",
            fixedDelayString = "${careertuner.auth.native-handoff-cleanup.fixed-delay-ms:60000}")
    @Transactional
    public void deleteExpiredHandoffs() {
        authMapper.deleteExpiredNativeAuthHandoffs();
    }
}
