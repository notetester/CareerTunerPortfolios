// 라우트 연결용 얇은 위임 래퍼. 실제 구현은 features/interview 로 이전했다.
// 무거운 의존성(mediapipe/heygen/recharts 등)을 로그인 초기 번들에서 빼기 위해 lazy 로 분리한다
// (모바일 고려 §11.1 화면 단위 코드 분할, Home/Analysis 와 동일 패턴).
import { Suspense, lazy } from "react";
import { PageFallback } from "./pageFallback";

const LazyInterviewPage = lazy(() =>
  import("@/features/interview/pages/InterviewPage").then((module) => ({ default: module.InterviewPage })),
);

export function AIInterviewPage() {
  return (
    <Suspense fallback={<PageFallback />}>
      <LazyInterviewPage />
    </Suspense>
  );
}
