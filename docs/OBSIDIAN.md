# Obsidian Vault 운영

CareerTuner는 `docs/`만 문서인 프로젝트가 아니다. 루트 README/AGENTS, backend/frontend/desktop README, `ml/` 하위 모델 문서, `docs/` 하위 기획/아키텍처 문서, 그리고 세 개의 docs submodule이 함께 프로젝트 지식망을 이룬다. 따라서 Obsidian은 `docs/` 폴더가 아니라 repo 루트 `CareerTuner/`를 Vault로 연다.

## 판단 근거

- Markdown 문서는 현재 270개 이상이며 `docs/`, `ml/`, `frontend/`, `backend/`, `desktop/`, repo 루트에 분산돼 있다.
- 현재 구현 상태는 `backend/README.md`, `frontend/README.md`, `desktop/README.md`, `ml/**/README.md`에 있고, 이 문서들은 `docs/` 전용 Vault에서는 자연스럽게 검색/백링크 대상이 되지 않는다.
- `docs/ai-reports`, `docs/ai-artifacts`, `docs/storyboard`는 submodule이지만 로컬 파일시스템에서는 일반 폴더처럼 읽힌다.
- Codex/Claude Code 같은 도구는 이전 대화 맥락을 잃기 쉬우므로, 문서 본문을 복제하는 방식보다 작업별 진입점과 책임 경계를 명확히 연결하는 방식이 유리하다.

## 여는 방법

1. 최신 dev와 submodule을 받는다.

```bash
git switch dev
git pull --ff-only origin dev
git submodule update --init --recursive
```

2. Obsidian에서 `CareerTuner/` 루트 폴더를 Vault로 연다.

3. 첫 문서는 [docs/00_KNOWLEDGE_MAP.md](00_KNOWLEDGE_MAP.md)를 연다.

## Submodule 주의

메인 repo는 submodule 내부 파일을 직접 추적하지 않고 gitlink pointer만 추적한다. submodule 내부 문서를 수정해야 하면 해당 submodule repo에서 먼저 commit/push한 뒤, 메인 repo에서 pointer 변경을 PR로 올린다.

`git submodule update --remote`는 일반 최신화 명령으로 쓰지 않는다. `docs/ai-artifacts`처럼 메인 repo의 dev가 default branch보다 앞선 별도 branch 커밋을 고정할 수 있기 때문이다. 무심코 `--remote`를 쓰면 프로젝트가 고정한 문맥을 되감을 수 있다.

## 커밋하는 Obsidian 파일

팀 공통으로 의미 있는 최소 설정만 커밋한다.

- `.obsidian/app.json`: Vault 검색/파일 탐색에서 빌드 산출물과 의존성 폴더를 제외한다.
- `docs/00_KNOWLEDGE_MAP.md`: 프로젝트 지식맵 진입점.
- `docs/OBSIDIAN.md`: 이 운영 원칙.

개인 작업 상태는 커밋하지 않는다.

- `.obsidian/workspace*.json`
- `.obsidian/cache/`
- `.obsidian/hotkeys.json`
- `.obsidian/graph.json`
- `.obsidian/plugins/`
- `.obsidian/themes/`

## 링크 작성 원칙

- 표준 Markdown 상대 링크를 우선한다.
- 한글 파일명과 공백이 있는 경로는 기존 문서처럼 URL encoding을 사용한다.
- 새 문서가 기존 기준 문서를 대체한다고 쓰지 않는다. 책임 범위가 다르면 기존 문서에 링크만 추가한다.
- 긴 설명을 지식맵에 복제하지 않는다. 지식맵은 문서의 위치와 읽는 순서를 알려주는 역할만 한다.

## 업데이트 기준

다음 변경이 생기면 [docs/00_KNOWLEDGE_MAP.md](00_KNOWLEDGE_MAP.md)를 같이 갱신한다.

- 새 기능군 문서 또는 module README 추가.
- 공통 API, 인증/권한, DB, AI provider, release flow처럼 여러 영역에 영향을 주는 문서 추가.
- submodule의 대표 README나 보고서 진입점 변경.
- 심사/발표용 산출물의 대표 위치 변경.
