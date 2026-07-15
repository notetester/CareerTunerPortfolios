# 런타임 보조 데이터

이 폴더는 애플리케이션 재기동 시 사용할 수 있는 작은 비밀 없는 런타임 스냅샷을 둔다.

`block-rule-cache.json`은 보안 차단 규칙의 DB → 메모리 → 파일 캐시 중 파일 계층이다.
[`BlockRuleCacheService`](../backend/src/main/java/com/careertuner/admin/securityops/engine/BlockRuleCacheService.java)가
DB 동기화 시 갱신하며, 파일이 없거나 읽을 수 없으면 DB에서 다시 적재한다.

- 정책 정본은 DB다. 캐시 파일을 손으로 편집해 운영 정책을 바꾸지 않는다.
- 사용자 원문, 인증정보, 비밀값을 캐시에 넣지 않는다.
- 저장소의 빈 규칙 스냅샷은 로컬 최초 기동용 기준값일 뿐 운영 규칙의 최신 상태를 증명하지 않는다.
- 실제 운영에서는 쓰기 가능한 외부 경로를 `security.block.cache.file`로 지정해 배포본과 런타임 상태를 분리한다.
