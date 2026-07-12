// 라우트 연결용 얇은 위임 래퍼. 실제 구현은 features/community 로 이전했다.
// 무거운 의존성(tiptap 에디터 등)을 로그인 초기 번들에서 빼기 위해 lazy 로 분리한다
// (Home/Analysis 와 동일 패턴).
import { Suspense, lazy } from "react";
import { PageFallback } from "./pageFallback";

const LazyCommunityPage = lazy(() =>
  import("../../features/community/pages/CommunityHomePage").then((module) => ({
    default: module.CommunityHomePage,
  })),
);

export function CommunityPage() {
  return (
    <Suspense fallback={<PageFallback />}>
      <LazyCommunityPage />
    </Suspense>
  );
}
