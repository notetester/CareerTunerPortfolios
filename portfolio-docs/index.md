---
layout: home
hero:
  name: CareerTuner
  text: 기능 설명서
  tagline: 채용공고에 맞춰 스펙·면접 답변을 조정하는 AI 취업 전략 플랫폼. 도메인별 구현을 코드 기준으로 정리했습니다.
  actions:
    - theme: brand
      text: 프로젝트 개요
      link: /overview
    - theme: alt
      text: 아키텍처
      link: /architecture
    - theme: alt
      text: ▶ 라이브 데모
      link: https://notetester.github.io/CareerTunerPortfolios/
features:
  - title: 지원 건(Application Case) 중심
    details: 공고 분석 → 적합도 비교 → 예상 질문 → 모의면접 → 첨삭까지, 하나의 지원 건이 전 과정을 관통합니다.
    link: /overview
  - title: AI 가상 면접
    details: 세션·꼬리질문·AI 면접관 대화·답변 평가·리포트, RAG(Qdrant)와 음성/영상 비언어 분석까지.
    link: /interview
  - title: 적합도 · 취업 전략
    details: 규칙 엔진 점수 + LLM 설명, 근거 게이트로 환각을 억제하고 자체 파인튜닝 3B 모델을 씁니다.
    link: /fit-analysis
  - title: AI 통합 · 자체 LLM
    details: Claude·OpenAI 폴백 체인과 RTX 4090에서 LoRA 파인튜닝한 자체 모델(Ollama)을 함께 운용합니다.
    link: /ai-integration
  - title: AI 오케스트레이터
    details: 목표를 받아 실행 계획을 동적 생성하고, 의존 그래프로 병렬 실행하며 진행을 SSE로 스트리밍합니다.
    link: /autoprep
  - title: 멀티플랫폼 · 운영
    details: 반응형 웹 + PWA + Capacitor + C++17/Qt/QML 데스크톱, 그리고 사용자 기능마다 대응하는 관리자 화면.
    link: /admin
---

## 이 설명서에 대하여

CareerTuner는 Spring Boot 4 백엔드와 React 19 프런트엔드로 구성된 6인 팀(A~F) 모노레포 프로젝트입니다.
이 설명서는 **실제 소스 코드를 근거로** 도메인별 구현과 설계 결정을 정리한 것으로,
왼쪽 사이드바에서 각 기능 영역을 살펴볼 수 있습니다.

- **라이브 데모**: <https://notetester.github.io/CareerTunerPortfolios/> — 백엔드 없이 브라우저 mock 으로 동작
- **소스 저장소**: <https://github.com/notetester/CareerTunerPortfolios> — 민감정보를 전 이력에서 제거한 공개본

> 이 공개본은 비공개 원본의 전체 커밋 이력을 보존하되, API 키·DB 자격증명·내부 IP 등 민감정보를
> `git filter-repo` 로 모든 이력에서 제거했습니다. 자세한 내용은 [보안 · 공개 전 스크럽](/security) 문서를 참고하세요.
