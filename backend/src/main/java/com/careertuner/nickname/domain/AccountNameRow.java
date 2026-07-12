package com.careertuner.nickname.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 표시명 벌크 해석의 계정명(users.name) 폴백 행. 탈퇴 계정은 공개 id도 숨긴다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountNameRow {

    private Long userId;
    private String name;
    private String status;
}
