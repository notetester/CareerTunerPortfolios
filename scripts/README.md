# 저장소 공통 스크립트

이 폴더에는 특정 애플리케이션 모듈에 속하지 않는 저장소 수준의 검사 도구만 둔다.

| 경로 | 책임 |
| --- | --- |
| [`docs/`](docs/README.md) | Markdown 링크·앵커·서브모듈 문서 참조 검사 |
| [`verification/`](verification/README.md) | 변경 파일을 시연 준비도 재검증 항목에 매핑 |

백엔드 실행기는 `backend/`, 프런트엔드 빌드·검사 스크립트는 `frontend/scripts/`, ML 재현 도구는 각 `ml/*/scripts/`,
배포 보조 스크립트는 `.github/scripts/`가 소유한다. 실행 로그와 일회성 raw 결과를 이 폴더에 커밋하지 않는다.
