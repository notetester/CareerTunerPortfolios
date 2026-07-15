# 브랜치 명명 규칙

CareerTuner 저장소에서 브랜치 이름을 정하는 권장 방식.
공동 개발에서 이름만 보고도 **누구의 브랜치인지·무엇을 위한 것인지** 알 수 있게 하는 것이 목적이다.

## 이름 형식

브랜치 이름은 소유자와 목적이 드러나게 둔다.

```
<owner>/<purpose>
```

- `<owner>` — 소유자 식별자. 사람은 본인 owner 이름을 쓴다. 도구가 자동 생성하는 브랜치도 책임자 owner 를 앞에 둔다.
- `<purpose>` — 목적을 kebab-case 로 적는다. 예: `ai-fix`, `rag-r2b`, `4090-ops`, `admin-search`.
- 예: `LEE-JEONG-GUCK/ai-fix`.

소유자나 목적을 이름만으로 알기 어려운 형태(PR 번호만 있는 `pr-82`, `codex/pr82-merge-dev` 등)는 피한다.

소유자별 작업 영역은 [TEAM_WORK_DISTRIBUTION.md](../../TEAM_WORK_DISTRIBUTION.md)를 참고한다.

### git 이름공간 참고

Git 에서는 `LEE-JEONG-GUCK` 브랜치와 `LEE-JEONG-GUCK/ai-fix` 브랜치를 동시에 둘 수 없다(같은 이름공간 충돌).
owner 이름만으로 된 개인 작업 브랜치를 유지하는 동안 목적별 브랜치가 필요하면, 영역 코드 형식(`docs/c-...`, `feat/c-...`)이나
하이픈 형식(`LEE-JEONG-GUCK-ai-fix`)을 쓴다. 이 저장소의 C 영역 문서 브랜치는 `docs/c-<purpose>` 형식을 써 왔다.

## 보호 브랜치

`dev` 는 통합 대상, `main`/`master`/`live` 는 릴리스 브랜치다. 직접 push 대신 개인 브랜치에서 PR 로 반영한다.

## 한 브랜치 한 목적

한 브랜치는 한 목적(하나의 PR)에 대응시킨다.
