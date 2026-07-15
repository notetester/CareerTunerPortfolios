# 저장소 운영 문서 안내

Git, 브랜치, 서브모듈과 문서 경로 변경 규칙의 진입점이다. 협업 규칙의 최종 정본은
[AGENTS.md](../../../AGENTS.md)이며, 이 폴더는 반복 절차와 보조 설명만 맡는다.

## 현재 문서

| 주제 | 문서 | 경로 정책 |
| --- | --- | --- |
| 브랜치 명명 | [branch-naming.md](branch-naming.md) | 저장소 운영 폴더에서 관리 |
| AI 관련 저장소 경계 | [AI_REPOSITORY_BOUNDARIES.md](../../AI_REPOSITORY_BOUNDARIES.md) | 현재 경로 유지 |
| 공통 협업·push·PR 규칙 | [AGENTS.md](../../../AGENTS.md) | 루트 고정 |
| 프로젝트 진입점 | [README.md](../../../README.md) | 루트 고정 |

## 경로 변경 체크리스트

1. `git grep`과 Markdown link audit로 모든 내부 참조를 찾는다.
2. Java·TypeScript 주석뿐 아니라 JSON, YAML, workflow, script의 기능적 경로 참조를 확인한다.
3. 고정된 네 서브모듈 commit 안에서 메인 저장소 경로를 참조하는 문서를 확인한다.
4. `git mv`와 참조 수정을 같은 커밋에 넣는다.
5. Markdown 경로·fragment 검사와 해당 경로 소비 테스트를 실행한다.
6. 외부 deep link가 알려진 문서는 구경로 호환 안내 또는 단계적 이전을 사용한다.

서브모듈 경로를 바꾸는 작업은 `.gitmodules` 수정만으로 끝나지 않는다. 별도 저장소 문서, CI, 공개
포트폴리오 복사본과 clone 절차까지 함께 검증해야 한다.
