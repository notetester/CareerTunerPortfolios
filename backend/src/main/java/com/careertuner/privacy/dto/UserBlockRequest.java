package com.careertuner.privacy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 계정 차단 생성. blockIp=true 면 대상의 최근 접속 IP 도 함께 차단(해시 파생). */
public record UserBlockRequest(
        @NotNull Long targetUserId,
        Boolean blockIp,
        @Size(max = 200) String memo
) {}
