package com.careertuner.privacy.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 개인 IP 차단 1건 — 원본 IP 는 저장하지 않고 해시만 둔다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIpBlock {

    private Long id;
    private Long userId;
    private String ipHash;
    private Long sourceUserId;
    private String label;
    private LocalDateTime createdAt;

    // JOIN 표시용
    private String sourceUserName;
    private String sourceUserStatus;
    /** 파생 원본 계정 차단의 masked_label — non-null 이면 표시 이름을 이 라벨로 대체(익명성 유지). */
    private String sourceMaskedLabel;
}
