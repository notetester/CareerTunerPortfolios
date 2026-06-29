-- C 소유 R3 review-first evidence gate.
-- 적합도 분석 AI 설명 출력의 결정론 후처리 안전층(점수/판단·원본 데이터 미변경, E1 grounding guard 위의 soft review 층).
-- 타 담당(A/B/D/E/F) 원본·공통 스키마는 변경하지 않고 C-only 테이블만 additive 로 추가한다.
-- schema.sql 캐노니컬에도 동일하게 반영한다(기존 C 정규화 테이블과 동일 관행).

-- gate 가 판단에 사용한 evidence 버킷 스냅샷(감사·재현용). 스킬명/축약만 저장(개인정보·원문 prompt 미포함).
CREATE TABLE IF NOT EXISTS fit_analysis_evidence_source (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    fit_analysis_id BIGINT       NOT NULL,
    source_type     VARCHAR(40)  NOT NULL,                 -- userEvidence/jobRequirements/catalogFacts/companyContext
    user_owned      TINYINT(1)   NOT NULL DEFAULT 0,       -- 지원자 보유 근거인지(userEvidence 만 1)
    item_count      INT          NOT NULL DEFAULT 0,
    items_json      JSON         NULL,                     -- 축약 스킬/근거 목록(원문·개인정보 제외)
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fit_evidence_source_analysis (fit_analysis_id, source_type),
    CONSTRAINT fk_fit_evidence_source_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 적합도 분석 1건당 gate 결정(1:1). gate 는 점수/applyDecision/matched/missing 을 바꾸지 않고 노출·검토 상태만 기록한다.
CREATE TABLE IF NOT EXISTS fit_analysis_gate_result (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    fit_analysis_id       BIGINT       NOT NULL,
    gate_status           VARCHAR(20)  NOT NULL,            -- PASSED/REVIEW_REQUIRED/REJECTED
    needs_human_review    TINYINT(1)   NOT NULL DEFAULT 0,
    reason_count          INT          NOT NULL DEFAULT 0,
    max_severity          VARCHAR(20)  NULL,                -- warning/critical(없으면 NULL)
    gate_reasons_json     JSON         NULL,                -- [{type,claim,reason,severity}] 축약(개인정보 제외)
    evidence_gate_version VARCHAR(40)  NOT NULL,            -- 정책 버전 추적/롤백용(r3-review-first)
    rag_runtime_enabled   TINYINT(1)   NOT NULL DEFAULT 0,  -- RAG runtime 자동주입 보류(항상 0)
    rewrite_applied       TINYINT(1)   NOT NULL DEFAULT 0,  -- rewrite 자동노출 보류(항상 0)
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fit_gate_result_analysis (fit_analysis_id),
    KEY idx_fit_gate_result_status (gate_status, created_at),
    CONSTRAINT fk_fit_gate_result_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
