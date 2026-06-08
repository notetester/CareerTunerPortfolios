---
name: push
description: 현재 작업 브랜치를 origin에 push. fetch → dev 변경 확인 → 충돌 분석 → 사용자 결정 → push 순으로 진행.
---

# push — 현재 브랜치를 origin에 push

통합 브랜치는 `dev`로 고정. 작업 브랜치는 현재 체크아웃된 브랜치를 사용한다.

## 실행 순서

### 0. 보호 브랜치 가드

```
git branch --show-current
```

현재 브랜치가 `dev`, `main`, `master`, `live` 중 하나면 즉시 거절:

```
보호 브랜치에서는 push 불가 (dev 직접 push 금지 — AGENTS.md 규칙).
본인 작업 브랜치로 이동 후 다시 실행할 것.
```

개인 작업 브랜치일 때만 다음 단계 진행.

### 1. git fetch

```
git fetch origin
```

### 2. dev에 새 커밋 있는지 확인

```
git log --oneline <현재브랜치>..origin/dev
```

- 새 커밋 없으면 → 5단계로 바로 이동
- 새 커밋 있으면 → 3단계 진행

### 3. 충돌 가능성 분석 후 사용자에게 보고

```
git diff --name-only <현재브랜치>...origin/dev
```

내가 건드린 파일과 겹치는 파일 목록을 정리해서 보고하고, 사용자에게 선택을 물어볼 것:

- **그대로 push** — 통합은 나중에 PR에서
- **merge 후 push** — `git merge origin/dev` 시도

### 4. merge 선택 시

충돌 발생하면:

- 충돌 파일 목록과 양쪽 내용을 분석해서 사용자에게 보고
- 사용자 결정 후 Edit으로 충돌 마커 제거
- `git add` + merge commit

### 5. 커밋 메시지 제안

미커밋 변경사항 있으면 메시지 후보 제시 → 사용자 승인 후 커밋. 변경사항 없으면 생략.

- 형식: `feat:` / `fix:` / `docs:` / `chore:` / `refactor:` prefix
- **AI 도구 표기 절대 금지** — Co-Authored-By 트레일러, "Generated with ..." 문구 등 일절 넣지 않는다 (AGENTS.md 규칙)

### 6. push

```
git push origin <현재브랜치>
```
