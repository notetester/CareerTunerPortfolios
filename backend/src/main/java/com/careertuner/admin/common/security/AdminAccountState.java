package com.careertuner.admin.common.security;

/** 관리자 계정 변경 트랜잭션에서 잠근 최소 계정 상태. */
public record AdminAccountState(Long id, String role, String status) {
}
