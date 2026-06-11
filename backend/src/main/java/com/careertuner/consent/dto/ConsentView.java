package com.careertuner.consent.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * user_consent 조회 결과 DTO.
 *
 * MyBatis가 SELECT 결과를 기본 생성자 + setter로 채우기 쉽도록 class 형태로 둔다.
 * record도 생성자 매핑으로 쓸 수 있지만, 컬럼이 늘거나 alias가 바뀔 때 초보자가 디버깅하기 어렵다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsentView {
    private Long id;
    private Long userId;
    private String userEmail;
    private String consentType;
    private boolean agreed;
    private LocalDateTime agreedAt;
    private LocalDateTime revokedAt;
    private String source;
    private LocalDateTime createdAt;
}
