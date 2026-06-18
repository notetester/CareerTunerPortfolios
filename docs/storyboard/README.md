# CareerTuner · C 영역 UI/UX 스토리보드

채용공고 **적합도 분석 → 부족 역량·학습·자격증 추천 → 지원 전략 → 장기 취업 경향**까지,
C 영역(홈·스펙비교·취업분석·대시보드)의 화면과 흐름을 **실제 구동 화면 캡처 + 주석 + 플로우차트**로 정리한 스토리보드다.

데모 사용자 `김데모` · 출처 `mock 데모 빌드(VITE_USE_MOCK=true, 백엔드 없이 자체완결)` · 생성 `2026-06-18`

> 📦 **이미지·PPTX·PDF 등 시각 산출물은 이 repo에 두지 않습니다.** 용량 분리를 위해 별도 repo
> [notetester/CareerTunerDocs](https://github.com/notetester/CareerTunerDocs) 에서 관리합니다.
> 이 repo(메인)에는 **소스(`tools/`·`spec/`)와 텍스트 문서**, 그리고 아래 ✅ 정적 HTML 스토리보드만 둡니다. 표의 바이너리/이미지(PNG/PPTX/PDF) 경로는 CareerTunerDocs 기준입니다.

> ✅ **메인 repo에서 바로 보는 스토리보드** → [`output/storyboard-static.html`](output/storyboard-static.html)
> 브라우저로 열면 **실제 화면(렌더된 DOM 스냅샷)** 이 이미지 없이 그대로 나옵니다 — React·서버·이미지 불필요(우리 화면은 전부 코드/SVG라 DOM만 떠서 정적 HTML로 박았고, 앱 CSS 1벌을 열릴 때 각 화면에 주입). 화면 위 점선 박스 = 설명 지점.
> 재생성: `node tools/capture-dom.mjs`(mock 서버 실행 중) → `node tools/render-dom.mjs`.

## 산출물

| 파일 | 용도 |
| --- | --- |
| [output/CareerTuner_C_Storyboard.pptx](output/CareerTuner_C_Storyboard.pptx) | **발표·제출용 슬라이드**(표지 + 여정 + 11프레임) |
| [output/storyboard.pdf](output/storyboard.pdf) | 배포·제출용 PDF |
| [output/storyboard.html](output/storyboard.html) | 웹 뷰(라이브 오버레이 + Mermaid 여정도) |
| [output/storyboard.md](output/storyboard.md) | GitHub·PR용 마크다운(이미지 + Mermaid) |
| [output/C_영역_통합기획서.md](output/C_영역_통합기획서.md) | C 영역 통합 기획서(문제정의·AI기능·데이터·아키텍처) |
| [spec/c-flow.spec.json](spec/c-flow.spec.json) | 진실의 원천 — 프레임·callout 좌표·캡션·여정 |
| `assets/` | 원본 캡처 PNG(웹/앱 × 11화면) |
| `output/annotated/` | 주석(네모박스+번호) 합성 PNG |

## 어떻게 만들었나 (재현 가능한 파이프라인)

수작업 캡처·GUI 조작 **없이**, 데이터(스펙) → 코드 생성으로 전 과정을 자동화했다.

```text
1) 캡처   tools/capture.mjs   헤드리스 브라우저가 mock 앱을 순회 → 웹/앱 PNG (assets/)
2) 스펙   spec/c-flow.spec.json  프레임별 callout 좌표(0~1 정규화)·캡션·C기능·여정
3) 렌더   tools/render.mjs    웹·앱 나란히 + SVG 박스 오버레이 + Mermaid → storyboard.html
   주석   tools/build.py annotate   캡처 위에 박스+번호 배지 합성 → output/annotated/
   슬라이드 tools/build.py pptx      표지·여정·프레임 슬라이드 → .pptx
   문서   tools/md.mjs        주석 이미지 + Mermaid → storyboard.md
   PDF    tools/export.mjs pdf  storyboard.html → storyboard.pdf
```

스펙(좌표·캡션) 한 줄을 고치면 HTML·PPTX·PDF·MD에 동시 반영된다.

### 재생성

```bash
# 1) 데모 mock 서버 실행 (별도 터미널, 백엔드 불필요)
cd frontend && npm run dev:mock          # http://localhost:5173

# 2) 캡처 → 병합 → 렌더 → 빌드
cd docs/storyboard/tools
npm install                              # playwright-core (시스템 Chrome 사용, 브라우저 다운로드 없음)
node capture.mjs                         # assets/*.png
node render.mjs                          # output/storyboard.html
python build.py both                     # output/annotated/*, output/*.pptx  (pip install python-pptx 필요)
node md.mjs                              # output/storyboard.md
node export.mjs pdf                      # output/storyboard.pdf
```

## 화면 인벤토리

**사용자 여정** — 01 홈 대시보드 · 02 취업분석 대시보드 · 03 취업분석·장기경향 · 04 지원건 상세 진입 · 05 적합도 분석+전략+학습추천
**관리자(C 소유)** — 06 관리자 홈 · 07 분석 통계 · 08 적합도 분석 관리 · 09 적합도 프롬프트 운영 · 10 장기분석 프롬프트 운영
**상태 분기** — 11 로그인 필요

> 각 프레임의 주석·캡션·흐름은 PPTX/HTML/MD에서 확인. C 영역 AI 기능(#12 적합도 · #13 부족역량 · #14 학습 · #15 자격증 · #16 장기경향 · #17 다음방향 · #18 대시보드요약)이 어느 화면에 어떻게 구현됐는지 매핑돼 있다.
