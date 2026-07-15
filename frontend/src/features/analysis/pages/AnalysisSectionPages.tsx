import { AnalysisPage, type AnalysisTab } from "./AnalysisPage";

function AnalysisSectionPage({ section }: { section: AnalysisTab }) {
  return <AnalysisPage section={section} />;
}

export function AnalysisTrendsPage() {
  return <AnalysisSectionPage section="trend" />;
}

export function AnalysisWeaknessesPage() {
  return <AnalysisSectionPage section="weakness" />;
}

export function AnalysisReadinessPage() {
  return <AnalysisSectionPage section="readiness" />;
}

export function AnalysisScoresPage() {
  return <AnalysisSectionPage section="score" />;
}

export function AnalysisRecommendationsPage() {
  return <AnalysisSectionPage section="recommendation" />;
}
