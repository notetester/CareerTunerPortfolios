-- =====================================================================
--  F 담당(법적문서) 스키마 마이그레이션 — 2026-06-21
--  Task 4 Phase 1 — 약관/개인정보처리방침/마케팅수신동의를 버전·조항으로 저장.
--
--  배경:
--   AdminTerms(관리자 약관 편집)·LegalDocPage(공개 열람)의 하드코딩 mock을 백엔드로
--   실체화한다. 문서 타입별로 버전을 쌓아 개정 이력을 남기고, "현재 시행본"은
--   읽는 시점에 effective_date<=NOW() 중 최신본으로 계산한다(별도 스케줄러 없이 예약 시행).
--
--  설계 결정(DB 체크리스트):
--   - 라이프사이클: status = DRAFT | PUBLISHED 두 단계만 둔다.
--       게시중(live)/예정(next)/종료(old) 배지는 effective_date 와 NOW() 비교로 *계산*한다.
--         · live = PUBLISHED 중 effective_date<=NOW() 의 effective_date 최댓값 1건
--         · next = PUBLISHED 이고 effective_date>NOW()
--         · old  = PUBLISHED 이고 effective_date<=NOW() 인데 live 가 아닌 것
--       (AdminTerms 의 live/next/old 배지와 1:1로 맞아떨어짐 → 스케줄러 불필요)
--   - 보존: 게시본(PUBLISHED)은 hard delete 금지(법적/감사). DRAFT 만 삭제 가능.
--   - N+1 방지: 공개 조회 = (버전 1건) + (그 버전 조항 일괄) 2쿼리. 조항을 행마다 조회하지 않는다.
--   - 엣지: doc_type별 DRAFT 1건 제한(앱), 조항 0개 게시 차단(앱),
--       is_adverse=1(불리한 변경)이면 시행일 30일 리드타임 경고(일반 7일) — 앱에서 검증.
--   - AI 확장 자리: summary(개정요약, AI 자동생성), is_adverse(불리한변경 판정),
--       legal_clause.embedding(bge-m3 벡터 → 챗봇 RAG 지식원). 컬럼만 미리 두고 로직은 후속 Phase.
--
--  ⚠️ 공유 DB 변경 → 팀장 합의 후 AWS(team1_db) 적용. 로컬은 이 패치를 직접 실행.
--     CREATE TABLE IF NOT EXISTS 로 재실행 안전.
--  ☐ 적용 이력: (미적용) AWS team1_db 적용 후 날짜 기록할 것.
--  실행: mysql -h <host> -u <user> -p <db> < 20260621_f_legal_documents.sql
-- =====================================================================

-- ── 법적 문서 버전 ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS legal_document_version (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    doc_type        VARCHAR(20)  NOT NULL COMMENT 'TERMS | PRIVACY | MARKETING | AI_CONSENT | COPYRIGHT (LegalDocType 와 정렬)',
    version_label   VARCHAR(20)  NOT NULL COMMENT '표시용 버전 (예: v2.4)',
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT | PUBLISHED',
    summary         VARCHAR(500) NULL     COMMENT '개정 요약 (공지·이메일 고지에 사용 / AI 자동생성 가능)',
    is_adverse      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '불리한 변경 여부 (1이면 시행 30일 전 공지 의무)',
    effective_date  DATETIME     NULL     COMMENT '시행일 (DRAFT면 NULL). 공개 노출/배지 계산 기준',
    published_at    DATETIME     NULL     COMMENT '게시 시각',
    admin_id        BIGINT       NULL     COMMENT '작성 관리자(users.id)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_legal_ver_type_status (doc_type, status),
    KEY idx_legal_ver_type_eff (doc_type, effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='법적 문서 버전 (약관/개인정보/마케팅 개정 이력)';

-- ── 법적 문서 조항 ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS legal_clause (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    version_id  BIGINT       NOT NULL COMMENT 'legal_document_version.id',
    seq         INT          NOT NULL COMMENT '조항 순서 (제N조)',
    title       VARCHAR(200) NOT NULL COMMENT '조항 제목',
    body        TEXT         NOT NULL COMMENT '조항 본문 (줄바꿈 = 항 1.2.3. 구분)',
    embedding   JSON         NULL     COMMENT 'bge-m3 임베딩 (AI B 챗봇 RAG용, 1024차원). 후속 Phase',
    PRIMARY KEY (id),
    KEY idx_legal_clause_ver (version_id, seq),
    CONSTRAINT fk_legal_clause_ver FOREIGN KEY (version_id)
        REFERENCES legal_document_version (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='법적 문서 조항 (버전에 종속, 버전 삭제 시 CASCADE)';

-- ── 검증 (적용 후 확인용) ─────────────────────────────────────────────
-- SHOW TABLES LIKE 'legal\_%';
-- SELECT doc_type, version_label, status, effective_date FROM legal_document_version ORDER BY doc_type, effective_date;
