# 운영 문서 안내

이 폴더는 반복 가능한 실행·배포·환경 전환·장애 대응 절차를 모으는 위치다. 링크 결합도가 높은
일부 운영 문서는 기존 경로를 유지하며, 모듈에 가까운 문서는 해당 모듈 옆에 둔다.

## 현재 운영 문서 지도

| 주제 | 현재 문서 | 경로 정책 |
| --- | --- | --- |
| AWS EC2·Docker 배포 | [DEPLOY.md](../../DEPLOY.md) | 루트 관례 경로 유지 |
| 데모·웹·Android·iOS 릴리즈 | [RELEASE.md](../RELEASE.md) | 고결합 경로 유지 |
| local·Tailscale·AWS·domain·Sites 환경 | [ENVIRONMENTS.md](../ENVIRONMENTS.md) | 고결합 경로 유지 |
| mock에서 실데이터로 전환 | [real-data-runbook.md](real-data-runbook.md) | 운영 폴더에서 관리 |
| AI 운영 | [ai/](ai/README.md) | AI runbook과 GPU 정책 |
| 브랜치·저장소 운영 | [repository/](repository/README.md) | Git과 저장소 경계 |
| 모바일 패키징 | [frontend/MOBILE_BUILD.md](../../frontend/MOBILE_BUILD.md) | 모듈 인접 경로 유지 |
| 데스크톱 패키징 | [desktop/README.md](../../desktop/README.md) | 모듈 인접 경로 유지 |
| 관리자 권한 DB 운영 | [DB maintenance README](../../backend/src/main/resources/db/maintenance/README.md) | DB 스크립트 인접 경로 유지 |

## runbook 작성 규칙

운영 문서는 다음 항목을 포함한다.

1. 적용 환경과 사전 조건
2. 비밀값을 제외한 필요한 설정 이름
3. 실행 명령과 예상 성공 신호
4. 실패 증상, 진단 순서, 안전한 rollback
5. 검증 명령과 마지막 검증 기준 SHA·날짜
6. 외부 콘솔에서 사람이 해야 하는 단계와 권한

실제 비밀번호, API 키, 토큰, 개인 이메일, 내부 전용 주소는 문서에 쓰지 않는다. 환경별 값의 정본은
환경 설정과 배포 시스템이며, 문서는 변수 이름과 주입 위치만 설명한다.

## 변경 원칙

- 명령을 바꾸면 관련 workflow·script·설정과 같은 PR에서 갱신한다.
- `현재 운영 기본값`과 `선택 가능한 예시`를 분리한다.
- 외부 서비스 화면에 의존하는 절차는 메뉴 경로와 마지막 확인일을 기록한다.
- 일회성 장애 조사와 수행 로그는 runbook 본문에 누적하지 않고 [archive/](../archive/README.md) 또는
  적절한 artifact 저장소로 보낸다.
- 경로 이동은 소비 코드와 검증 파일의 정확한 경로 참조까지 함께 바꾸고 검증한 뒤 수행한다.
