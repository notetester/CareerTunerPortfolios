# 배포와 장애 대응

CareerTuner는 한 코드베이스를 웹, Android/iOS, Qt 데스크톱에서 사용하지만 배포 산출물과 인증 복귀 경로는 플랫폼마다 다릅니다.

| 채널 | 산출물 | 핵심 검증 |
| --- | --- | --- |
| 웹 | AWS 서비스 + 정적 mock 데모 | API readiness, SPA deep link, OAuth 반환, 다크/라이트·반응형 |
| Android | Capacitor APK/AAB | release HTTPS, cleartext 차단, verified App Link, 서명 |
| iOS | Capacitor/Xcode 빌드 | Associated Domains, AASA, OAuth handoff, 시뮬레이터 빌드 |
| 데스크톱 | C++17 + Qt 6.11 + QML 패키지 | 로그인, 지원 건·면접·플래너·커뮤니티 인계, CTest, 배포 ZIP |

## AWS 우선, mock은 장애 시에만

정상 상황에서는 AWS API와 DB가 항상 우선입니다. Sites/정적 데모는 운영 API를 선점하지 않고 readiness가 실제로 실패했을 때만 독립 mock 체험으로 안내합니다. 복구 판단도 단순 HTTP 200이 아니라 AWS readiness와 DB 상태를 함께 확인합니다.

## 인증 반환 주소

웹 OAuth callback과 네이티브 앱 복귀는 같은 URI를 억지로 공유하지 않습니다. 모바일은 PKCE와 일회성 handoff code를 사용하고, Android verified App Link와 iOS Associated Domains가 허용한 exact path만 앱으로 돌려보냅니다. 운영 provider 콘솔에는 플랫폼별 반환 주소를 각각 등록해야 합니다.

## 공개 데모

Pages 루트는 `VITE_USE_MOCK=true`로 만든 네트워크 독립 데모이고 `/docs/`는 이 설명서입니다. `/Obsidian/`에는 비공개 vault 원문이 아니라 검토된 공개 projection만 배포합니다. mock 데이터는 운영 중 우선 경로가 아니며 실제 사용자 정보·운영 endpoint·자격증명을 포함하지 않습니다.

관련 문서: [플랫폼](./platform.md), [인증](./auth.md), [검증 기준선](./verification.md)
