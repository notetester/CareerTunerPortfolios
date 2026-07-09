import { Suspense, lazy } from "react";
import { PageFallback } from "./pageFallback";

const LazyPlannerPage = lazy(() =>
  import("@/features/planner/pages/PlannerPage").then((module) => ({ default: module.PlannerPage })),
);

export function PlannerPage() {
  return (
    <Suspense fallback={<PageFallback />}>
      <LazyPlannerPage />
    </Suspense>
  );
}
