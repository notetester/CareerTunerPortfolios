-- C 영역: NCS(국가직무능력표준) 세분류 카탈로그.
-- 용도: 공고→직무→NCS 세분류 매핑, 적합도 grounding(evidence-gated RAG 근거), 사용자 NCS 검색.
-- 출처: NCS정보망DB(2026-02) 정규화 — 세분류 1,109건(대분류 24). 원자료·정규화 산출물은 ai-artifacts 서브모듈.
-- detail_json 구조: units[] → { unitNo, unitName, level, elements[] → { elementName, level,
--   performanceCriteria[], knowledge[], skills[], attitudes[] } }
CREATE TABLE IF NOT EXISTS ncs_classification (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    ncs_code      VARCHAR(60)  NOT NULL,               -- 복합 세분류 코드(대-중-소-세)
    major_code    VARCHAR(20)  NOT NULL,
    major_name    VARCHAR(100) NOT NULL,
    middle_code   VARCHAR(20)  NOT NULL,
    middle_name   VARCHAR(100) NOT NULL,
    minor_code    VARCHAR(20)  NOT NULL,
    minor_name    VARCHAR(100) NOT NULL,
    sub_code      VARCHAR(20)  NOT NULL,
    sub_name      VARCHAR(200) NOT NULL,               -- 세분류명(직무명)
    unit_count    INT NOT NULL DEFAULT 0,              -- 능력단위 수
    element_count INT NOT NULL DEFAULT 0,              -- 능력단위요소 수
    min_level     INT NULL,                            -- 최저 NCS 수준
    max_level     INT NULL,                            -- 최고 NCS 수준
    search_text   MEDIUMTEXT NULL,                     -- 능력단위명+요소명+기술 키워드(검색)
    detail_json   MEDIUMTEXT NOT NULL,                 -- 전체 중첩 구조
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ncs_code (ncs_code),
    KEY idx_ncs_sub_name (sub_name),
    KEY idx_ncs_major (major_code, middle_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
