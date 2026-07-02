# CareerTuner Desktop (C++ / Qt 6)

면접 준비 **컨트롤 센터** 데스크탑 클라이언트. 기존 Spring 백엔드(`/api/**`)를 그대로 소비하며,
무거운 AI 작업은 서버가 하고 이 앱은 화면 + 실시간 모니터링 + 디스패치 + **산출물 로컬 저장**을 담당한다.

> 설계 문서: `../docs/desktop-app-concept.html` · `../docs/desktop-app-v2-mockup.html`(현행 v2 셸) · `../docs/desktop-app-stack.html`

## 요구사항
- **Qt 6.5+** (Quick · Network · Widgets · **Multimedia**)
- **CMake 3.16+**
- **C++17** 컴파일러 (MSVC 2019+ / MinGW / clang)

## 빌드

Qt Creator로 `CMakeLists.txt`를 열거나, CLI:

```bash
cmake -S . -B build -DCMAKE_PREFIX_PATH=<Qt설치경로>/6.x/<kit>
cmake --build build
# 실행: build/CareerTunerDesktop(.exe)  — PATH 에 <Qt>/bin 필요
```

예) Windows + MinGW: `-DCMAKE_PREFIX_PATH=C:/Users/<user>/Qt/6.8.3/mingw_64 -G Ninja -DCMAKE_CXX_COMPILER=g++`

## 구조

```
desktop/
├─ CMakeLists.txt            빌드 레시피 (Qt6 Quick/Network/Widgets/Multimedia)
├─ main.cpp                  진입 · 트레이 상주 · C++ 객체를 QML 에 노출
├─ core/                     ── 엔진(C++) ──
│  ├─ ApiClient              REST + JWT Bearer + multipart + raw 다운로드
│  ├─ AuthService            로그인 · refresh 자동로그인 · 로그아웃
│  ├─ SettingsStore          QSettings 영속화 (토큰·서버주소·저장폴더·자동저장·트레이알림)
│  ├─ JobModel               세션 목록 (사이드바 바인딩, 케이스 라벨 병합)
│  ├─ InterviewSession       선택 세션의 대화 스레드 + 연습 흐름 + 리포트 + 로컬 내보내기
│  ├─ VoiceRecorder          Qt Multimedia 마이크 녹음 (m4a)
│  ├─ NotificationPoller     알림 30초 폴링 → 트레이 토스트
│  ├─ AutoPrepRunner         autoprep 인테이크 + POST-SSE 실행 스트림 파싱
│  ├─ CollaborationClient    친구 요청 · 1:1 채팅/쪽지 · 첨부 업로드/다운로드
│  └─ SseClient              GET-SSE + Last-Event-ID 자동 재연결 (예비)
└─ qml/                      ── 화면(QML · CC Desktop 문법) ──
   ├─ Theme.qml              블랙+인디고 팔레트 싱글톤
   ├─ Main.qml               셸: 사이드바(세션 리스트) + 중앙 뷰 + 하단 입력바 + 접이식 폰 패널
   ├─ SessionThread.qml      질문→답변→채점카드→꼬리질문 대화 타임라인 + agent-steps
   ├─ HomeView.qml           autoprep 한 줄 인테이크 + 되묻기(CASE/MODE) + 라이브 스텝
   ├─ InputBar.qml           답변/인테이크 겸용 입력바 + 음성 녹음 UI
   ├─ ReportView.qml         리포트 + md/HTML 저장 + 세션 자료 일괄 내보내기
   ├─ PhonePanel.qml         접이식 폰 연동 패널 (디스패치)
   ├─ DevicesPage.qml        알림 수신 채널 현황
   ├─ CollaborationPage.qml  친구 검색/요청 + 1:1 채팅/쪽지/첨부
   ├─ SettingsPage.qml       저장폴더·자동저장·자동로그인·서버주소·트레이알림
   ├─ LoginPage.qml          로그인 (+자동로그인 토글)
   └─ NewJobDialog.qml       새 면접 준비 위저드 (지원건 → 모드)
```

## 현재 상태 — v2 (CC Desktop 셸)

- [x] CC Desktop 스타일 셸: 사이드바 = 세션 리스트, 중앙 = 세션 대화 스레드
- [x] 자동 로그인 (QSettings 토큰 + `POST /api/auth/refresh`)
- [x] 면접 연습 실연동: 답변 제출 → 채점 카드 → 꼬리질문 → 모범답안
- [x] 음성 답변: 녹음(m4a) → `voice-transcribe` 전사 → `voice-score` 전달력 채점
- [x] 리포트 실연동 + 로컬 저장 (md/HTML · 세션 자료 일괄 내보내기 · 완료 시 자동 저장)
- [x] 알림 30초 폴링 → 트레이 토스트 (폰→데스크탑 역방향 연동)
- [x] 데스크탑 협업 패널: 친구 요청/수락, 1:1 채팅, 쪽지, 첨부 업로드·다운로드
- [x] autoprep 한 줄 인테이크 + POST-SSE 라이브 실행 스텝
- [x] 폰 디스패치 (접이식 폰 패널) · agent-steps 타임라인
- [ ] (서버측) Job 영속화 `prep_job`/`prep_job_event`/`user_device` — **팀 합의 후** (SSE 재접속·기기 타겟 발송의 전제)

## 백엔드 연결

기본은 팀 공용 원격 백엔드. 설정 화면에서 서버 주소를 바꿀 수 있고 QSettings 에 영속화된다
(로컬 시연: `http://localhost:8080`). 로그인 후 JWT 는 `Authorization: Bearer` 자동 첨부,
자동 로그인 켜면 refresh 토큰으로 재시작 시 재로그인 없이 진입한다.

## 로컬 저장

- 저장 폴더 기본값: `문서\CareerTuner` (설정에서 변경)
- 세션별 하위 폴더에 `리포트.md`/`리포트.html` · 녹음(m4a) · `회사분석.md` · `직무분석.md` 저장
- "완료 시 자동 저장" 켜면 세션의 마지막 답변 채점 후 리포트가 자동 저장된다
