import { defineConfig } from 'vitepress'

// 데모(Pages 루트)와 한 저장소를 공유하므로 설명서는 /docs/ 하위에 배포된다.
export default defineConfig({
  base: '/CareerTunerPortfolios/docs/',
  lang: 'ko-KR',
  title: 'CareerTuner 기능 설명서',
  description:
    '채용공고에 맞춰 스펙·면접 답변을 조정하는 AI 취업 전략 플랫폼 CareerTuner의 기능·아키텍처 설명서.',
  cleanUrls: true,
  lastUpdated: true,
  head: [
    ['meta', { name: 'robots', content: 'index, follow' }],
  ],
  themeConfig: {
    nav: [
      { text: '설명서', link: '/overview' },
      { text: '라이브 데모', link: 'https://notetester.github.io/CareerTunerPortfolios/' },
      { text: '기술 학습', link: 'https://notetester.github.io/CareerTunerLearning/' },
      { text: '지식 지도', link: 'https://notetester.github.io/CareerTunerPortfolios/Obsidian/' },
      { text: '소스', link: 'https://github.com/notetester/CareerTunerPortfolios' },
    ],
    sidebar: [
      {
        text: '시작하기',
        items: [
          { text: '설명서 홈', link: '/' },
          { text: '프로젝트 개요', link: '/overview' },
          { text: '아키텍처', link: '/architecture' },
        ],
      },
      {
        text: '사용자 기능',
        items: [
          { text: '인증 · 계정 · 동의', link: '/auth' },
          { text: '프로필 · 스펙', link: '/profile' },
          { text: '지원 건 · 공고 분석', link: '/application-case' },
          { text: '회사 · 직무 분석', link: '/company-job-analysis' },
          { text: '적합도 · 취업 전략', link: '/fit-analysis' },
          { text: 'AI 가상 면접', link: '/interview' },
          { text: '답변 첨삭', link: '/correction' },
          { text: '커뮤니티 · 신고 · 챗봇', link: '/community' },
          { text: '결제 · 크레딧 · 요금제', link: '/billing' },
          { text: '알림 (SSE · Push)', link: '/notification' },
        ],
      },
      {
        text: 'AI 엔진',
        items: [
          { text: 'AI 통합 · 멀티프로바이더 · 자체 LLM', link: '/ai-integration' },
          { text: '모델 증거 매트릭스', link: '/model-evidence' },
          { text: 'AI 오케스트레이터 (자동 준비)', link: '/autoprep' },
        ],
      },
      {
        text: '운영 · 플랫폼',
        items: [
          { text: '관리자 · 운영', link: '/admin' },
          { text: '데스크탑 · 모바일', link: '/platform' },
          { text: '데이터 생명주기', link: '/data-lifecycle' },
          { text: '배포 · 장애 대응', link: '/release-readiness' },
          { text: 'Second Brain · 지식 지도', link: '/second-brain' },
          { text: '검증 기준선', link: '/verification' },
          { text: '보안 · 공개 전 스크럽', link: '/security' },
        ],
      },
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/notetester/CareerTunerPortfolios' },
    ],
    footer: {
      message: '민감정보(API 키·DB 자격증명·내부 IP)는 전체 커밋 이력에서 제거된 포트폴리오 공개본입니다.',
      copyright: 'CareerTuner — 6인 팀 프로젝트 (A~F) · 포트폴리오용',
    },
    outline: { level: [2, 3], label: '이 페이지' },
    docFooter: { prev: '이전', next: '다음' },
    darkModeSwitchLabel: '다크 모드',
    returnToTopLabel: '맨 위로',
    sidebarMenuLabel: '메뉴',
  },
})
