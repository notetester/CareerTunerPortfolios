package com.careertuner.admin.securityops.feed;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.admin.securityops.batch.BlockBatchMapper;
import com.careertuner.admin.securityops.batch.BlockBatchService;
import com.careertuner.admin.securityops.batch.IpBlockBatch;
import com.careertuner.admin.securityops.engine.BlockRuleCacheService;
import com.careertuner.admin.securityops.feed.PolicyFeedModels.ParsedFeedRule;
import com.careertuner.admin.securityops.feed.PolicyFeedModels.PolicyFeedImportRequest;
import com.careertuner.admin.securityops.feed.PolicyFeedModels.PolicyFeedImportResult;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * 정책기관 피드(CSV/JSON) 대량 업로드 import.
 *
 * <p>파싱 → 전용 배치(FEED_*) 생성 → 유효·비중복 규칙만 배치에 귀속 삽입 → 캐시 갱신 → 성공/skip/실패 리포트.
 * TripTogether {@code importPolicyFeed} 를 이식했다.</p>
 */
@Service
@RequiredArgsConstructor
public class PolicyFeedImportService {

    private static final int MESSAGE_CAP = 30;
    private static final int DEFAULT_PRIORITY = 100;

    private final PolicyFeedParser parser;
    private final BlockBatchMapper batchMapper;
    private final BlockBatchService batchService;
    private final BlockRuleCacheService cacheService;
    private final AdminActionLogService actionLogService;

    /** 파일(멀티파트) 업로드 — 원문(CSV/JSON) 문자열로 import. */
    @Transactional
    public PolicyFeedImportResult importFromText(AuthUser authUser, String content, String sourceType,
                                                 String sourceName, String action, String category) {
        AdminAccess.requireAdmin(authUser);
        List<ParsedFeedRule> parsed = parser.parse(content, action);
        return doImport(authUser, parsed, normalizeSourceType(sourceType, content), sourceName, action, category);
    }

    /** JSON API 요청 — rawText 가 있으면 파싱, 없으면 rows 를 직접 검증. */
    @Transactional
    public PolicyFeedImportResult importFromRequest(AuthUser authUser, PolicyFeedImportRequest request) {
        AdminAccess.requireAdmin(authUser);
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 본문이 비어 있습니다.");
        }
        String action = request.action();
        List<ParsedFeedRule> parsed = request.rawText() != null && !request.rawText().isBlank()
                ? parser.parse(request.rawText(), action)
                : parser.parseRows(request.rows(), action);
        String sourceType = request.rawText() != null && !request.rawText().isBlank()
                ? normalizeSourceType(null, request.rawText()) : "POLICY_FEED_API";
        return doImport(authUser, parsed, sourceType, request.sourceName(), action, request.category());
    }

    private PolicyFeedImportResult doImport(AuthUser authUser, List<ParsedFeedRule> parsed, String sourceType,
                                            String sourceName, String action, String category) {
        if (parsed == null || parsed.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "가져올 규칙이 없습니다. 파일/본문을 확인해 주세요.");
        }
        String ruleAction = action == null || action.isBlank() ? "BLOCK" : action.trim().toUpperCase(Locale.ROOT);
        String cat = category == null || category.isBlank() ? "SECURITY" : category.trim().toUpperCase(Locale.ROOT);

        IpBlockBatch batch = IpBlockBatch.builder()
                .batchCode(batchService.generateBatchCode("FEED"))
                .batchName("정책 피드 import" + (sourceName == null ? "" : " · " + sourceName))
                .sourceType(sourceType)
                .sourceName(sourceName)
                .ruleAction(ruleAction)
                .defaultPriority(DEFAULT_PRIORITY)
                .active(true)
                .memo("정책기관 피드 대량 업로드")
                .createdBy(authUser.id())
                .build();
        batchMapper.insertBatch(batch);

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<String> messages = new ArrayList<>();
        for (ParsedFeedRule rule : parsed) {
            if (!rule.valid()) {
                failed++;
                addMessage(messages, "실패: " + rule.value() + " — " + rule.error());
                continue;
            }
            if (batchMapper.countRuleByTypeValue(rule.matchType(), rule.value()) > 0) {
                skipped++;
                addMessage(messages, "중복 skip: " + rule.matchType() + " " + rule.value());
                continue;
            }
            batchMapper.insertBatchRule(rule.matchType(), rule.value(),
                    rule.action() == null ? ruleAction : rule.action(), cat, rule.reason(),
                    DEFAULT_PRIORITY, batch.getId(), authUser.id());
            created++;
        }

        int total = batchMapper.countBatchRules(batch.getId());
        int activeCount = batchMapper.countActiveBatchRules(batch.getId());
        batchMapper.updateBatchCounts(batch.getId(), total, activeCount, authUser.id());
        batchMapper.insertBatchOperation(batch.getId(), "IMPORT", sourceType, parsed.size(), created, authUser.id(),
                "피드 import: 생성 " + created + " / 중복 " + skipped + " / 실패 " + failed);

        actionLogService.record(authUser, null, "SECURITY_POLICY_FEED_IMPORTED", "SECURITY_BLOCK_RULE",
                null, "{\"batchId\":" + batch.getId() + ",\"created\":" + created + ",\"skipped\":" + skipped
                        + ",\"failed\":" + failed + "}", "정책 피드 import");

        cacheService.invalidateAndRefresh();
        return new PolicyFeedImportResult(batch.getId(), batch.getBatchCode(), parsed.size(),
                created, skipped, failed, messages);
    }

    private void addMessage(List<String> messages, String message) {
        if (messages.size() < MESSAGE_CAP) {
            messages.add(message);
        }
    }

    private String normalizeSourceType(String explicit, String content) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim().toUpperCase(Locale.ROOT);
        }
        String t = content == null ? "" : content.trim();
        return t.startsWith("[") || t.startsWith("{") ? "POLICY_FEED_JSON" : "POLICY_FEED_CSV";
    }
}
