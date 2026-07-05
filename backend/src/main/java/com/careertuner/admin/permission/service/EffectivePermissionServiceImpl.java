package com.careertuner.admin.permission.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.careertuner.admin.permission.mapper.EffectivePermissionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 실효 권한 조회 + 60초 인메모리 캐시.
 *
 * <p>캐시는 사용자별 (권한 집합, 만료 시각) 엔트리를 ConcurrentHashMap 에 보관한다.
 * 단일 인스턴스 배포 전제(현행 운영 구성)이며, 권한 변경 API(/api/admin/super/**) 호출 시
 * {@code AdminPermissionInterceptor} 가 evictAll() 을 호출해 최대 60초 지연도 제거한다.</p>
 */
@Service
@RequiredArgsConstructor
public class EffectivePermissionServiceImpl implements EffectivePermissionService {

    private static final long TTL_MILLIS = 60_000L;

    private final EffectivePermissionMapper mapper;

    private final ConcurrentMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Set<String> permissions, long expiresAtMillis) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    @Override
    public Set<String> getEffectivePermissions(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        CacheEntry entry = cache.get(userId);
        if (entry != null && !entry.expired()) {
            return entry.permissions();
        }
        List<String> codes = mapper.findEffectivePermissionCodes(userId);
        Set<String> permissions = Set.copyOf(codes);
        cache.put(userId, new CacheEntry(permissions, System.currentTimeMillis() + TTL_MILLIS));
        return permissions;
    }

    @Override
    public boolean hasAny(Long userId, String... permissionCodes) {
        if (permissionCodes == null || permissionCodes.length == 0) {
            return true;
        }
        Set<String> owned = getEffectivePermissions(userId);
        for (String code : permissionCodes) {
            if (owned.contains(code)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    @Override
    public void evictAll() {
        cache.clear();
    }
}
