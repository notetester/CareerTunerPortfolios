# 공개 저장소 경계

이 저장소는 CareerTuner의 구현과 개발 이력을 포트폴리오에서 검토할 수 있도록 만든 공개 복제본입니다. 원본 저장소를 공개로 전환하지 않고 별도의 이력을 구성해, 제품 구조와 기여 기록은 보존하면서 개인정보·자격증명·내부 운영 정보는 공개 경계 밖에 둡니다.

## 포함하는 것

- Spring Boot/MyBatis 백엔드, React 웹, Capacitor 모바일, Qt 데스크톱과 AI/ML 관련 제품 코드
- 백엔드 없이 실행할 수 있는 명시적 mock 데모와 역할별 데모 로그인
- `/docs/`에 배포하는 기능·아키텍처 설명서와 `/Obsidian/`의 공개 검토용 지식 지도
- 허용된 기여자 정보로 정규화하고 민감값을 정화한 도달 가능 커밋 이력, 브랜치, 태그와 보존 ref
- 재현에 필요한 소형 fixture, validator, 테스트와 공개 가능한 설계 문서

## 포함하지 않는 것

- API 키, OAuth secret, 결제·SMS·푸시 자격증명, DB 비밀번호와 운영 환경 변수 값
- 실제 내부 IP·개인 장비 주소, 개인 경로, 운영 로그, 원시 모델 출력과 반복 benchmark 산출물
- 개인정보가 화면에 포함될 수 있는 원본 스토리보드, 관리자 캡처와 내부 작업 메모
- 공개 여부를 별도로 검토해야 하는 대용량 보고서·산출물·지식 원문
- 외부 비공개 자료를 가리키는 submodule URL 또는 gitlink

제외된 네 자료 범주는 `docs/storyboard/`, `docs/ai-reports/`, `docs/ai-artifacts/`, `docs/obsidian-vault/`의 공개 안내 README로 대체합니다. 공개 가능한 요약과 재구성 자료는 `portfolio-docs/`와 `/Obsidian/`에서 확인할 수 있습니다.

## 이력 정화 원칙

공개본은 최신 파일만 복사한 스냅샷이 아닙니다. 원본의 도달 가능한 커밋 그래프를 기준으로 commit mapping과 부모 topology를 검증한 뒤, 알려진 민감값의 exact scan과 일반 패턴 scan을 함께 적용합니다. 텍스트 치환으로 안전하게 정화할 수 없는 로그·cache·개인정보 포함 이미지는 전체 이력에서 제거합니다.

원본과 공개본의 commit SHA가 다른 것은 정상입니다. 검증 가능한 기준 수치와 SHA는 [`PUBLIC_RELEASE_MANIFEST.md`](PUBLIC_RELEASE_MANIFEST.md)에 고정합니다. 구체적인 민감값 목록과 운영 자격증명은 이 저장소에 기록하지 않습니다.

## 실행 경계

GitHub Pages 배포는 `VITE_USE_MOCK=true`인 네트워크 독립 데모를 사용합니다. mock은 운영 장애 시연과 포트폴리오 검토를 위한 명시적 모드이며, 실제 서비스가 정상일 때 운영 API보다 우선하지 않습니다. 실제 OAuth, SMS, 결제, 유료 AI provider의 동작에는 각 운영 환경에서 별도로 주입한 자격증명과 callback 설정이 필요합니다.
