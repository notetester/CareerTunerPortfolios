# CareerTuner 디자인 시스템 — Linear/Vercel 프리미엄 (다크 기본 + 라이트 토글)

> 방향: Linear·Vercel 계열의 **프리미엄 다크 우선** UI. 빽빽한 제품(SaaS)에 어울리고, "AI가 자동 생성한 티"가 안 나는 룩.
> 모드: **다크 기본 + 라이트 토글, 둘 다 정식 지원.** (기본을 라이트로 바꾸는 건 `<html>`/`<body>` 기본 클래스 하나 차이.)
> 스택: Tailwind v4(CSS-first `@theme`) + React 19 + shadcn/ui(Radix + CVA). 폰트는 **Wanted Sans**(Pretendard 폴백).
> 출처: Linear 디자인 시스템 토큰, Vercel/Geist, 프리미엄 다크 UI 원칙.

---

## 0. 배경 — 왜 이 방향인가 (의사결정 로그)

처음엔 강사 요청대로 **Apple(라이트 미니멀)** 을 시도했다. 그러나:
- 우리 제품은 정보가 빽빽한 **도구**(지원 건 관리, 8탭 상세, 대시보드)다. apple.com식 "텅 빈 마케팅 미니멀"은 이런 밀도에 안 맞는다.
- 순수 재스킨(색만 교체)으로는 Apple 느낌이 안 났다. **느낌은 색이 아니라 깊이·여백·타이포·단일 액센트에서 나온다.**

그래서 "프리미엄 + AI 안 같음"을 밀도 높은 제품에서 실제로 잘 뽑는 **Linear/Vercel 계열**로 방향을 확정했다. 강사가 말한 "Apple"의 진짜 의도(= 비싸 보이고 깔끔)는 이 방향으로 충족한다.

> **교훈(다시 실수 금지):** "다크로 바꾸기" = 흑백으로 칠하기가 **아니다.** 프리미엄은 ① 얇은 보더+엣지(다크)/부드러운 그림자(라이트)로 만드는 깊이, ② 넉넉한 여백, ③ 큰·타이트한 타이포, ④ 절제된 단일 액센트, ⑤ 은은한 모션 — 이 다섯에서 나온다.

---

## 1. 원칙

1. **깊이는 장식이 아니라 빛으로.** 다크: 1px 저알파 보더 + 상단 라이트 엣지. 라이트: 헤어라인 + 부드러운 레이어 그림자. 컬러 그림자·과한 drop-shadow 금지.
2. **단일 액센트, 극도로 절제.** 인디고/바이올렛 한 계열만. CTA·활성·링크·작은 하이라이트에만. 면적의 95%는 무채색.
3. **큰 타이포 + 타이트 트래킹.** 위계는 굵기 떡칠이 아니라 크기·여백 대비로.
4. **여백이 곧 프리미엄.** 섹션은 크게, 요소는 촘촘하되 정렬은 칼같이.
5. **은은한 모션.** 빠르고 조용하게(150~250ms). 통통 튐·네온 펄스 금지.
6. **두 모드 모두 1급 시민.** 색은 전부 시맨틱 토큰으로. 컴포넌트에 hex/`slate-*` 하드코딩 금지 → 토큰만 바꾸면 라이트↔다크 동시 대응.

---

## 2. "AI티" 안티패턴 — 금지 목록

- ❌ **무지개 대각선 그라데이션**(`135deg` 보라→시안 등). 배경·버튼·텍스트 전부 금지.
- ❌ **무지개 그라데이션 텍스트**(`bg-clip-text`로 파랑→시안). — 단, **모노 그라데이션**(흰→회색 / 잉크→회색)은 허용(§4, Linear 시그니처).
- ❌ **제목·버튼에 이모지** → 모노 아이콘으로.
- ❌ **컬러 그림자·`shadow-2xl` 떡칠** → 깊이는 보더/엣지/부드러운 그림자로.
- ❌ **액센트를 면 전체에 처바르기.** 인디고를 그라데이션으로 깔거나 버튼 5개를 다 액센트로 → 금지. **다크에서 절제해 쓰면 Linear, 라이트에 그라데이션으로 깔면 AI슬롭.** 차이는 "절제".
- ❌ **shadcn 디폴트 잔재**(`#030213`, `--gradient-brand`, 두꺼운 3px 포커스 링).
- ❌ **페이지에 색 하드코딩**(`slate-50`/`blue-600`/`from-… to-…`). 전부 시맨틱 토큰 경유.
- ❌ **가짜 후기(랜덤 아바타)·근거 없는 통계 남발.**
- ❌ **순검정 `#000` / 순백 위주.** 다크는 `#08090a`, 라이트 잉크는 `#16171a`(순검정 아님).

---

## 3. 컬러 — 시맨틱 토큰 (다크 + 라이트 2벌)

모든 색은 시맨틱 토큰으로 정의하고, 컴포넌트는 토큰만 참조한다.

### 3.1 다크 (기본) — Linear 계열

```css
:root, .dark {
  --bg:#08090a;            /* 캔버스(히어로 등 최심부) */
  --bg-panel:#0d0e10;      /* 사이드바·유틸바·패널 */
  --surface:#141517;       /* 카드 */
  --surface-2:#1a1b1e;     /* 카드 내부·호버 */
  --surface-3:#202125;     /* 활성 탭·세그먼트 */
  --border:rgba(255,255,255,.08);
  --border-strong:rgba(255,255,255,.14);
  --edge:rgba(255,255,255,.06);     /* 상단 라이트 엣지(깊이) */
  --text:#f7f8f8;          /* 제목·기본 */
  --text-2:#c2c7d0;        /* 본문 */
  --text-3:#8a8f98;        /* 보조 */
  --text-4:#62666d;        /* 캡션·disabled */
  --accent:#5e6ad2;        /* CTA 배경 */
  --accent-2:#7170ff;      /* 링크·활성·하이라이트 */
  --accent-hover:#828fff;
  --accent-soft:rgba(113,112,255,.14);
  --success:#3fb950; --warning:#d29922; --danger:#f85149;
}
```

### 3.2 라이트 (토글) — Vercel 계열

```css
.light {
  --bg:#fafafa;
  --bg-panel:#f5f5f7;
  --surface:#ffffff;
  --surface-2:#f6f6f8;
  --surface-3:#ececed;
  --border:#e7e7eb;
  --border-strong:#d6d6dc;
  --edge:transparent;               /* 라이트는 엣지 대신 그림자 */
  --text:#16171a;          /* 순검정 아님 */
  --text-2:#3f4046;
  --text-3:#6b6c76;
  --text-4:#9a9ba4;
  --accent:#5e6ad2;
  --accent-2:#5046c8;      /* 라이트는 링크용으로 더 진하게(대비) */
  --accent-hover:#4a40c0;
  --accent-soft:rgba(94,106,210,.10);
  --success:#1a7f37; --warning:#9a6700; --danger:#cf222e;
}
```

### 3.3 엘리베이션 토큰 (깊이) — 모드별

```css
:root, .dark {
  --shadow-card: inset 0 1px 0 var(--edge);                          /* 상단 라이트 엣지 */
  --shadow-pop:  inset 0 1px 0 var(--edge), 0 30px 80px -40px rgba(0,0,0,.9);
}
.light {
  --shadow-card: 0 1px 2px rgba(17,17,26,.05), 0 4px 14px -4px rgba(17,17,26,.06);
  --shadow-pop:  0 1px 2px rgba(17,17,26,.04), 0 28px 60px -28px rgba(17,17,26,.18);
}
```

### 3.4 Tailwind v4 `@theme` 매핑

`theme.css`의 토큰을 위 값으로 교체하고, `@theme inline`에 노출한다. 기존 `--gradient-brand*`, `--brand-blue/indigo`, `#030213`는 **삭제**.

```css
@theme inline {
  --color-bg: var(--bg);            --color-bg-panel: var(--bg-panel);
  --color-surface: var(--surface);  --color-surface-2: var(--surface-2);  --color-surface-3: var(--surface-3);
  --color-border: var(--border);    --color-border-strong: var(--border-strong);
  --color-ink: var(--text);         --color-ink-2: var(--text-2);  --color-ink-3: var(--text-3);  --color-ink-4: var(--text-4);
  --color-accent: var(--accent);    --color-accent-2: var(--accent-2);
}
```
→ `bg-surface`, `text-ink`, `text-ink-3`, `border-border`, `bg-accent` 등으로 사용. 다크/라이트는 `.dark`/`.light` 클래스로 자동 전환.

### 3.5 액센트 사용 규칙
- 한 화면에서 액센트(인디고/바이올렛)는 **가장 중요한 행동 1~2개 + 활성 상태 + 링크**에만.
- 그라데이션 채움 금지(모노 텍스트 그라데이션·진행바 제외).
- 상태색(성공/경고/위험)은 **상태 표시에만**, 장식 금지.

---

## 4. 타이포그래피

- **폰트: Wanted Sans**(모던·중립, 다크/라이트 모두 깔끔). Pretendard는 폴백으로 둠.
  - CDN: `https://cdn.jsdelivr.net/gh/wanteddev/wanted-sans@v1.0.1/packages/wanted-sans/fonts/webfonts/variable/split/WantedSansVariable.min.css` · family `"Wanted Sans Variable"`
  - `--font-sans: "Wanted Sans Variable", "Pretendard Variable", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`
- **헤드라인:** 크게, weight **600**, 음수 자간(라틴/대형 -0.025~-0.03em). 한글 본문은 자간 0~-0.01em(과하면 답답).
- **모노 텍스트 그라데이션(허용):** 대형 헤드라인에 다크 `linear-gradient(180deg,#fff,#b3b8c4)` / 라이트 `…(#16171a,#52535c)`. 무지개 금지, 같은 명도축만.

| 토큰 | size | weight | line-height | tracking |
| --- | --- | --- | --- | --- |
| display | clamp(2.4rem,4.6vw,4rem) | 600 | 1.07 | -0.028em |
| title | clamp(1.8rem,3.2vw,2.6rem) | 600 | 1.1 | -0.022em |
| h3 | 1.0625rem(17) | 600 | 1.3 | -0.01em |
| body | 0.9375rem(15) | 400 | 1.55 | -0.011em |
| small | 0.8125rem(13) | 400~500 | 1.5 | 0 |

---

## 5. 깊이 · 보더 · 모션

### 5.1 깊이(핵심)
- **다크:** 카드/탭/버튼 = `1px var(--border)` + `inset 0 1px 0 var(--edge)`(상단 라이트 엣지). 호버 시 보더를 `--border-strong`로 밝히고 `translateY(-2px)`.
- **라이트:** `1px var(--border)`(헤어라인) + 부드러운 레이어 그림자(`--shadow-card`). 호버 시 그림자/보더 강화.
- 떠 있는 요소(목업·모달·팝오버)는 `--shadow-pop`(중립, 컬러 없음).
- **히어로 오로라:** 다크는 accent 계열 radial 스팟라이트 2개 + `blur(90px)`, opacity~.55. 라이트는 동일 구조 opacity~.2(은은한 틴트). 미세 그리드 페이드 오버레이 허용.

### 5.2 Radius (컴팩트)
```css
--radius-sm:8px;   /* 버튼·인풋·탭 */
--radius:12px;     /* 카드 */
--radius-lg:16px;  /* 큰 패널 */
--radius-pill:980px; /* 배지·세그먼트 */
```

### 5.3 모션
```css
--ease:cubic-bezier(.4,0,.2,1);
/* 호버/전이 150~200ms, 스크롤 진입 fade+translateY(14px) 600ms. prefers-reduced-motion 존중. */
```

---

## 6. 컴포넌트 스펙 (shadcn `ui/*` className 재정의)

- **Button**
  - primary: `bg-accent text-white` + `box-shadow: inset 0 1px 0 rgba(255,255,255,.22), 0 1px 2px rgba(0,0,0,.35)`(라이트는 그림자 약하게). hover `--accent-hover`, active `scale(.985)`.
  - ghost: `bg-[var(--ghost-bg)] text-ink border border-border`, hover 강화.
  - 한 화면 primary 1개 원칙.
- **Card:** `bg-surface border border-border rounded-[12px] shadow-[var(--shadow-card)]`. 패딩 20~24px.
- **Input:** `bg-surface border border-border rounded-[8px]`, 높이 40~44px, focus = 보더 `--accent` + `ring 0 0 0 3px var(--accent-soft)`. **3px 두꺼운 shadcn 링 제거.**
- **Tabs(상세):** 활성 = `bg-surface-3 text-ink shadow-[var(--shadow-card)]`(다크 `slate-900` 같은 거 금지), 비활성 = `text-ink-3`.
- **글로벌 내비:** `bg-[var(--chrome)] backdrop-blur(18px) saturate(160%)` + 하단 `border-border`. 로고 그라데이션 텍스트 제거 → 솔리드.
- **Badge/pill:** `rounded-pill bg-surface-2 text-ink-3 border border-border`. 상태는 작은 컬러 도트로.
- **사이드바 항목:** 활성 = `bg-accent-soft text-accent-2`(+얇은 액센트 보더). 라벨+아이콘 항상 동반(affordance).

> **앱 UI affordance 규칙(중요):** 절제는 색·장식에만. **"뭐가 클릭 가능한지"는 절대 비우지 않는다** — 중요 행동은 채워진 버튼, 클릭 가능한 행은 호버+셰브론+커서, 아이콘엔 항상 텍스트 라벨, 네비는 항상 보이고 현재 위치 강조.

---

## 7. 구현 매핑 + ⚠️ 적용 보류

> ⚠️ **현재 다른 작업자가 동시 작업 중. 코드 적용은 보류한다.** 본 문서·`SKILL.md`만 먼저 확정. `theme.css`·`ui/*`·페이지 수정은 팀 순서/합의 후 진행.

| 변경 | 파일 | 비고 |
| --- | --- | --- |
| 토큰 2벌(다크/라이트)·그라데이션 삭제 | `frontend/src/styles/theme.css` | 공통 영역 |
| 프리미티브 겉모습 재정의 | `frontend/src/app/components/ui/*.tsx` | 구조·API 유지 |
| Wanted Sans 웹폰트 추가 + `--font-sans` 교체 | `frontend/src/styles/fonts.css`, `theme.css` | Pretendard 폴백 유지 |
| **페이지 색 하드코딩 → 토큰 치환** | `app/pages/*`, `features/*/pages/*` | `slate-*`/`blue-*`/`from-…` 제거 |
| 모드 토글(다크 기본) + 저장 | 테마 프로바이더 | `<html>`에 기본 `dark`, 토글 시 `light`, localStorage |

**핵심:** 어차피 새 룩 입히려면 페이지의 하드코딩 색을 다 건드려야 한다. 그때 새 하드코딩 대신 **시맨틱 토큰으로 빼면 라이트/다크가 거의 같은 작업량에 딸려온다.** "둘 다 지원"은 추가 비용이 아니라 같은 작업의 결과.

### 우선순위
1. 토큰 레이어(theme.css) — 다크/라이트 2벌. (보류 중)
2. 프리미티브(Button/Card/Input/Tabs) 겉모습.
3. 페이지별 하드코딩 색 → 토큰 치환 + 깊이/여백/타이포 정리.

### 롤백/머지 안전 (전면 롤백 대비) — 단일 `Victor` 브랜치 기준
팀장이 자는 동안 `Victor`→`dev` 머지를 못 해주므로, 깨끗한 `dev`에서 전용 브랜치를 따는 건 보류. 다른 클로드도 `Victor`에서 작업 중이라 **단일 `Victor`**를 공유한다. 이 전제에서 디자인이 깨끗하게 롤백되려면:

- **디자인은 맨 마지막에, 단일 커밋(또는 디자인만의 contiguous block)으로.** 다른 클로드 작업이 전부 끝나 커밋된 뒤 그 **위에** 올린다 → 디자인이 히스토리 맨 위. 중간에 안 섞이게.
  - **같은 클론**(한 폴더에서 클로드 둘): 걔 커밋이 이미 로컬에 있으니 pull 불필요, 걔가 끝난 뒤 이어서 커밋만. 단 동시 편집 금지(직렬화).
  - **다른 클론**(2대 머신 → `origin/Victor`): 걔 push 후 `git pull origin Victor`로 받고 그 위에 올린다.
- **`Victor`→`dev` 머지는 squash 금지 (결정적).** squash하면 다른 작업+디자인이 한 커밋으로 뭉개져 디자인만 못 뺀다. **merge commit(`--no-ff`) 또는 rebase 머지**로 커밋을 살려야 디자인 커밋이 dev에 따로 남는다.
- **롤백 방법:**
  - PR 머지 전(팀장이 열린 PR 검토) → 디자인이 맨 위라 그 커밋만 떼고 push. 가장 쉬움.
  - 이미 머지됨 → `git revert <디자인 커밋>`(포워드, 맨 위라 충돌 없음). **`dev`에서 `reset --hard`/force-push 금지.**
- **유일한 함정:** 디자인 커밋 위에 (de-hardcode가 건드린) 같은 페이지를 또 고친 커밋이 쌓이면 revert 충돌. 디자인 머지 후 위에 아무것도 안 쌓인 상태에서 팀장이 빨리 보면 깨끗.
- 레포가 **squash-only**(머지 방식 강제)면 위가 성립 안 됨 → 그땐 디자인을 별도 PR로 분리(다른 작업 `dev` 머지 후 진행).

---

## 8. 모드 정책
- **기본: 다크 — 다크가 이 디자인의 본체(canonical)다.** 글로우·상단 라이트 엣지·`#08090a` 같은 깊이 기법은 "빛을 어둠 위에 올리는" 다크 네이티브라, 정체성이 다크에서 가장 산다. "AI가 못 뽑는" 신호도 다크에서 제일 강하다. → **다크를 가장 먼저·가장 빡세게 폴리시하고, 룩의 기준으로 삼는다.**
- **라이트는 1급 곁(companion)이지 곁다리가 아니다.** 강사의 라이트(Apple) 기대 + 긴 읽기 + 프로젝터 시연 때문에 라이트도 똑같이 프리미엄하게 유지한다(다크의 글로우/엣지 대신 헤어라인 + 부드러운 그림자 레시피).
- 라이트는 헤더 토글로 전환, 선택은 localStorage 저장. 기본 모드 전환은 `<html>` 기본 클래스 하나만 바꾸면 됨(구조 변경 아님).

---

## 9. 참고 소스
- Linear 디자인 시스템 — 다크 토큰(`#08090a` 계열, accent `#5e6ad2`/`#7170ff`)
- Vercel/Geist — 라이트 레시피(`#fafafa`/`#16171a`, 헤어라인, 컴팩트 radius, 타이트 트래킹)
- 프리미엄 다크 원칙 — 얇은 보더 `rgba(255,255,255,.08)`, 미세 글로우, 정밀한 모션
- 프리뷰 파일: `design-premium-modes.html`(라이트/다크·메인/상세 토글)
