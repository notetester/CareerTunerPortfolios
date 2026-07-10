package com.careertuner.applicationcase.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 지원 건 상태 변경 이력(관리자 상태 변경 시 기록). 기록만 되고 어디서도 읽히지 않던 것을
 * 관리자 상세 타임라인으로 노출한다(회색지대 QA — 고아 쓰기 해소).
 */
@Data
public class ApplicationCaseStatusHistory {

    private Long id;
    private Long applicationCaseId;
    private String previousStatus;
    private String newStatus;
    private String memo;
    private String changedByName;   // users.name (탈퇴 등으로 없으면 null)
    private LocalDateTime createdAt;
}
