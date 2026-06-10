// 라우트 수준 연결만 유지한다(FEATURE_OWNERSHIP: 성숙한 기능은 features/<기능>로 구현 이전).
// 홈 구현은 features/home/pages/HomePage.tsx(C 담당)에 있다.
// C 페이지는 lazy 로 분리해 모바일 초기 번들을 줄인다(모바일 고려 §11.1 화면 단위 코드 분할).
import { Suspense, lazy } from "react";
import { PageFallback } from "./pageFallback";

const LazyHomePage = lazy(() =>
  import("@/features/home/pages/HomePage").then((module) => ({ default: module.HomePage })),
);

export function HomePage() {
  return (
    <Suspense fallback={<PageFallback />}>
      <LazyHomePage />
    </Suspense>
  );
}
