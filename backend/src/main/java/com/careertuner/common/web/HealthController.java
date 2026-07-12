package com.careertuner.common.web;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 헬스 체크 — liveness 와 readiness 를 분리한다.
 *
 * <ul>
 *   <li>{@code GET /api/health} (liveness): JVM 프로세스가 요청을 받는가. 의존성 무관, 항상 UP.
 *       프런트엔드 개발 프록시/모니터링의 가벼운 가용성 핑.</li>
 *   <li>{@code GET /api/health/ready} (readiness): DB 연결까지 살아 핵심 요청을 처리할 수 있는가.
 *       DB 왕복(SELECT 1) 실패 시 503 을 반환한다. 배포 검증은 이 엔드포인트를 써야
 *       "DB 끊긴 백엔드가 healthy 로 보고"되는 것을 잡는다.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final HealthMapper healthMapper;

    /** liveness — 프로세스가 살아 요청을 받으면 UP. 의존성 확인 안 함(의도적). */
    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("service", "CareerTuner");
        body.put("status", "UP");
        body.put("time", OffsetDateTime.now().toString());
        return ApiResponse.ok(body);
    }

    /** readiness — DB 왕복까지 성공해야 UP(200), 실패 시 DOWN(503). 배포 게이트가 사용. */
    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<Map<String, String>>> ready() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("service", "CareerTuner");
        body.put("time", OffsetDateTime.now().toString());
        try {
            healthMapper.ping();
            body.put("status", "UP");
            body.put("db", "UP");
            return ResponseEntity.ok(ApiResponse.ok(body));
        } catch (Exception e) {
            log.error("readiness 실패 — DB 연결 이상: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("SERVICE_UNAVAILABLE", "DB 연결 이상"));
        }
    }
}
