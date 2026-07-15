# 증분 검증 범위 선택기

`select-demo-regression-scope.mjs`는 두 Git 기준점 사이에서 바뀐 파일을
[`docs/verification/demo-readiness-checks.json`](../../docs/verification/demo-readiness-checks.json)에 대조해
A~F·플랫폼·릴리스 검사 항목을 선택한다.

```powershell
node --test scripts/verification/select-demo-regression-scope.test.mjs
node scripts/verification/select-demo-regression-scope.mjs --base <base-sha> --head <head-sha> --strict
```

`--strict`에서 미매핑 파일이 하나라도 발견되면 실패한다. 새 기능 경로를 추가할 때는 기능 체크와 필요한
플랫폼·데이터 생명주기 의존성을 함께 등록한다.

중요: 출력된 목록은 **실행해야 할 검사 범위**다. 선택기는 백엔드 테스트, 프런트엔드 빌드, 모바일·데스크톱
실기 검증을 실행하지 않는다. 실제 실행 결과와 PR·commit SHA는
[전 영역 검증 원장](../../docs/verification/DEMO_READINESS_LEDGER.md)에 별도 증거로 기록한다.
