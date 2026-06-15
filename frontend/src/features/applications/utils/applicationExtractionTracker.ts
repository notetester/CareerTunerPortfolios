import type { ApplicationCaseExtraction } from "../types/applicationCase";
import {
  isApplicationCaseExtractionActive,
  isApplicationCaseExtractionTerminal,
} from "../types/applicationCase";

export const APPLICATION_EXTRACTION_TRACKED_EVENT = "application-case-extraction:tracked";

const STORAGE_KEY = "careertuner.applicationCaseExtractions.tracked";

function canUseWindow(): boolean {
  return typeof window !== "undefined";
}

function readStoredExtractions(): ApplicationCaseExtraction[] {
  if (!canUseWindow()) return [];

  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    if (!value) return [];
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed as ApplicationCaseExtraction[] : [];
  } catch {
    try {
      window.localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Ignore storage cleanup failures.
    }
    return [];
  }
}

function writeStoredExtractions(extractions: ApplicationCaseExtraction[]): void {
  if (!canUseWindow()) return;

  const activeExtractions = Array.from(
    new Map(
      extractions
        .filter((extraction) => isApplicationCaseExtractionActive(extraction.status))
        .map((extraction) => [extraction.id, extraction]),
    ).values(),
  );

  try {
    if (activeExtractions.length === 0) {
      window.localStorage.removeItem(STORAGE_KEY);
      return;
    }

    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(activeExtractions));
  } catch {
    // Tracking is a best-effort bridge; polling from /active still works without storage.
  }
}

export function readTrackedApplicationCaseExtractions(): ApplicationCaseExtraction[] {
  return readStoredExtractions();
}

export function replaceTrackedApplicationCaseExtractions(extractions: ApplicationCaseExtraction[]): void {
  writeStoredExtractions(extractions);
}

export function removeTrackedApplicationCaseExtractions(extractionIds: number[]): void {
  if (extractionIds.length === 0) return;

  const ids = new Set(extractionIds);
  writeStoredExtractions(readStoredExtractions().filter((extraction) => !ids.has(extraction.id)));
}

export function registerApplicationCaseExtraction(extraction: ApplicationCaseExtraction | null | undefined): void {
  if (!extraction) return;

  const next = new Map(readStoredExtractions().map((item) => [item.id, item]));
  if (isApplicationCaseExtractionActive(extraction.status)) {
    next.set(extraction.id, extraction);
  } else if (isApplicationCaseExtractionTerminal(extraction.status)) {
    next.delete(extraction.id);
  }
  writeStoredExtractions(Array.from(next.values()));

  if (canUseWindow()) {
    window.dispatchEvent(new CustomEvent<ApplicationCaseExtraction>(APPLICATION_EXTRACTION_TRACKED_EVENT, {
      detail: extraction,
    }));
  }
}
