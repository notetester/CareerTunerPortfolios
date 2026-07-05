-- 광고 시스템 (W7) — 관리자 광고 관리 + 3플랫폼 노출 + 유료플랜 광고 제거
-- 1) advertisement    : 광고 본체. 배치(placement)·타겟 플랫폼(target_platform)·게재 기간·활성·우선순위/가중치 + 노출/클릭 카운터.
--    * 노출/클릭 집계는 컬럼 직접 +1(로그 테이블 없음). TripTogether ad_campaign 이식 — CT 화면/라우트에 맞게 배치 코드·플랫폼 정의.
--    * 유료플랜 사용자 광고 제외는 노출 조회(서비스)에서 users.plan 검사로 처리(스키마 개입 없음).
-- 이미지: 기존 file_asset(image_file_id) 재사용. link_url 은 외부/내부 링크 문자열.

CREATE TABLE IF NOT EXISTS advertisement (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    title           VARCHAR(200)  NOT NULL COMMENT '광고 제목(관리자 식별·대체 텍스트)',
    image_file_id   BIGINT        NULL COMMENT '광고 이미지 file_asset id(선택). 없으면 텍스트 배너',
    link_url        VARCHAR(1000) NULL COMMENT '클릭 시 이동 URL. 비어 있으면 클릭 비활성',
    placement       VARCHAR(30)   NOT NULL COMMENT '노출 위치. HOME_BANNER/FEED_INLINE/SIDEBAR/INTERSTITIAL',
    target_platform VARCHAR(20)   NOT NULL DEFAULT 'ALL' COMMENT '타겟 플랫폼. WEB/APP/DESKTOP/ALL',
    start_at        DATETIME      NULL COMMENT '게재 시작. NULL=제한 없음',
    end_at          DATETIME      NULL COMMENT '게재 종료. NULL=제한 없음',
    active          TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '활성 여부',
    priority        INT           NOT NULL DEFAULT 0 COMMENT '우선순위(높을수록 먼저 선택)',
    weight          INT           NOT NULL DEFAULT 1 COMMENT '동일 우선순위 내 가중 랜덤 비중(>=1)',
    impression_count BIGINT       NOT NULL DEFAULT 0 COMMENT '노출 누적',
    click_count     BIGINT        NOT NULL DEFAULT 0 COMMENT '클릭 누적',
    created_by      BIGINT        NULL COMMENT '등록 관리자 id',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_advertisement_serve (placement, active, start_at, end_at),
    KEY idx_advertisement_platform (target_platform),
    CONSTRAINT chk_advertisement_placement
        CHECK (placement IN ('HOME_BANNER', 'FEED_INLINE', 'SIDEBAR', 'INTERSTITIAL')),
    CONSTRAINT chk_advertisement_platform
        CHECK (target_platform IN ('WEB', 'APP', 'DESKTOP', 'ALL')),
    CONSTRAINT fk_advertisement_image FOREIGN KEY (image_file_id) REFERENCES file_asset (id) ON DELETE SET NULL,
    CONSTRAINT fk_advertisement_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '광고(배치·플랫폼·기간·집계)';
