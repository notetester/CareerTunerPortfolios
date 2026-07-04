package com.careertuner.privacy.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 벌크 관계 판정용 (사용자 id → role). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleRow {

    private Long id;
    private String role;
}
