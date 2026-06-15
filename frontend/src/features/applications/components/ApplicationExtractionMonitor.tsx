import { useCallback, useEffect, useRef } from "react";
import { useAuth } from "@/app/auth/AuthContext";
import {
  getLatestApplicationCaseExtraction,
  listActiveApplicationCaseExtractions,
} from "../api/applicationCasesApi";
import type { ApplicationCaseExtraction } from "../types/applicationCase";
import {
  isApplicationCaseExtractionActive,
  isApplicationCaseExtractionTerminal,
} from "../types/applicationCase";
import { toast } from "@/features/notification/components/toast";
import { useNotificationStore } from "@/features/notification/hooks/useNotificationStore";
import {
  APPLICATION_EXTRACTION_TRACKED_EVENT,
  readTrackedApplicationCaseExtractions,
  removeTrackedApplicationCaseExtractions,
  replaceTrackedApplicationCaseExtractions,
} from "../utils/applicationExtractionTracker";

const POLL_INTERVAL_MS = 4000;

export function ApplicationExtractionMonitor() {
  const { loading: authLoading, isAuthenticated } = useAuth();
  const fetchNotifications = useNotificationStore((state) => state.fetchNotifications);
  const loadingToastIdRef = useRef<number | null>(null);
  const activeJobsRef = useRef<Map<number, ApplicationCaseExtraction>>(new Map());
  const handledTerminalJobIdsRef = useRef<Set<number>>(new Set());
  const pollingRef = useRef(false);

  const dismissLoadingToast = useCallback(() => {
    if (loadingToastIdRef.current !== null) {
      toast.dismiss(loadingToastIdRef.current);
      loadingToastIdRef.current = null;
    }
  }, []);

  const syncLoadingToast = useCallback((activeCount: number) => {
    if (activeCount > 0 && loadingToastIdRef.current === null) {
      loadingToastIdRef.current = toast.loading(
        activeCount === 1 ? "공고문 추출이 진행 중입니다." : `공고문 추출 ${activeCount}건이 진행 중입니다.`,
      );
      return;
    }

    if (activeCount === 0) {
      dismissLoadingToast();
    }
  }, [dismissLoadingToast]);

  const handleTerminalJob = useCallback(async (job: ApplicationCaseExtraction) => {
    if (handledTerminalJobIdsRef.current.has(job.id)) return;
    handledTerminalJobIdsRef.current.add(job.id);
    removeTrackedApplicationCaseExtractions([job.id]);
    dismissLoadingToast();

    if (job.status === "SUCCEEDED") {
      toast.success("공고문 추출이 완료됐습니다.");
    } else if (job.status === "FAILED") {
      toast.error(job.errorMessage || "공고문 추출에 실패했습니다.");
    }

    await fetchNotifications();
  }, [dismissLoadingToast, fetchNotifications]);

  const poll = useCallback(async () => {
    if (pollingRef.current) return;
    pollingRef.current = true;

    try {
      const activeJobs = await listActiveApplicationCaseExtractions();
      const activeJobsById = new Map<number, ApplicationCaseExtraction>();
      const trackedJobsById = new Map<number, ApplicationCaseExtraction>();

      for (const job of readTrackedApplicationCaseExtractions()) {
        trackedJobsById.set(job.id, job);
      }

      for (const job of activeJobsRef.current.values()) {
        trackedJobsById.set(job.id, job);
      }

      for (const job of activeJobs) {
        if (isApplicationCaseExtractionTerminal(job.status)) {
          await handleTerminalJob(job);
          continue;
        }

        if (isApplicationCaseExtractionActive(job.status)) {
          activeJobsById.set(job.id, job);
          trackedJobsById.set(job.id, job);
        }
      }

      const nextActiveJobs = new Map<number, ApplicationCaseExtraction>(activeJobsById);

      for (const job of trackedJobsById.values()) {
        if (activeJobsById.has(job.id)) continue;

        const latest = await getLatestApplicationCaseExtraction(job.applicationCaseId);
        if (!latest) continue;

        if (isApplicationCaseExtractionTerminal(latest.status)) {
          await handleTerminalJob(latest);
          continue;
        }

        if (isApplicationCaseExtractionActive(latest.status)) {
          nextActiveJobs.set(latest.id, latest);
        }
      }

      activeJobsRef.current = nextActiveJobs;
      replaceTrackedApplicationCaseExtractions(Array.from(nextActiveJobs.values()));
      syncLoadingToast(nextActiveJobs.size);
    } finally {
      pollingRef.current = false;
    }
  }, [handleTerminalJob, syncLoadingToast]);

  useEffect(() => {
    if (authLoading) return;

    if (!isAuthenticated) {
      activeJobsRef.current = new Map();
      handledTerminalJobIdsRef.current = new Set();
      replaceTrackedApplicationCaseExtractions([]);
      dismissLoadingToast();
      return;
    }

    let cancelled = false;
    const run = async () => {
      try {
        if (!cancelled) {
          await poll();
        }
      } catch {
        // Polling failures should not interrupt route rendering or spam toasts.
      }
    };

    void run();
    const handleTrackedExtraction = (event: Event) => {
      const extraction = (event as CustomEvent<ApplicationCaseExtraction>).detail;
      if (!extraction) return;

      if (isApplicationCaseExtractionTerminal(extraction.status)) {
        const nextActiveJobs = new Map(activeJobsRef.current);
        nextActiveJobs.delete(extraction.id);
        activeJobsRef.current = nextActiveJobs;
        void handleTerminalJob(extraction).then(() => {
          void run();
        });
        return;
      }

      if (isApplicationCaseExtractionActive(extraction.status)) {
        activeJobsRef.current = new Map(activeJobsRef.current).set(extraction.id, extraction);
        void run();
      }
    };
    window.addEventListener(APPLICATION_EXTRACTION_TRACKED_EVENT, handleTrackedExtraction);

    const intervalId = window.setInterval(() => {
      void run();
    }, POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.removeEventListener(APPLICATION_EXTRACTION_TRACKED_EVENT, handleTrackedExtraction);
      window.clearInterval(intervalId);
    };
  }, [authLoading, dismissLoadingToast, handleTerminalJob, isAuthenticated, poll]);

  return null;
}
