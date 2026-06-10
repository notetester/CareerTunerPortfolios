import type { CapacitorConfig } from '@capacitor/cli';

// CareerTuner 모바일 앱 패키징 (docs/planning/모바일 고려.md §8 Capacitor 단계).
// webDir = Vite 빌드 산출물(dist). `npm run build` 후 `npx cap sync android` 로 동기화한다.
//
// ⚠ 데이터 연동 메모: 프런트엔드 API 호출은 상대경로 `/api` 라서, 번들된 앱(http(s)://localhost)
//   안에서는 백엔드에 닿지 않는다(BlueStacks→PC localhost 불가). 현재 APK 는 UI/반응형 셸 테스트용.
//   실데이터까지 보려면 도달 가능한 백엔드를 가리켜야 한다. 두 가지 방법:
//   1) 아래 server.url 주석을 풀어 호스팅된 화면을 로드(thin shell). 또는
//   2) API base 를 환경변수로 분리해 PC LAN IP(예: http://192.168.x.x:8080)로 빌드(백엔드 CORS 허용 필요).
const config: CapacitorConfig = {
  appId: 'com.careertuner.app',
  appName: 'CareerTuner',
  webDir: 'dist',
  // server: { url: 'https://notetester.github.io/CareerTunerDemo/', cleartext: false },
  android: {
    // 디버그 APK 사이드로드(BlueStacks) 시 자체서명 사용. 평문 http 백엔드를 쓸 경우에만 허용.
    allowMixedContent: true,
  },
};

export default config;
