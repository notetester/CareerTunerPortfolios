-- AI 런타임 설정을 기존 관리자 런타임 설정 콘솔에 노출하기 위한 패치.
-- (1) 변경 이력 테이블에 reason(변경 사유) 감사 컬럼 추가.
-- (2) AI 관련 설정 키를 시드(setting_value는 NULL 유지 → 관리자가 덮어쓰기 전까지 fallback_value가 유효값).
-- 재실행 안전(idempotent): 컬럼 추가는 information_schema 가드, 시드는 ON DUPLICATE KEY UPDATE.

-- ── (1) reason 감사 컬럼 (change_type 뒤). MySQL 8 구버전 호환 위해 IF NOT EXISTS 대신 가드 패턴 ──
SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'application_runtime_setting_history'
      AND column_name = 'reason'
);
SET @sql := IF(
    @exist = 0,
    'ALTER TABLE application_runtime_setting_history ADD COLUMN reason VARCHAR(500) NULL AFTER change_type',
    'SELECT 1'
);
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- ── (2) AI 런타임 설정 시드 (setting_value는 NULL 유지, 유효값은 fallback_value) ──
-- AI_GPU_SERVER: 공유 4090 Ollama 호스트 env. 저장은 즉시, 실제 적용은 Ollama 재시작(ops) 필요.
INSERT INTO application_runtime_setting
    (setting_key, setting_group, display_name, fallback_value, value_type, editable, active, description)
VALUES
    ('ai.gpu.num-parallel', 'AI_GPU_SERVER', 'Ollama NUM_PARALLEL', '2', 'NUMBER', 1, 1,
     '공유 4090 Ollama 서버의 모델당 병렬 슬롯. 부하 테스트 확정값 2. ⚠ 이 값은 4090 호스트 env 라 저장은 즉시 되지만 실제 적용은 Ollama 재시작(ops)이 필요.'),
    ('ai.gpu.max-loaded-models', 'AI_GPU_SERVER', 'Ollama MAX_LOADED_MODELS', '8', 'NUMBER', 1, 1,
     '동시 상주 모델 수 상한. 확정값 8(사실상 무제한, VRAM이 실질 상한). ⚠ 4090 env — 적용은 Ollama 재시작 필요.'),
    ('ai.gpu-gate.enabled', 'AI_GPU_GATE', 'GPU permit 게이트 ON/OFF', 'false', 'BOOLEAN', 1, 1,
     '백엔드 GPU 동시호출 세마포어. 기본 OFF(기존 무제약). 과부하 시 ON.'),
    ('ai.gpu-gate.permits', 'AI_GPU_GATE', 'GPU permit 동시 허용수', '12', 'NUMBER', 1, 1,
     '게이트 ON 시 총 동시 GPU 호출 상한. 동시성 스윕 무릎점 12.'),
    ('ai.gpu-gate.acquire-timeout-seconds', 'AI_GPU_GATE', 'permit 대기 상한(초)', '30', 'NUMBER', 1, 1,
     'permit 을 이 시간 안에 못 얻으면 각 도메인 실패 경로로.'),
    ('ai.analysis.chain-total-time-budget-seconds', 'AI_TIMEOUT', 'C 폴백 체인 총 시간예산(초)', '120', 'NUMBER', 1, 1,
     '[전체] C 적합도 폴백 체인의 재시도 억제 상한. 각 tier 첫 시도는 이 값에 안 막힘(per-tier 우선).'),
    ('ai.analysis.claude-timeout-seconds', 'AI_TIMEOUT', 'C Claude tier 타임아웃(초)', '30', 'NUMBER', 1, 1,
     '[단계별] C 폴백의 Claude tier 최소 보장 per-attempt 타임아웃.'),
    ('ai.analysis.openai-timeout-seconds', 'AI_TIMEOUT', 'C OpenAI tier 타임아웃(초)', '30', 'NUMBER', 1, 1,
     '[단계별] C 폴백의 OpenAI tier 최소 보장 per-attempt 타임아웃.'),
    ('ai.analysis.oss-total-time-budget-seconds', 'AI_TIMEOUT', 'C 자체모델 총 시간예산(초)', '90', 'NUMBER', 1, 1,
     '[영역별] C OSS(자체 3B) tier 재시도 포함 총 상한.'),
    ('ai.correction.self-total-time-budget-seconds', 'AI_TIMEOUT', 'E 첨삭 자체모델 예산(초)', '30', 'NUMBER', 1, 1,
     '[영역별] E 첨삭 self tier 총 시간예산.'),
    ('ai.b-analysis.local-llm-total-time-budget-seconds', 'AI_TIMEOUT', 'B 공고 로컬LLM 예산(초)', '180', 'NUMBER', 1, 1,
     '[영역별] B 로컬 LLM per-attempt 상한.')
ON DUPLICATE KEY UPDATE
    setting_group  = VALUES(setting_group),
    display_name   = VALUES(display_name),
    fallback_value = VALUES(fallback_value),
    value_type     = VALUES(value_type),
    description    = VALUES(description);
