# 인증 · 계정 · 동의

지원 건, 프로필, 업로드, 면접, 결제는 모두 계정에 귀속됩니다. 인증은 JWT 로그인만이 아니라 계정 생성·복구, OAuth 연결, MFA, 전화 인증, 동의 이력, 웹/모바일/데스크톱 복귀까지 하나의 생명주기로 다룹니다.

## 지원 경로

- 로그인 아이디 또는 이메일 + 비밀번호
- Google, Kakao, Naver OAuth 로그인과 기존 계정 연결·해제
- 이메일 인증, 아이디 찾기, 비밀번호 재설정, 휴면 해제
- TOTP MFA, 백업 코드, 푸시 승인
- Firebase Phone Auth와 provider 미설정 시 OTP 경계
- 웹 callback, Android verified App Link, iOS Associated Domains, Qt 데스크톱 로그인
- 약관·개인정보·AI 데이터·이력서 분석·마케팅 동의 이력

## access JWT와 refresh token

access token은 짧은 수명의 서명 JWT이며 `type=access`를 확인해 refresh/state 토큰 재사용을 막습니다. refresh token은 DB에 기기·접속 정보와 함께 저장하고 재발급할 때 기존 토큰을 폐기한 뒤 회전합니다. 로그아웃 전체, 계정 차단·삭제, 보안 변경은 refresh token을 폐기합니다.

프런트는 저장된 token이 바뀔 때 `/auth/me`로 실제 role을 다시 확인합니다. 이전 로그인 요청의 늦은 응답이 새 계정 세션을 덮지 못하도록 요청 세대를 비교합니다. Qt `AuthService`도 같은 원칙으로 MFA와 자동 로그인을 처리합니다.

## OAuth와 네이티브 handoff

웹은 provider authorization-code callback으로 돌아옵니다. 네이티브 앱은 시스템 브라우저, PKCE verifier, 짧은 수명의 일회성 handoff code를 사용합니다. 앱에 provider token을 URL로 직접 전달하지 않습니다.

- authorization URL은 등록된 공식 HTTPS endpoint만 허용
- Android/iOS callback은 exact path만 앱으로 연결
- 같은 handoff code의 동시 교환은 한 번만 성공
- 네트워크·5xx 실패는 TTL 안 재시도할 수 있지만 성공·취소·세대 변경 뒤 verifier를 정리
- 소셜 로그인과 소셜 계정 연결 callback을 구분

운영하려면 Google/Kakao/Naver 콘솔에 웹과 네이티브 반환 주소, client ID/secret을 각각 등록해야 합니다. 코드에 provider adapter가 있다는 사실과 운영 자격증명이 설정됐다는 사실을 구분합니다.

## MFA와 전화번호 인증

TOTP 등록은 secret과 QR 준비, 확인 코드 검증, 백업 코드 발급을 단계로 나눕니다. 백업 코드는 한 번 사용하면 폐기합니다. 로그인은 비밀번호 성공 뒤 MFA challenge를 완료해야 token을 발급합니다.

전화번호 인증은 Firebase client proof를 서버가 검증하는 경로를 우선하며, SMS provider가 준비되지 않은 환경에서는 해당 기능을 완료로 가장하지 않습니다. 외부 키 발급 대기는 코드 미구현과 별도 상태입니다.

## 동의 게이트

동의는 현재 boolean을 덮어쓰지 않고 버전·시각·철회를 이력으로 남깁니다. AI 분석과 이력서 처리처럼 목적이 다른 기능은 별도 동의를 확인합니다. 소셜 로그인으로 계정만 만들어졌더라도 필수 동의가 없으면 원래 화면으로 보내지 않고 동의 화면을 먼저 거칩니다.

## 관리자 경계

`/api/admin/**`는 익명과 USER에게 닫혀 있습니다. ADMIN도 세부 권한 조회가 성공해야 메뉴와 route를 볼 수 있고, backend가 같은 permission code를 재검증합니다. SUPER_ADMIN은 권한 관리까지 할 수 있습니다. `/admin/policies` 같은 URL을 직접 입력해도 화면 마운트 전에 차단됩니다.

## 계정 삭제와 재활성화

삭제는 soft delete가 원칙이며 활성 조회에서 제외합니다. 같은 식별자로 다시 가입할 때 무관한 중복 사용자를 만들지 않고 정책에 따른 재활성화·복구를 거칩니다. 프로필, 지원 건, 파일, 결제, 커뮤니티 등 연쇄 데이터는 도메인별 보존·삭제 규칙을 적용합니다.

## 공개 데모

mock 데모는 운영 OAuth를 호출하지 않고 일반 사용자와 관리자 persona를 명시적으로 전환합니다. 장애 fallback은 정상 AWS 로그인을 선점하지 않으며, 공개 static demo에서만 관리자 체험을 허용합니다.

관련 문서: [관리자](./admin.md), [플랫폼](./platform.md), [보안](./security.md), [데이터 생명주기](./data-lifecycle.md)
