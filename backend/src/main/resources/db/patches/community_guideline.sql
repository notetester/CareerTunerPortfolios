-- =====================================================================
--  커뮤니티 가이드라인 테이블
--  담당: HEON-JEONG-SUK
--  작성일: 2026-06-12
-- =====================================================================

CREATE TABLE IF NOT EXISTS community_guideline (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    version_label   VARCHAR(20)     NOT NULL COMMENT '버전 라벨 (v1.0, v1.1 등)',
    summary         VARCHAR(500)    NULL     COMMENT '개정 요약 — 공지사항 고지에 사용',
    lede            TEXT            NULL     COMMENT '머리말 (운영 철학 도입부)',
    oks_json        JSON            NULL     COMMENT '괜찮아요 예시 배열',
    nos_json        JSON            NULL     COMMENT '안 돼요 예시 배열',
    rules_json      JSON            NULL     COMMENT '금지 항목 배열 [{t,s,b}, ...]',
    params_json     JSON            NULL     COMMENT '운영 파라미터 {blind,sla,expire,s1,s2,appeal}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / SCHEDULED',
    enforce_type    VARCHAR(20)     NOT NULL DEFAULT 'IMMEDIATE' COMMENT 'IMMEDIATE / SCHEDULED',
    scheduled_at    DATETIME        NULL     COMMENT '예약 시행 시각',
    published_at    DATETIME        NULL     COMMENT '실제 게시 시각',
    admin_id        BIGINT          NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    KEY idx_guideline_status (status, published_at DESC),
    CONSTRAINT fk_guideline_admin FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기본 데이터: 최초 제정 v1.0
INSERT INTO community_guideline (version_label, summary, lede, oks_json, nos_json, rules_json, params_json, status, enforce_type, published_at)
SELECT 'v1.0', '최초 제정 — 선 게시·후 검토 원칙',
  'CareerTuner 커뮤니티는 면접·취업 경험을 솔직하게 나누는 곳입니다. 솔직함이 핵심 가치이기 때문에, 저희는 글을 미리 검열하지 않습니다. 대신 다른 사람에게 실제 피해를 주는 행동만 좁고 명확하게 금지하고, 위반에는 단계적으로 대응합니다.',
  '["회사·전형에 대한 부정적 평가","면접 질문·과정 복기","연봉·처우 등 민감한 주제의 토론","날 선 의견과 반박 (내용을 향한 비판)"]',
  '["특정인을 알아볼 수 있게 쓰는 것","인신공격·혐오 표현","지어낸 후기","회사의 미공개 기밀"]',
  '[{"t":"개인 특정·신상 노출","s":0,"b":"실명, 연락처, 또는 부서·직급·시기 조합으로 누구인지 알 수 있는 서술. 익명 커뮤니티에서 가장 큰 피해를 만드는 행위라 가장 엄격하게 봅니다."},{"t":"인신공격·혐오 표현","s":0,"b":"특정 이용자나 집단을 향한 모욕·위협, 출신·성별·연령 등에 대한 비하. 거친 말투나 욕설 섞인 푸념 자체는 제재 대상이 아니에요 — 사람을 겨냥할 때만 적용됩니다."},{"t":"허위 사실·조작된 후기","s":0,"b":"경험하지 않은 전형의 후기, 의도적인 평판 조작. 단순히 기억이 달라서 생긴 오류는 수정 요청으로 처리하고 제재하지 않습니다."},{"t":"광고·스팸·도배","s":0,"b":"영리 목적의 홍보, 동일 내용 반복 게시, 외부 유도 링크. 스터디 모집이나 무료 자료 공유는 광고로 보지 않아요."},{"t":"불법 정보·기밀 유출","s":1,"b":"법령 위반 콘텐츠, 기업의 미공개 기밀·내부 문서 유출. 피해가 크고 회복이 어려운 영역이라 단계 없이 즉시 영구 제한될 수 있습니다."}]',
  '{"blind":3,"sla":24,"expire":90,"s1":7,"s2":30,"appeal":30}',
  'PUBLISHED', 'IMMEDIATE', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM community_guideline WHERE version_label = 'v1.0');
