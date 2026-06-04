package com.careertuner.common.web;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프런트엔드/모니터링에서 백엔드 가용성을 확인하는 헬스 체크.
 * 프런트엔드 개발 프록시 기준 GET /api/health 로 호출된다.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("service", "CareerTuner");
        body.put("status", "UP");
        body.put("time", OffsetDateTime.now().toString());
        return ApiResponse.ok(body);
    }
}
