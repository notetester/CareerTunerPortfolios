import type { CorrectionTab } from "../types/correction";
import { CorrectionPage } from "./CorrectionPage";

function CorrectionSectionPage({ section }: { section: CorrectionTab }) {
  return <CorrectionPage section={section} />;
}

export function AnswerCorrectionPage() {
  return <CorrectionSectionPage section="answer" />;
}

export function CoverLetterCorrectionPage() {
  return <CorrectionSectionPage section="cover" />;
}

export function ResumeCorrectionPage() {
  return <CorrectionSectionPage section="resume" />;
}

export function PortfolioCorrectionPage() {
  return <CorrectionSectionPage section="portfolio" />;
}
