package com.careertuner.community.moderation.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationSetting {

    private int id;
    private Strictness strictness;
    private double hideThreshold;
    private int sanctionThreshold;   // 누적 숨김 글 수 임계(이 이상이면 사용자 자동 차단)
    private int blockDays;           // 자동 차단 유지 일수

    // ── 작성 rate-limit(도배 방지) + 신고 누적 블러 임계 (콘솔 편집) ──
    private int reportBlurThreshold;         // 신고 누적 자동 블러 임계
    private int postRateWindowSeconds;       // 게시글 rate-limit 윈도(초)
    private int postRateMax;                 // 윈도 내 허용 게시글 수(0=비활성)
    private int commentRateWindowSeconds;    // 댓글 rate-limit 윈도(초)
    private int commentRateMax;              // 윈도 내 허용 댓글 수(0=비활성)
    private int inquiryRateWindowSeconds;    // 문의 rate-limit 윈도(초) — 정책값(집행은 support 도메인)
    private int inquiryRateMax;              // 윈도 내 허용 문의 수(0=비활성)

    private LocalDateTime updatedAt;
}
