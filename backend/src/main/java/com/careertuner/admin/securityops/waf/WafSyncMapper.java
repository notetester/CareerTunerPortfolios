package com.careertuner.admin.securityops.waf;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncTarget;

/** WAF 동기화 큐 드레인 매퍼. */
@Mapper
public interface WafSyncMapper {

    /** 처리 대기(QUEUED/PENDING) WAF 이벤트 + 규칙 조인. */
    List<WafSyncTarget> findPendingWafSyncTargets(@Param("limit") int limit);

    /** 첫 번째 활성 WAF 프로바이더(설정 행). 없으면 null → 스케줄러가 Mock 폴백. */
    WafProviderRow findEnabledWafProvider();

    /** 이벤트 처리 결과 반영. */
    int updateWafEventResult(@Param("id") Long id,
                             @Param("status") String status,
                             @Param("responseJson") String responseJson,
                             @Param("errorMessage") String errorMessage);

    /** 프로바이더 설정 조회 행(config_json 은 스케줄러가 파싱). */
    record WafProviderRow(
            Long id,
            String providerCode,
            String providerType,
            String mode,
            boolean enabled,
            String endpointUrl,
            String configJson) {
    }
}
