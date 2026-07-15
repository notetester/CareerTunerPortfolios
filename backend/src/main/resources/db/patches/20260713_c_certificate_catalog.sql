-- C 영역: 통합 자격증 카탈로그(국가+민간) + 국가 시험일정.
-- 목적: 이름 매칭이 아니라 "설명 붙은" 자격증 검색 + AI 추천 근거. 국가·민간 구별 없이 설명을 담고,
-- 국가만 시험일정을 추가로 가진다. 민간은 자격개요·등급별직무내용이 풍부(공공데이터), 국가는 기본정보 +
-- NCS 세분류 best-effort 매칭으로 직무 설명을 보강한다(풍부 설명은 data.go.kr API serviceKey 확보 시 확장).
-- 출처: 한국산업인력공단 국가자격 목록/종목코드, 국가전문자격 사전공고 일정, 한국직업능력연구원 민간자격등록(2025-12-31).
CREATE TABLE IF NOT EXISTS certificate (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    cert_type    VARCHAR(20)  NOT NULL,               -- NATIONAL_TECH / NATIONAL_PROF / PRIVATE
    name         VARCHAR(300) NOT NULL,
    grade        VARCHAR(1000) NULL,                  -- 등급(민간: 1급,2급… 다등급은 길다)
    authority    VARCHAR(200) NULL,                   -- 주무부처/소관
    issuer_org   VARCHAR(300) NULL,                   -- 신청기관(민간) / 한국산업인력공단(국가)
    series       VARCHAR(100) NULL,                   -- 계열(국가기술자격: 기술사/기사/기능사…)
    jm_cd        VARCHAR(20)  NULL,                   -- 국가 종목코드
    reg_no       VARCHAR(40)  NULL,                   -- 민간 등록번호
    official     VARCHAR(20)  NULL,                   -- 공인/등록/N
    status       VARCHAR(20)  NULL,                   -- ACTIVE/등록완료/등록폐지
    description  MEDIUMTEXT   NULL,                   -- 개요+직무내용(민간) / 구분+계열+NCS(국가)
    ncs_sub_name VARCHAR(200) NULL,                   -- best-effort NCS 세분류 매칭(국가)
    has_schedule TINYINT(1)   NOT NULL DEFAULT 0,     -- 국가 시험일정 보유
    search_text  MEDIUMTEXT   NULL,                   -- 이름+개요+직무 키워드(검색)
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cert_type_name (cert_type, name),
    KEY idx_cert_name (name),
    KEY idx_cert_jmcd (jm_cd)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS certificate_exam_schedule (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    cert_name       VARCHAR(300) NOT NULL,
    year            INT NOT NULL,
    round_name      VARCHAR(100) NULL,                -- 예: 2026년 1차
    doc_reg_start   VARCHAR(8) NULL,                  -- 필기 원서접수 시작(YYYYMMDD)
    doc_reg_end     VARCHAR(8) NULL,
    doc_exam        VARCHAR(8) NULL,                  -- 필기 시험
    doc_pass        VARCHAR(8) NULL,                  -- 필기 합격발표
    prac_exam_start VARCHAR(8) NULL,                  -- 실기 시작
    prac_exam_end   VARCHAR(8) NULL,
    prac_pass       VARCHAR(8) NULL,                  -- 최종 합격발표
    PRIMARY KEY (id),
    KEY idx_ces_name_year (cert_name, year)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
