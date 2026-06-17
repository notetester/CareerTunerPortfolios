# 디자인 적용 현황 — 자동 작업 로그 (2026-06-18 야간)

> 단일 출처 스펙: [design.md](design.md) · 집행 가드레일: `.claude/skills/careertuner-ui/SKILL.md`
> 브랜치: `Victor` · **push 안 함**(커밋만). 내 디자인 커밋은 `4715670` 위 contiguous 5블록.

## ✅ 완료 (커밋·빌드 검증됨)

| 커밋 | 내용 |
| --- | --- |
| `016f431` | **디자인 토큰 2벌**(라이트=Vercel / 다크=Linear) + **Wanted Sans**. shadcn 시맨틱 토큰 이름 유지·값만 교체 → 프리미티브·body 자동 적용. 기존 `--gradient-brand`·blue/indigo 잔재 제거 |
| `bf741c4` | **글로벌 헤더** 토큰화 — 그라데이션 로고/유틸바/아바타 제거, 글래스 블러 내비 |
| `aeeeabf` | **UI 프리미티브 폴리시** — Card 엣지/그림자(`--shadow-card`), Button 상단 하이라이트+press 스케일, 포커스 링 3px→2px (Card/Button/Input) |
| `5df7cec` | **페이지 그라데이션 제거** → 솔리드 토큰 (HomePage 랜딩 21, Dashboard·Analysis·Login·Pricing·Billing·Correction·VerifyEmail·ServiceInfo·AdminDashboard·Footer·MobileMoreSheet 28) |
| `31fbec4` | **면접 컴포넌트·공용 CSS** 그라데이션 제거 (PracticeTab·ModeSelectTab·ExpectedQuestionsTab·InterviewProgressBar·AutoSetupPanel 12 + ct-shared.css) |

**검증:** `npm run typecheck` = 0, `npm run build` = 0 (각 단계마다). **앱 전체 `bg-gradient-*`·`bg-clip-text` 0건.**

## 현재 상태
- **라이트 모드 = 기본 + 완성도 높음.** Vercel 프리미엄 톤, 그라데이션·무지개텍스트·AI-tell 제거, 헤더·프리미티브·주요 페이지 토큰화. 시연 가능.
- **다크 모드 = 토큰만 준비, 미활성.** `.dark` 팔레트(Linear)는 정의됐지만, ① 활성화 수단(html.dark 기본 / 테마 토글)이 없고 ② 페이지들이 아직 `slate-*`/`blue-*` 색을 하드코딩해서 다크에서 깨짐. **그래서 다크 기본 전환은 보류.**

## ⛏️ 남은 작업 (깨어서, 시각 QA 하며 — 자동으로 막 하면 위험)

1. **솔리드 brand-blue/indigo → 단일 액센트 수렴**: `bg-blue-600`/`bg-indigo-600`/`text-blue-600/700` 등 **246곳 / 58파일**. 그라데이션은 아니지만 "그 파랑". 크로스팀(전원 페이지). `bg-primary`/`text-primary`로.
2. **`slate-*` 하드코딩 → 시맨틱 토큰**: 다크 coherence의 핵심. `bg-slate-50→bg-background`, `text-slate-900→text-foreground`, `border-slate-200→border-border` 등. **단, `bg-slate-900/950`을 배경으로 쓴 곳**(예: 다크 카드)은 토큰 매핑 시 대비 깨짐 주의 — 한 곳씩 눈으로 확인 필요. 대량·크로스팀.
3. **다크 기본 활성화**: 페이지 de-hardcode 충분히 된 뒤 → `index.html`의 `<html>`에 기본 `dark` 클래스 + 테마 토글(next-themes `ThemeProvider defaultTheme="dark"` 또는 수동 `documentElement.classList` + localStorage). nativeShell이 이미 html.dark를 읽으므로 토글만 붙이면 됨.
4. **잔여 솔리드**: `AutoSetupPanel`의 `bg-indigo-600` 버튼 등 스코프 밖으로 남긴 솔리드 → `bg-primary`.

> **왜 여기서 멈췄나:** 1·2는 246곳+slate 대량으로 **A~F 전원 페이지(크로스팀)**에 걸치고, 시각 확인 없이는 미묘한 대비/레이아웃 깨짐을 잡을 수 없다. 자는 동안 강행하면 팀원 페이지를 망가뜨릴 위험 → 깨어있을 때 함께 진행이 안전.

## 롤백 / 머지 (design.md §7과 동일)
- 내 5커밋(`016f431`~`31fbec4`)은 `4715670` 위 **contiguous 블록**. 다른 작업과 안 섞임.
- 롤백: `git revert <커밋>` (포워드). `dev`에서 `reset --hard`/force-push 금지.
- `Victor`→`dev` 머지는 **squash 금지**(merge-commit/rebase로 커밋 보존)해야 디자인만 선택 롤백 가능.
- 프리뷰 목업은 전부 삭제됨(검증은 실제 빌드/사이트로).
