package com.careertuner.nickname.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 표시명 벌크 해석의 계정명(users.name) 폴백 행 — (userId, name). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountNameRow {

    private Long userId;
    private String name;
}
