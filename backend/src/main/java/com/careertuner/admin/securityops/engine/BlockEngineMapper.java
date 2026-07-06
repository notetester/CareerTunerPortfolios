package com.careertuner.admin.securityops.engine;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

/** 차단 집행 엔진 전용 매퍼 — 런타임 캐시 로드·차단 로그·유효상태 동기화. */
@Mapper
public interface BlockEngineMapper {

    /**
     * 캐시에 실을 활성·유효 IP성 규칙(우선순위 DESC). 규칙 활성 + 미만료 + (배치 없음 또는 배치 활성)을
     * 직접 필터해 항상 최신 유효 규칙만 반환한다(USER/EMAIL 류는 요청 차단 대상이 아니라 제외).
     */
    List<ActiveBlockRule> findActiveBlockRulesForCache();

    /** 차단된 요청 로그 1건 저장(best-effort). */
    void insertBlockAccessLog(BlockAccessLogEntry entry);

    /**
     * 모든 규칙의 is_effective_active 를 활성·만료·배치활성 종합값으로 재계산(관리자 화면 표시용 동기화).
     * @return 갱신된 행 수
     */
    int recomputeEffectiveActive();
}
