# 프로토타입 시안 (팀 공유용)

> ✅ **AI 오케스트레이터는 실제 풀스택 구현 완료**(2026-06-23, `backend/.../ai/autoprep` + `frontend/src/features/autoprep`).
> 아래 시안은 그 과정의 디자인 산출물이다. 구현은 메인 홈(MemberHome)에 임베드돼 있고, 동적 plan·의존 그래프 병렬·멀티턴 채팅 인테이크·SSE 진행까지 동작한다.

정적 시안 모음. 더블클릭하면 브라우저로 열린다(단일 파일, 외부 의존은 폰트 CDN 하나뿐).

- **landing.html** — **AI 오케스트레이터** 랜딩 페이지 시안.
  - 다크 Linear/Vercel 톤(기존 `frontend/src/styles/theme.css` 토큰 기반).
  - "시작하기" 클릭 → **기존 라이트 SaaS(회원 대시보드)** 화면으로 전환.
  - 히어로 입력창에서 엔터/예시 칩 → 6단계 파이프 데모 애니메이션.
- **orchestrator-flow.svg** — AI 오케스트레이터 6파트 풀파이프 흐름도
  (인테이크 챗봇 → 두뇌(Plan) → 오케스트레이터 → ① A 프로필 · ② B 공고 · ③ C 적합도 · ④ E 자소서 · ⑤ D 면접 · ⑥ F 커뮤니티).
- **orchestrator-screen.html** — 작업 과정 화면 시안(메인 홈 임베드 + 작업 과정 팝업 + 6파트 세부스텝 에너지바 + 파일 첨부). **✅ 실제 구현됨** → `AutoPrepWorkView`.
- **orchestrator-chat.html** — 채팅 팝업 시안(입력 즉시 팝업 → 멀티턴 대화로 지원 건·모드 채움 → 작업 과정). **✅ 실제 구현됨** → `AutoPrepChatModal`.

> **디자인 기준**: 토큰은 `frontend/src/styles/theme.css`가 진실. 랜딩=다크 Linear(`#08090a`/카드 `#141517`/인디고 `#5e6ad2→#7170ff` 135°/투명 보더), SaaS 본체=라이트(`#fafafa`). 별도 디자인 md를 새로 만들지 않는다.
>
> **실제 통합 시**: 랜딩은 신규 컴포넌트로, "시작하기"는 기존 라우팅(`/login`→`/dashboard`)으로 연결. SaaS 화면은 기존 `HomePage`(MemberHome) 그대로 쓴다(이 시안의 SaaS는 구조 재현용 목업).
