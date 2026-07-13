# CareerTuner 보안 정책

CareerTuner는 프로필·지원 건·업로드 문서·면접 미디어·AI 결과·결제/크레딧 데이터를 다룹니다. 보안 수정은 현재 개발선과 실제 배포 경로에 연결된 인증, 데이터 소유권, 관리자 권한, 파일, AI provider, 결제 원장을 우선합니다.

## 비공개 신고

취약점의 재현 절차나 실제 비밀값을 공개 Issue·PR·Discussion에 올리지 마세요. 가능하면 GitHub Private vulnerability reporting 또는 Security Advisory를 사용하고, 사용할 수 없다면 저장소 소유자에게 비공개로 전달해 주세요.

다음 정보를 마스킹된 형태로 포함하면 확인에 도움이 됩니다.

- 영향받는 API·화면·workflow·파일 경로
- 필요한 계정 역할과 세부 권한
- 최소 재현 절차와 예상/실제 결과
- 개인정보 노출, 권한 상승, 비용 발생, 데이터 변경, 서비스 중단 등 영향
- 확인한 완화 방법이나 수정 제안

실제 토큰·비밀번호·개인키, 사용자 문서, 면접 녹화물, 결제정보는 신고 본문에 첨부하지 마세요.

## 우선 보호 대상

| 영역 | 주요 경계 |
| --- | --- |
| 인증·계정 | stateless JWT, refresh 회전/폐기, OAuth state·PKCE handoff, BCrypt, 관리자 계정 상태 재검사 |
| 사용자 데이터 | `application_case` 소유권, profile version, 업로드 소유자, 소프트 삭제·복구·고아 방지 |
| 관리자 | `/api/admin/**` 역할 게이트 + 도메인별 READ/CREATE/UPDATE/DELETE + 최고 관리자 guard |
| AI·검색 | 동의 게이트, provider 목적지 고정, 자체 모델·Claude·OpenAI 폴백, RAG/Qdrant, 학습 샘플 |
| 결제·크레딧 | 승인 idempotency, 사용권·차감 원장, 관리자 조회·변경 권한 |
| 배포 | Pages mock 데모, 모바일·Qt 릴리스, GitHub Actions secret, 공개 산출물 검사 |

특히 다음 사례를 우선 신고해 주세요.

- 다른 사용자의 지원 건, 프로필, 파일, 면접, 결제 내역에 접근 가능
- 익명·일반 회원 또는 권한 없는 관리자가 관리자 route/API를 사용 가능
- JWT·refresh·OAuth state·이메일/전화 인증 proof를 재사용하거나 우회 가능
- 파일 경로 탈출, 임의 파일 읽기/쓰기, 위험한 MIME 실행 가능
- MyBatis 쿼리에서 SQL injection 또는 소유권 조건 누락 가능
- prompt injection이나 provider 연동을 통해 토큰·파일·타 사용자 데이터에 접근 가능
- 결제 승인, 크레딧 차감, AI 사용량을 중복·누락·조작 가능
- 저장소 이력, workflow log, 공개 데모에 키·비밀번호·내부 주소가 노출

단순 오타·스타일·반응형 문제, 공개 mock 데이터 표시 오류, 민감정보가 없는 개발 경고는 보안 취약점이 아닐 수 있지만 일반 버그로 제보할 수 있습니다.

## 수정과 검증

확인된 취약점은 공개 재현을 만들기 전에 비공개 브랜치나 Security Advisory에서 수정합니다. 수정은 영향 계층의 회귀 테스트와 함께 검증합니다. 예시는 백엔드 `./gradlew test`, 프런트 `npm run typecheck`와 권한/세션 테스트, 파일·worker 테스트, 배포 산출물 secret scan입니다.

비밀값이 노출되면 파일에서 지우는 것으로 끝내지 않습니다. 키를 폐기·회전하고, 과거 Git 이력, Actions log/artifact, 배포 사이트와 캐시를 함께 확인합니다.

## 이 공개 포트폴리오의 이력 정화

이 저장소는 비공개 팀 저장소를 공개로 전환한 것이 아니라 별도로 만든 공개 미러입니다. 공개 릴리스는 다음 계약을 지킵니다.

- 비공개 원본의 모든 branch·tag 커밋과 PR ref에만 남은 커밋을 공개 archive branch로 보존
- `git filter-repo`로 blob·commit/tag message의 자격증명, 내부 주소, 의도하지 않은 개인정보를 이력 전체에서 치환
- 여섯 팀원의 실명·실제 이메일은 author·committer·trailer에서 정규화해 기여 증거로 유지
- empty/degenerate commit을 가지치기하지 않고, 원본 커밋마다 공개 커밋 하나가 대응하는지 검증
- 변환 후 새 mirror를 만들어 도달 불가능한 원본 object까지 게시 후보에서 제거
- `gitleaks`, exact-value 검사, 패턴 검사, 허용 identity 검사 후에만 공개 ref를 교체

정화 규칙과 원래 비밀값 목록은 비공개 작업 공간에만 둡니다. 공개 tip에서는 비공개 submodule과 내부 운영 문서를 제거하고, 실행 가능한 mock 데모·`/docs/` 설명서·`/Obsidian/` 공개-safe 그래프만 제공합니다.

Pages 데모는 실제 자격증명 없이 브라우저에서 동작하는 시연판입니다. 실제 OAuth·SMS·결제·유료 AI provider의 운영 성공을 의미하지 않습니다. 자세한 구현과 공개 절차는 [보안과 공개 이력 정화](https://notetester.github.io/CareerTunerPortfolio/docs/security)를 참고하세요.
