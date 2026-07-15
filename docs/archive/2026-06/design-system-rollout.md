# 디자인 적용 현황 — 작업 기록 (2026-06-18)

> **보관 문서:** 2026-06-18 적용 당시의 커밋 기록이며 현재 구현 상태를 주장하지 않는다.
> 현재 디자인 원칙은 [루트 디자인 스펙](../../../design.md)과 프런트 런타임을 확인한다.
>
> 당시 단일 출처 스펙: [design.md](../../../design.md) · 당시 사용한 개인 UI 가드레일은 저장소 추적 대상이 아니었다.
> 브랜치: `Victor` · **push 안 함**(커밋만). 디자인 커밋은 `4715670` 위 contiguous 블록.

## ✅ 완료 — Linear/Vercel 프리미엄, 다크 기본 + 라이트 토글 (전면 적용)

| 커밋 | 내용 |
| --- | --- |
| `016f431` | 디자인 토큰 2벌(라이트=Vercel / 다크=Linear) + **Wanted Sans** |
| `bf741c4` | 글로벌 헤더 토큰화 (그라데이션 제거, 글래스 내비) |
| `aeeeabf` | UI 프리미티브 폴리시 (카드 엣지/그림자·버튼 하이라이트·포커스 링) |
| `5df7cec` | 페이지 그라데이션 제거 → 솔리드 토큰 (랜딩·대시보드·분석·로그인 등) |
| `31fbec4` | 면접 컴포넌트·공용 CSS 그라데이션 제거 |
| `1c2c819` | **Tailwind 팔레트 브리지** — slate/gray/blue/indigo → 시맨틱 토큰 (페이지 무수정 다크 대응) |
| `dc3fc6e` | `bg-white` → `bg-card` 일괄 (다크 카드 표면) |
| `851580d` | **다크 모드 활성화** (ThemeProvider 기본 다크 + 헤더 라이트/다크 토글) + 의도적 다크 배경 24곳 정리 |

**검증:** `typecheck` = 0, `build` = 0. **앱 전체 그라데이션 0건.**

## 접근 방식 (왜 안전하게 전면 적용이 됐나)
- 핵심은 **팔레트 브리지**: `theme.css`의 `@theme`에서 `--color-slate-*`·`--color-blue-*` 등을 시맨틱 토큰(`--foreground`·`--surface-2`·`--primary`…)에 매핑. → 페이지에 하드코딩된 `text-slate-900`·`bg-slate-50`·`bg-blue-600`가 **파일 수정 없이** 다크/라이트 자동 전환. (검증: 빌드 산출 CSS에서 `.text-slate-900{color:var(--foreground)}` 확인)
- `bg-white`만 브리지 불가(흰색은 `text-white`에 필요) → `bg-card`로 일괄 치환.
- `bg-slate-800/900/950`을 **배경**으로 쓴 24곳(Footer·뱃지·코드블록·오버레이·아바타)은 다크에서 뒤집히므로 개별 정리(`bg-foreground text-background` / `bg-black/N` / 코드블록 고정 다크 등).
- 결과: **팀원 페이지 파일 거의 무수정**(className 직접 변경은 그라데이션·bg-white·다크배경 한정), 색은 theme.css에서 중앙 제어. 되돌리기 쉬움.

## 현재 상태
- **다크 = 기본, 라이트 = 헤더 토글.** 둘 다 LIVE. 선택은 localStorage 저장.
- slate/blue/gray/white 하드코딩은 브리지+치환으로 자동 테마.
- **시각 QA 필요(권장)**: 페이지 토글하며 확인. status 색(green/amber/red)은 의도적 semantic이라 그대로 두었고, 드물게 쓰인 arbitrary 색(teal/purple 등 일부)은 다크에서 어색하면 개별 보정 필요 — 발견 시 알려주면 수정.

## ⚠️ 개발 환경 메모 (디자인 무관)
- dev 서버가 `:5174`로 뜨면 **로그인 403** 발생: 백엔드 CORS가 `:5173`만 허용 → 프록시가 보내는 `Origin: :5174`를 거부. **`:5173`에서 실행/접속**하면 정상. (운영은 동일 출처라 무관.) 백엔드 꺼진 것 아님(403=구동 중).

## 🔙 롤백 / 머지 (design.md §7)
- 디자인 커밋(`016f431`~`851580d`)은 `4715670` 위 contiguous 블록. 다른 작업과 안 섞임.
- 롤백: `git revert <커밋>` (포워드). `dev`에서 `reset --hard`/force-push 금지.
- `Victor`→`dev` 머지는 **squash 금지**(merge-commit/rebase)해야 디자인만 선택 롤백 가능.
