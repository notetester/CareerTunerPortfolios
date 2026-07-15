import { PlannerPage, type PlannerTab } from "./PlannerPage";

function PlannerSectionPage({ section }: { section: PlannerTab }) {
  return <PlannerPage section={section} />;
}

export function PlannerSchedulePage() {
  return <PlannerSectionPage section="schedule" />;
}

export function PlannerMemosPage() {
  return <PlannerSectionPage section="memo" />;
}

export function PlannerOverlaysPage() {
  return <PlannerSectionPage section="overlay" />;
}
