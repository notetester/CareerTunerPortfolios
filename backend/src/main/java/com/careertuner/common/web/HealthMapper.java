package com.careertuner.common.web;

import org.apache.ibatis.annotations.Mapper;

/** readiness 헬스체크용 DB 왕복 핑(SELECT 1). 연결/풀 장애 시 예외를 던진다. */
@Mapper
public interface HealthMapper {

    /** DB 연결이 살아 있으면 1을 반환. 연결 실패 시 예외. */
    int ping();
}
