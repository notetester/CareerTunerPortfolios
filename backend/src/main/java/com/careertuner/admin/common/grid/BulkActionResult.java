package com.careertuner.admin.common.grid;

/** 일괄 작업 결과 요약. requested=정규화 후 대상 수, updated=변경 수, skipped=건너뜀(미존재/동일 상태 등). */
public record BulkActionResult(int requested, int updated, int skipped) {
}
