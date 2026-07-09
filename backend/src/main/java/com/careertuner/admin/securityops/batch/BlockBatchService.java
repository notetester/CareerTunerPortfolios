package com.careertuner.admin.securityops.batch;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.securityops.engine.BlockRuleCacheService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * IP 정책 배치 관리 — 생성·조회·토글(cascade 전략).
 *
 * <p>차단 규칙을 배치로 묶어 그룹 단위로 켜고 끈다. 배치 OFF 시 하위 규칙은 캐시 로드에서 제외되어
 * 즉시 미적용된다(캐시 쿼리가 {@code batch active} 를 함께 검사). cascade 전략으로 규칙 자체의
 * 활성 플래그까지 동기화할 수 있다. TripTogether {@code toggleIpBlockBatch} 를 이식했다.</p>
 */
@Service
@RequiredArgsConstructor
public class BlockBatchService {

    static final Set<String> CASCADE_STRATEGIES =
            Set.of("BATCH_ONLY", "CASCADE_ACTIVE_RULES", "RESTORE_BATCH_CONTROL", "FORCE_ENABLE_ALL");
    private static final Set<String> RULE_ACTIONS = Set.of("BLOCK", "ALLOWLIST", "REVIEW", "CHALLENGE");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final BlockBatchMapper mapper;
    private final BlockRuleCacheService cacheService;

    @Transactional(readOnly = true)
    public List<IpBlockBatchRow> batches(AuthUser authUser, String keyword, Boolean active, int limit) {
        AdminAccess.requireAdmin(authUser);
        int lim = limit <= 0 || limit > 500 ? 200 : limit;
        return mapper.findBatches(blankToNull(keyword), active, lim);
    }

    @Transactional(readOnly = true)
    public IpBlockBatchRow batch(AuthUser authUser, Long id) {
        AdminAccess.requireAdmin(authUser);
        return requireBatch(id);
    }

    @Transactional
    public IpBlockBatchRow createBatch(AuthUser authUser, IpBlockBatchRequest request) {
        AdminAccess.requireAdmin(authUser);
        IpBlockBatch batch = IpBlockBatch.builder()
                .batchCode(generateBatchCode("MANUAL"))
                .batchName(request.batchName().trim())
                .sourceType(normalize(request.sourceType(), "MANUAL"))
                .sourceName(blankToNull(request.sourceName()))
                .ruleAction(request.ruleAction() != null && RULE_ACTIONS.contains(request.ruleAction().toUpperCase(Locale.ROOT))
                        ? request.ruleAction().toUpperCase(Locale.ROOT) : "BLOCK")
                .defaultPriority(request.defaultPriority() == null ? 100 : request.defaultPriority())
                .active(true)
                .memo(blankToNull(request.memo()))
                .createdBy(authUser.id())
                .build();
        mapper.insertBatch(batch);
        mapper.insertBatchOperation(batch.getId(), "CREATE", null, 0, 0, authUser.id(), "배치 생성");
        return requireBatch(batch.getId());
    }

    /** 배치 ON/OFF + cascade 전략 적용. */
    @Transactional
    public IpBlockBatchRow toggleBatch(AuthUser authUser, Long id, boolean active, String cascadeStrategy) {
        AdminAccess.requireAdmin(authUser);
        IpBlockBatchRow before = requireBatch(id);
        String strategy = cascadeStrategy == null || !CASCADE_STRATEGIES.contains(cascadeStrategy.toUpperCase(Locale.ROOT))
                ? "BATCH_ONLY" : cascadeStrategy.toUpperCase(Locale.ROOT);

        mapper.setBatchActive(id, active, authUser.id());
        int affected = 0;
        if (!active && "CASCADE_ACTIVE_RULES".equals(strategy)) {
            affected = mapper.setRulesActiveByBatch(id, false);
        } else if (active && "FORCE_ENABLE_ALL".equals(strategy)) {
            affected = mapper.setRulesActiveByBatch(id, true);
        }
        // RESTORE_BATCH_CONTROL / BATCH_ONLY 는 규칙 활성 플래그를 건드리지 않는다(배치 플래그만).

        int total = mapper.countBatchRules(id);
        int activeCount = mapper.countActiveBatchRules(id);
        mapper.updateBatchCounts(id, total, activeCount, authUser.id());
        mapper.insertBatchOperation(id, active ? "TOGGLE_ON" : "TOGGLE_OFF", strategy, total, affected, authUser.id(),
                "배치 " + (active ? "활성화" : "비활성화") + " (" + strategy + "), 영향 규칙 " + affected + "건");

        cacheService.invalidateAndRefresh();
        return requireBatch(id);
    }

    IpBlockBatchRow requireBatch(Long id) {
        IpBlockBatchRow row = mapper.findBatchById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "IP 정책 배치를 찾을 수 없습니다.");
        }
        return row;
    }

    public String generateBatchCode(String prefix) {
        return prefix + "_" + LocalDateTime.now().format(STAMP);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
