# CareerTuner Desktop (C++ / Qt 6)

면접 준비 **컨트롤 센터** 데스크탑 클라이언트. 기존 Spring 백엔드(`/api/**`)를 그대로 소비하며,
무거운 AI 작업은 서버(러너)가 하고 이 앱은 화면 + 실시간 모니터링 + 디스패치를 담당한다.

> 설계 문서: `../docs/desktop-app-concept.html` · `../docs/desktop-app-prototype.html` · `../docs/desktop-app-stack.html`

## 요구사항
- **Qt 6.5+** (Quick · Network · Widgets)
- **CMake 3.16+**
- **C++17** 컴파일러 (MSVC 2019+ / MinGW / clang)

## 빌드

Qt Creator로 `CMakeLists.txt`를 열거나, CLI:

```bash
cmake -S . -B build -DCMAKE_PREFIX_PATH=<Qt설치경로>/6.x/<kit>
cmake --build build
# 실행: build/CareerTunerDesktop(.exe)
```

예) Windows + MSVC: `-DCMAKE_PREFIX_PATH=C:/Qt/6.7.0/msvc2019_64`

## 구조

```
desktop/
├─ CMakeLists.txt        빌드 레시피 (Qt6 Quick/Network/Widgets)
├─ main.cpp              진입 · 트레이 상주 · C++ 객체를 QML에 노출
├─ core/                 ── 엔진(C++) ──
│  ├─ ApiClient          REST 호출 + JWT Bearer + ApiResponse envelope 파싱
│  ├─ SseClient          실시간 스트림 + Last-Event-ID 자동 재연결 ★핵심
│  ├─ AuthService        POST /api/auth/login → accessToken 보관
│  └─ JobModel           작업 목록 (QAbstractListModel, QML 바인딩)
└─ qml/                  ── 화면(QML) ──
   ├─ Main.qml           셸: 사이드바 + 화면 스택
   └─ DashboardPage.qml  작업 카드 목록 (jobModel 바인딩)
```

C++(엔진)와 QML(화면)을 분리한다 — `main.cpp`가 `api`/`auth`/`sse`/`jobModel`을
context property로 QML에 노출하고, QML은 그걸 바인딩해 그린다.

## 현재 상태 — STEP 1 골격

- [x] 프로젝트 골격 · 다크 테마 셸(사이드바 + 화면 스택)
- [x] 대시보드 + 작업 카드(시드 데이터로 화면 확인)
- [x] `SseClient` — 끊겨도 Last-Event-ID로 이어받는 자동 재연결
- [x] `ApiClient` / `AuthService` — JWT 로그인 흐름
- [ ] 로그인 화면 · 실제 서버 연동(작업 목록/SSE 구독)
- [ ] 작업 상세(의존그래프) · 면접 연습 · 리포트 화면
- [ ] 트레이 아이콘 리소스
- [ ] (서버측) Job 영속화 API `prep_job`/`prep_job_event`/`user_device` — **팀 합의 후**

## 백엔드 연결

기본 `http://localhost:8080`. 로그인 후 받은 JWT로 이후 요청에 `Authorization: Bearer` 자동 첨부.
원격(Tailscale) 백엔드를 쓰려면 `ApiClient::setBaseUrl()` 로 주소만 바꾸면 된다.
