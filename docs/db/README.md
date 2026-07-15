# DB 설계 산출물

이 폴더는 현재 schema에서 다시 만든 DB 설계 산출물의 진입점으로 예약한다. 기존 A 영역 HTML/PDF는
프로필 버전·AI provenance·MFA·세부 관리자 권한 이전 자료여서 2026-06 보관본으로 이동했다.

| 파일 | 범위 |
| --- | --- |
| [`a-profile-db-design-snapshot.html`](../archive/2026-06/a-profile-db-design-snapshot.html) | A 영역 과거 DB 설명서(현재 schema 아님) |
| [`a-profile-db-design-snapshot.pdf`](../archive/2026-06/a-profile-db-design-snapshot.pdf) | 위 보관본의 과거 인쇄용 PDF |

스키마의 정본은 `backend/src/main/resources/db/schema.sql`과 시간순 patch이며, MyBatis 계약은
`backend/src/main/resources/mapper/`가 결정한다. HTML·PDF가 정본과 다르면 런타임 스키마를 우선하고 두 산출물을
함께 다시 만든다. PDF만 직접 수정하지 않으며, 새 산출물 PR에는 비교한 schema/patch 기준 commit을 기록한다.
