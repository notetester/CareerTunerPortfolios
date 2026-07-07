package com.careertuner.admin.securityops.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 애플리케이션 레벨 차단 규칙 런타임 캐시.
 *
 * <p>요청마다 DB 를 조회하지 않고, 규칙 변경/관리자 동기화 시점에 <b>DB → 메모리 → 파일</b> 캐시로 갱신한다.
 * 파일 캐시는 재기동 직후 DB 조회 전에도 마지막 스냅샷으로 즉시 방어를 시작하기 위한 보조 수단이다.
 * TripTogether {@code BlockRuleCacheService} 를 이식했다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockRuleCacheService {

    private final BlockEngineMapper blockEngineMapper;
    private final ObjectMapper objectMapper;

    @Value("${security.block.cache.file:./data/block-rule-cache.json}")
    private String cacheFilePath;

    private final AtomicReference<BlockRuleCacheSnapshot> current = new AtomicReference<>();

    /** 현재 스냅샷. 최초 접근이면 파일→DB 순으로 로드한다(요청 경로에서 호출되므로 빠르게). */
    public BlockRuleCacheSnapshot getSnapshot() {
        BlockRuleCacheSnapshot snapshot = current.get();
        return snapshot != null ? snapshot : loadFromFileOrDatabase();
    }

    /** DB 에서 최신 유효 규칙을 읽어 메모리·파일 캐시를 갱신한다. */
    public synchronized BlockRuleCacheSnapshot refreshFromDatabase() {
        List<ActiveBlockRule> rules = safeList(blockEngineMapper.findActiveBlockRulesForCache());
        BlockRuleCacheSnapshot snapshot = BlockRuleCacheSnapshot.of("DB", rules);
        current.set(snapshot);
        writeToFile(snapshot);
        log.info("[BlockCache] DB 동기화 완료: rules={}, file={}", rules.size(), cacheFilePath);
        return snapshot;
    }

    /** 재기동 시 파일 캐시가 있으면 우선 로드, 없으면 DB 조회. */
    public synchronized BlockRuleCacheSnapshot loadFromFileOrDatabase() {
        BlockRuleCacheSnapshot fileSnapshot = readFromFile();
        if (fileSnapshot != null) {
            current.set(fileSnapshot);
            log.info("[BlockCache] 파일 캐시 로드: rules={}, file={}", fileSnapshot.getRules().size(), cacheFilePath);
            return fileSnapshot;
        }
        return refreshFromDatabase();
    }

    /** 규칙 변경 후 캐시 무효화 + 즉시 재적재. 규칙 CRUD·토글·배치 변경 시 호출. */
    public BlockRuleCacheSnapshot invalidateAndRefresh() {
        try {
            blockEngineMapper.recomputeEffectiveActive();
        } catch (Exception e) {
            log.warn("[BlockCache] 유효상태 재계산 실패(무시하고 캐시 갱신 진행): {}", e.getMessage());
        }
        current.set(BlockRuleCacheSnapshot.of("INVALIDATED", Collections.emptyList()));
        return refreshFromDatabase();
    }

    private void writeToFile(BlockRuleCacheSnapshot snapshot) {
        try {
            Path path = Path.of(cacheFilePath).toAbsolutePath().normalize();
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(path.toFile(), snapshot);
        } catch (Exception e) {
            log.warn("[BlockCache] 파일 캐시 저장 실패: {}", e.getMessage());
        }
    }

    private BlockRuleCacheSnapshot readFromFile() {
        try {
            Path path = Path.of(cacheFilePath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                return null;
            }
            BlockRuleCacheSnapshot snapshot = objectMapper.readValue(path.toFile(), BlockRuleCacheSnapshot.class);
            if (snapshot != null && snapshot.getSource() == null) {
                snapshot.setSource("FILE");
            }
            return snapshot;
        } catch (Exception e) {
            log.warn("[BlockCache] 파일 캐시 로드 실패: {}", e.getMessage());
            return null;
        }
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    @Data
    @NoArgsConstructor
    public static class BlockRuleCacheSnapshot {
        private String source;
        private LocalDateTime loadedAt;
        private List<ActiveBlockRule> rules = new ArrayList<>();

        public static BlockRuleCacheSnapshot of(String source, List<ActiveBlockRule> rules) {
            BlockRuleCacheSnapshot snapshot = new BlockRuleCacheSnapshot();
            snapshot.setSource(source);
            snapshot.setLoadedAt(LocalDateTime.now());
            snapshot.setRules(rules == null ? new ArrayList<>() : new ArrayList<>(rules));
            return snapshot;
        }

        public int size() {
            return rules == null ? 0 : rules.size();
        }
    }
}
