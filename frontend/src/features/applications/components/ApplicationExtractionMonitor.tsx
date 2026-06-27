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
// [임시 방어] 백엔드가 stuck 추출 job 을 계속 active 로 반환하면(근본 원인은 B 도메인의
// 추출 파이프라인/데이터) 진행 토스트가 영영 안 닫혀 앱 화면/터치를 막는다. 이 시간을 넘겨
// "진행 중" 이 지속되면 stuck 으로 보고 토스트를 닫고 억제한다. 정상 추출 상한
// (JOB_POSTING_AI_WORKER_TIMEOUT≈120s)보다 길게 둬 정상 흐름은 건드리지 않는다.
const STUCK_LOADING_TIMEOUT_MS = 180000;

export function ApplicationExtractionMonitor() {
  const { loading: authLoading, isAuthenticated } = useAuth();
  const fetchNotifications = useNotificationStore((state) => state.fetchNotifications);
  const loadingToastIdRef = useRef<number | null>(null);
  const loadingShownAtRef = useRef(0);
  const loadingSuppressedRef = useRef(false);
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
    if (activeCount === 0) {
      loadingSuppressedRef.current = false;
      dismissLoadingToast();
      return;
    }

    // stuck 으로 판정돼 억제 중이면 더 띄우지 않는다(active 가 0 이 되면 위에서 해제).
    if (loadingSuppressedRef.current) return;

    if (loadingToastIdRef.current === null) {
      loadingToastIdRef.current = toast.loading(
        activeCount === 1 ? "공고문 추출이 진행 중입니다." : `공고문 추출 ${activeCount}건이 진행 중입니다.`,
      );
      loadingShownAtRef.current = Date.now();
      return;
    }

    // 이미 떠 있는데 정상 상한을 넘겨 계속 active → stuck 으로 보고 닫고 억제한다.
    if (Date.now() - loadingShownAtRef.current > STUCK_LOADING_TIMEOUT_MS) {
      dismissLoadingToast();
      loadingSuppressedRef.current = true;
    }
  }, [dismissLoadingToast]);

  const handleTerminalJob = useCallback(async (job: ApplicationCaseExtraction) => {
    if (handledTerminalJobIdsRef.current.has(job.id)) return;
    handledTerminalJobIdsRef.current.add(job.id);
    removeTrackedApplicationCaseExtractions([job.id]);
    dismissLoadingToast();

    if (job.status === "SUCCEEDED") {
      if (job.qualityStatus === "REVIEW_REQUIRED") {
        toast.warning("공고문 추출 결과 검수가 필요합니다.");
      } else {
        toast.success("공고문 추출이 완료됐습니다.");
      }
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
