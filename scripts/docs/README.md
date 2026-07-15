# 문서 링크 검사기

이 폴더는 메인 저장소가 직접 추적하는 문서 참조의 회귀를 검사한다. 문서를 옮기거나 이름을 바꿀 때는 아래 명령을
실행해 상대 링크, 로컬 Markdown 경로, 목차 앵커와 서브모듈 경계를 함께 확인한다.

```powershell
node scripts/docs/check-markdown-links.mjs
```

별도 npm 패키지는 필요하지 않으며 Node.js 20 이상과 Git만 사용한다.
비공개 서브모듈의 고정 commit을 로컬 object store 없이 읽어야 하는 CI에서는 read 권한 토큰을
`DOCS_SUBMODULE_TOKEN` 환경변수로만 주입한다. 검사기는 토큰을 URL·출력·파일에 쓰지 않고 해당 Git fetch의
일시적 HTTP 인증 헤더로 전달한다.

PR CI에는 repository secret을 주입하지 않는다. 다음 옵션은 메인 저장소가 직접 추적하는 대상만 검사하고
비공개 gitlink 안쪽 참조 수를 별도로 보고한다. 보호된 `dev` push에서는 옵션 없이 전체 검사를 실행해
서브모듈 고정 tree까지 확인한다.

```powershell
node scripts/docs/check-markdown-links.mjs --main-repo-only
```

## 검사 범위

- `git ls-files`가 반환하는 메인 저장소 추적 파일만 원본으로 읽는다.
- Markdown의 인라인 링크, reference-style 링크와 Markdown·HTML 문서의 `href`/`src` 속성을 검사한다.
- Markdown뿐 아니라 HTML, JSON, YAML, Java, Kotlin, JavaScript, TypeScript, 셸 스크립트 등 추적 중인 텍스트
  파일에 적힌 로컬 `*.md` 경로도 검사한다.
- Markdown 대상의 `#fragment`는 GitHub 방식의 제목 slug와 명시적 HTML `id`/`name`에 대조한다. 같은 제목이
  반복되면 `-1`, `-2` 접미사까지 반영한다.
- `node_modules`, `build`, `dist`, `vendor`, `.gradle`, `graphify-out` 안의 생성물은 검사하지 않는다.
- `https://`, `mailto:` 같은 외부 URL은 세지 않고 **네트워크 요청도 보내지 않는다**. `/login` 같은 웹 루트
  라우트 역시 저장소 파일 링크가 아니므로 건너뛴다.

## 서브모듈 링크

`docs/ai-reports/`, `docs/ai-artifacts/`, `docs/obsidian-vault/`, `docs/storyboard/` 같은 gitlink 아래 경로는 현재
체크아웃된 서브모듈 브랜치가 아니라 **메인 저장소 index가 고정한 commit SHA의 tree**를 기준으로 확인한다.
포인터를 아직 stage하지 않은 경우에는 `HEAD`의 gitlink를 사용한다. 따라서 서브모듈 변경은 먼저 해당
저장소에서 commit한 뒤 메인 저장소에서 gitlink를 stage하고 검사한다.

검사기는 다음 순서로 고정 tree를 찾는다.

1. 초기화된 서브모듈 worktree
2. 로컬 `.git/modules/...` object store
3. OS 임시 폴더로 해당 SHA만 shallow fetch

3번은 임의의 외부 웹 링크를 검사하는 동작이 아니라 저장소가 고정한 Git object를 읽기 위한 동작이다. 네트워크를
완전히 금지해야 하는 환경에서는 모든 서브모듈을 먼저 초기화한 뒤 다음 옵션을 사용한다.

```powershell
node scripts/docs/check-markdown-links.mjs --no-submodule-fetch
```

고정 SHA 또는 tree를 로컬에서 읽을 수 없으면 검사를 생략하지 않고 실패한다. `--verbose`를 추가하면 각 gitlink의
SHA와 object 출처를 표시한다.

## 실패 해석

실패 행은 `원본 파일:줄`, 참조 종류, 원문, 해석한 저장소 경로, 실패 이유를 출력한다.

```text
ERROR docs/example.md:12 [markdown-link] ../missing.md -> missing.md (target is not tracked by the main repository) <!-- docs-link-check: ignore -->
```

- `target is not tracked`: 경로가 틀렸거나 새 파일을 아직 Git에 추가하지 않은 경우다.
- `absent from the pinned submodule tree`: 서브모듈의 최신 브랜치가 아니라 메인 저장소가 고정한 SHA에 파일이 없다.
- `Markdown anchor does not exist`: 파일은 있지만 제목이나 명시적 앵커가 달라졌다.
- `path escapes the repository root`: `../` 수가 과도한 링크다.

문서 안에서 존재하지 않는 경로를 의도적으로 예시로 보여줘야 한다면 같은 줄에
`docs-link-check: ignore`를 적어 bare-path 검사만 제외할 수 있다. 실제 Markdown 링크는 예시라도 가능한 fixture나
현존 문서를 가리키는 편을 권장한다.

## 문서 이동 순서

1. `git mv`로 파일을 이동한다.
2. `rg -n "기존파일명|기존/경로" --glob "*.md"`로 명시적 참조를 찾는다.
3. 상위 폴더 `README.md`의 색인과 관련 정본 문서의 링크를 함께 갱신한다.
4. 이 검사기를 실행한다.
5. `git diff --check`와 변경 범위 검토를 마친다.

번호가 식별자인 ML 실험 보고서와 서브모듈 문서는 임의로 번호를 재사용하거나 최신 branch 경로만 먼저 가리키면 안
된다. 메인 저장소의 gitlink pointer와 링크 변경을 같은 변경 단위에서 검증한다.
