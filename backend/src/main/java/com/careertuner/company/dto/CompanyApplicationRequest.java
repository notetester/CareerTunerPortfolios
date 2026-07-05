package com.careertuner.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 기업 계정 전환 신청 요청. */
public record CompanyApplicationRequest(
        @NotBlank(message = "기업명을 입력해 주세요.") @Size(max = 100) String companyName,
        @Size(max = 50) String businessNumber,
        @NotBlank(message = "담당자 연락처를 입력해 주세요.") @Size(max = 100) String contact,
        @Size(max = 1000) String description
) {
}
