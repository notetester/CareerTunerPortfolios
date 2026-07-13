# AI 자료의 공개 저장소 경계

CareerTuner의 AI 관련 자료는 제품 재현에 필요한 코드와 공개 가능한 설명만 이 저장소에 포함합니다. 개인정보, 운영 정보 또는 원시 출력이 섞일 수 있는 자료는 공개하지 않습니다. 저장소 전체의 기준은 [`PUBLIC_REPOSITORY_BOUNDARIES.md`](../PUBLIC_REPOSITORY_BOUNDARIES.md)를 따릅니다.

## 공개 복제본에 포함하는 자료

| 경로 | 공개 역할 |
| --- | --- |
| `backend/`, `frontend/`, `desktop/`, `ml/` | 제품 실행 코드와 재현 가능한 모델 연결·검증 코드 |
| `ml/career-strategy-llm/scripts/` | 제품과 평가 재현에 필요한 validator, runner, deterministic helper |
| `portfolio-docs/ai-integration.md` | 멀티 provider, 자체 모델, RAG와 폴백 구조 설명 |
| `portfolio-docs/model-evidence.md` | 공개 근거 수준과 확인 가능한 한계 |
| `Obsidian/` | 공개 가능성을 검토한 축약 graph와 Wiki |
| `docs/ai-reports/`, `docs/ai-artifacts/`, `docs/obsidian-vault/` | 제외 범위를 알리는 공개 안내 README |

소형 synthetic fixture와 validator는 기능 재현에 필요하고 실제 사용자 데이터가 없을 때만 제품 경로에 둡니다.

## 공개하지 않는 자료

- 사용자 입력이나 운영 로그를 포함할 수 있는 raw model output과 generated result
- 반복 benchmark의 대용량 artifact와 cache
- 내부 장비, 네트워크, 원격 실행과 자격증명 운용 자료
- 사람 식별 정보가 포함될 수 있는 원본 지식 노트와 첨부 파일
- 공개용으로 다시 검토하지 않은 장문 보고서와 화면 캡처

위 자료를 가리키는 gitlink나 submodule 설정도 공개 tip에는 두지 않습니다. 필요한 설명은 공개 문서에서 사실과 한계를 재서술하며, 비공개 자료의 위치나 초기화 명령을 노출하지 않습니다.

## C career-strategy-llm 기준

- `ml/career-strategy-llm/scripts/`에는 validator, runner와 deterministic helper만 둡니다.
- `ml/career-strategy-llm/reports/`는 짧은 archive index와 호환 안내만 유지합니다.
- raw output, generated result와 반복 실행 artifact를 제품 저장소에 커밋하지 않습니다.
- 공개 문서에서 실험 결과를 설명할 때는 기준 모델, 데이터 범위, 평가 방법, 실패 조건과 확인 가능한 SHA를 함께 기록합니다.
