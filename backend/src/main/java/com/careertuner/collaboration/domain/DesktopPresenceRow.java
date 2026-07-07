package com.careertuner.collaboration.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** user_desktop_presence 조회 행 — LOCAL 파일 공유 게이트용 데스크톱 heartbeat. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesktopPresenceRow {

    private Long userId;
    private LocalDateTime lastSeenAt;
}
