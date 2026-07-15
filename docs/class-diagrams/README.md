# 클래스 다이어그램 산출물

이 폴더는 현재 코드에서 다시 만든 클래스 다이어그램의 진입점으로 예약한다. 기존 A 영역 다이어그램은
권한·프로필 AI 계약이 크게 바뀌어 2026-06 보관본으로 이동했다.

| 파일 | 범위 |
| --- | --- |
| [`a-profile-class-diagram-snapshot.html`](../archive/2026-06/a-profile-class-diagram-snapshot.html) | A 영역 과거 설명용 다이어그램(현재 구현 아님) |

HTML은 실행 코드의 정본이 아니다. 현재 클래스·메서드·관계는 `backend/src/main/java`, `frontend/src`와
[아키텍처 문서](../ARCHITECTURE.md)를 우선한다. 관련 코드가 바뀌면 HTML의 표시 내용을 소스와 대조하고,
검증하지 않은 상태에서 “최신”이라고 표기하지 않는다. 현재는 별도 자동 생성기가 없으므로 새 HTML을 만들 때
기준 commit을 기록하고 브라우저 화면과 인쇄 레이아웃을 함께 확인한다.
