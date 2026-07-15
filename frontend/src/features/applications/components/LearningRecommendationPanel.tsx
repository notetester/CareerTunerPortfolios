import { useEffect, useState } from "react";
import { Link } from "react-router";
import { AlertTriangle, Award, BookOpen, CalendarCheck, CalendarPlus, CheckCircle2, Circle, GraduationCap, Hammer, RefreshCw } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import { getCareerCertificateStrategy, updateFitAnalysisLearningTask } from "@/features/analysis/api/fitAnalysisApi";
import { createPlannerScheduleItem } from "@/features/planner/api/plannerApi";
import type { PlannerReminderChannel } from "@/features/planner/types/planner";
import { toast } from "@/features/notification/components/toast";
import type {
  CareerCertificateStrategy,
  CertificateEvidenceSnapshot,
  FitAnalysisDetail,
  FitAnalysisLearningTask,
  FitCertificateRecommendation,
  FitGapRecommendation,
} from "@/features/analysis/types/fitAnalysis";
import { parseJsonList, parseJsonValue } from "@/features/analysis/types/fitAnalysis";
import { AiChargeCostBadge } from "@/features/billing/components/AiChargeCostBadge";

interface LearningRecommendationPanelProps {
  analyses: FitAnalysisDetail[];
  loading: boolean;
  error: string | null;
  /** 학습 80% 이상 완료 시 재분석 유도 버튼에 연결. 미전달이면 안내 문구만 표시한다. */
  onReanalyze?: () => void;
  reanalyzing?: boolean;
  /** 집계 페이지처럼 상위에서 이미 제목을 그릴 때 패널 자체 제목을 숨긴다. */
  hideHeading?: boolean;
}

export function LearningRecommendationPanel({ analyses, loading, error, onReanalyze, reanalyzing = false, hideHeading = false }: LearningRecommendationPanelProps) {
  const [tasks, setTasks] = useState<Record<number, FitAnalysisLearningTask[]>>({});
  const [updatingTaskId, setUpdatingTaskId] = useState<number | null>(null);
  const [taskError, setTaskError] = useState<string | null>(null);

  useEffect(() => {
    setTasks(Object.fromEntries(analyses.map((analysis) => [analysis.id, analysis.learningTasks ?? []])));
  }, [analyses]);

  async function toggleTask(task: FitAnalysisLearningTask) {
    setUpdatingTaskId(task.id);
    setTaskError(null);
    try {
      const updated = await updateFitAnalysisLearningTask(task.fitAnalysisId, task.id, !task.completed);
      setTasks((current) => ({
        ...current,
        [task.fitAnalysisId]: (current[task.fitAnalysisId] ?? []).map((item) => item.id === updated.id ? updated : item),
      }));
    } catch {
      setTaskError("학습 과제 상태를 변경하지 못했습니다. 잠시 후 다시 시도해주세요.");
    } finally {
      setUpdatingTaskId(null);
    }
  }

  if (loading) return <StateCard title="학습 추천을 불러오는 중입니다." />;
  if (error) return <StateCard title={error} tone="error" />;
  if (analyses.length === 0) {
    return <StateCard title="아직 추천할 학습 결과가 없습니다." description="적합도 분석을 먼저 실행하면 부족 역량 기반 추천이 표시됩니다." />;
  }

  return (
    <div className="space-y-5">
      {!hideHeading && (
        <div>
          <h2 className="text-lg font-bold text-slate-900">학습/자격증 추천</h2>
          <p className="mt-1 text-sm text-slate-500">지원 건별 부족 역량을 학습 과제와 자격증 추천으로 연결합니다.</p>
        </div>
      )}
      {taskError && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-600">{taskError}</div>}

      <CareerStrategyCard />

      <div className="grid gap-4 lg:grid-cols-2">
        {analyses.map((analysis) => {
          const studyItems = parseJsonList(analysis.recommendedStudy);
          const certificates = parseJsonList(analysis.recommendedCertificates);
          const detailedCertificates = parseJsonValue<FitCertificateRecommendation[]>(analysis.certificateRecommendations, []);
          const gaps = parseJsonValue<FitGapRecommendation[]>(analysis.gapRecommendations, []);
          const learningTasks = tasks[analysis.id] ?? [];
          const completedCount = learningTasks.filter((task) => task.completed).length;
          const completionRate = learningTasks.length === 0 ? 0 : Math.round((completedCount / learningTasks.length) * 100);
          // 기획 원칙: 과도한 자격증 추천을 줄인다 — 필수 부족 역량이 남아 있으면 실무 보완 우선을 안내.
          const certificateCaution =
            detailedCertificates.length >= 2 && gaps.some((gap) => gap.priority === "HIGH");

          return (
            <Card key={analysis.id} className="border border-slate-200 bg-card">
              <CardHeader className="pb-3">
                <CardTitle className="text-base">{analysis.application.companyName} · {analysis.application.jobTitle}</CardTitle>
                {learningTasks.length > 0 && (
                  <div className="mt-2">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-semibold text-slate-600">이번 지원 건 준비율</span>
                      <span className="font-black text-blue-600">{completionRate}%</span>
                    </div>
                    <Progress value={completionRate} className="mt-1 h-1.5" />
                    <div className="mt-0.5 text-[11px] text-slate-400">학습 과제 {learningTasks.length}개 중 {completedCount}개 완료</div>
                  </div>
                )}
              </CardHeader>
              <CardContent className="space-y-4">
                {/* 학습 80% 이상 완료 시 재분석 유도(완료 후 점수 변화 확인 흐름). */}
                {learningTasks.length > 0 && completionRate >= 80 && (
                  <div className="flex flex-col gap-2 rounded-lg border border-green-200 bg-green-50 p-3 sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-xs leading-5 text-green-800">
                      학습 항목을 {completionRate}% 완료했습니다. 적합도를 다시 분석해 점수가 얼마나 올랐는지 확인해보세요.
                    </p>
                    {onReanalyze && (
                      <div className="flex shrink-0 flex-col items-start gap-1.5 sm:items-end">
                        <AiChargeCostBadge featureType="FIT_ANALYSIS" />
                        <Button
                          size="sm"
                          className="bg-green-600 text-white hover:bg-green-700"
                          disabled={reanalyzing}
                          onClick={onReanalyze}
                        >
                          <RefreshCw className={`size-3.5 ${reanalyzing ? "animate-spin" : ""}`} />
                          {reanalyzing ? "재분석 중..." : "적합도 재분석"}
                        </Button>
                      </div>
                    )}
                  </div>
                )}

                <WeeklyPlanCard tasks={learningTasks} />
                <LearningTaskList tasks={learningTasks} fallbackItems={studyItems} updatingTaskId={updatingTaskId} onToggle={toggleTask} />
                <PortfolioTaskCard gaps={gaps} />

                {certificateCaution && (
                  <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-800">
                    <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600" />
                    이 공고는 실무 프로젝트 경험을 더 중요하게 봅니다. 자격증 준비보다 필수 부족 역량 보완을 우선하는 것이 좋습니다.
                  </div>
                )}
                <CertificateList recommendations={detailedCertificates} fallbackItems={certificates} />
                <CertificateEvidenceSection snapshot={analysis.certificateEvidence ?? null} fitAnalysisId={analysis.id} applicationCaseId={analysis.applicationCaseId} />

                <Link to={`/applications/${analysis.applicationCaseId}`} className="inline-flex text-sm font-semibold text-blue-600 hover:text-blue-700">
                  지원 건 상세 보기
                </Link>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}

/** 장기 커리어 자격증 전략(희망직무 기준) — 특정 지원 건 전략과 분리된 사용자 단위 섹션. 실패 시 조용히 숨김(보조 정보). */
function CareerStrategyCard() {
  const [strategy, setStrategy] = useState<CareerCertificateStrategy | null>(null);

  useEffect(() => {
    let cancelled = false;
    getCareerCertificateStrategy()
      .then((data) => { if (!cancelled) setStrategy(data); })
      .catch(() => { /* 보조 섹션 — 실패해도 학습 추천 본문을 막지 않는다. */ });
    return () => { cancelled = true; };
  }, []);

  if (!strategy) return null;
  const hasContent = strategy.heldStrengths.length > 0 || strategy.longTermCandidates.length > 0;
  return (
    <Card className="border border-indigo-100 bg-indigo-50/50">
      <CardContent className="space-y-2 p-4">
        <div className="flex flex-wrap items-center gap-1.5 text-sm font-semibold text-slate-800">
          <GraduationCap className="size-4 text-indigo-600" />
          장기 커리어 전략{strategy.desiredJob ? ` · ${strategy.desiredJob}` : ""}
          <span className="rounded-full border border-indigo-200 bg-indigo-100 px-2 py-0.5 text-[11px] font-semibold text-indigo-700">
            이번 지원과 별개
          </span>
        </div>
        {strategy.heldStrengths.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5 text-xs text-slate-600">
            <span className="font-semibold text-slate-700">보유 강점:</span>
            {strategy.heldStrengths.map((name) => (
              <span key={name} className="rounded-full border border-green-200 bg-green-50 px-2 py-0.5 text-[11px] font-semibold text-green-700">{name}</span>
            ))}
          </div>
        )}
        {strategy.longTermCandidates.length > 0 && (
          <ul className="space-y-1 text-xs leading-5 text-slate-600">
            {strategy.longTermCandidates.map((candidate) => (
              <li key={candidate.name}>
                <span className="font-semibold text-slate-800">{candidate.name}</span> — {candidate.reason}
              </li>
            ))}
          </ul>
        )}
        {!hasContent && strategy.desiredJob == null && (
          <p className="text-xs leading-5 text-slate-500">{strategy.note}</p>
        )}
        {hasContent && <p className="text-[11px] leading-5 text-slate-400">{strategy.note}</p>}
        <Link to="/career-roadmap" className="mt-2 inline-block text-xs font-semibold text-indigo-600 underline-offset-2 hover:underline">
          연 단위 장기 로드맵 보기 →
        </Link>
      </CardContent>
    </Card>
  );
}

/** 자격증 전략·근거(공식 출처 조회 snapshot). 탭 요청이어도 '평가'라 후순위/불필요도 정상. 확인 못 하면 솔직하게 안내. */
function CertificateEvidenceSection({ snapshot, fitAnalysisId, applicationCaseId }: {
  snapshot: CertificateEvidenceSnapshot | null;
  fitAnalysisId: number;
  applicationCaseId: number;
}) {
  if (!snapshot) return null;
  const items = snapshot.items ?? [];
  const verdict = strategyVerdict(snapshot.strategyStatus);
  return (
    <div>
      <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800">
        <Award className="size-4 text-blue-600" />
        자격증 전략 · 근거
        {verdict && <EvidencePill tone={verdict.tone}>{verdict.label}</EvidencePill>}
      </div>
      {verdict?.note && <p className="mb-2 text-xs leading-5 text-slate-500">{verdict.note}</p>}
      {items.length === 0 ? (
        <p className="text-xs leading-5 text-slate-500">{emptyItemsMessage(snapshot.strategyStatus)}</p>
      ) : (
      <ul className="space-y-2">
        {items.map((item) => (
          <li key={item.certName} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-semibold text-slate-800">{item.certName}</span>
              <EvidenceBadge status={item.scheduleStatus} registration={item.registrationStatus} />
            </div>
            <p className="mt-1 text-xs leading-5 text-slate-600">{item.message}</p>
            {item.scheduleRounds.length > 0 && (
              <ul className="mt-1.5 space-y-0.5 text-[11px] text-slate-500">
                {item.scheduleRounds.slice(0, 2).map((round, index) => (
                  <li key={index}>
                    {round.round ? `${round.round} · ` : ""}접수 {fmtCertDate(round.docRegStart)}~{fmtCertDate(round.docRegEnd)} · 필기 {fmtCertDate(round.docExam)} · 발표 {fmtCertDate(round.docPass)}
                  </li>
                ))}
              </ul>
            )}
            {item.sourceName && (
              <div className="mt-1 text-[11px] text-slate-400">
                출처: {item.sourceUrl
                  ? <a href={item.sourceUrl} target="_blank" rel="noreferrer" className="underline">{item.sourceName}</a>
                  : item.sourceName}
              </div>
            )}
            <CertScheduleToPlannerButton item={item} fitAnalysisId={fitAnalysisId} applicationCaseId={applicationCaseId} />
          </li>
        ))}
      </ul>
      )}
    </div>
  );
}

/** items 가 비었을 때의 솔직한 안내 — '미연동/확인 못함'을 '자격증 불필요'로 오분류하지 않는다(판정과 문구 일치). */
function emptyItemsMessage(status: string | null): string {
  if (status === "NOT_NEEDED" || status === "OPTIONAL_LOW_PRIORITY") {
    return "현재 공고 기준으로는 자격증보다 실무 경험·프로젝트 보완이 우선입니다.";
  }
  // RECOMMENDED/REQUIRED/USE_EXISTING 인데 근거가 비어 있음 = 공식 출처 조회 미연동/확인 실패 — 불필요가 아니다.
  return "공식 출처 근거 조회가 아직 연동되지 않았거나 확인하지 못했습니다. 위 추천 자격증은 참고하되, 일정은 임의로 제시하지 않습니다.";
}

/** 게이트 판정 → 화면 배지·안내(솔직 표현). 탭 요청이어도 후순위/불필요가 정상 결과다. */
function strategyVerdict(status: string | null): { label: string; tone: "green" | "slate" | "amber" | "blue" | "red"; note?: string } | null {
  switch (status) {
    case "REQUIRED_OR_STRONGLY_PREFERRED": return { label: "강하게 필요", tone: "red" };
    case "RECOMMENDED": return { label: "추천", tone: "blue" };
    case "USE_EXISTING_AS_STRENGTH": return { label: "보유 강점 활용", tone: "green" };
    case "OPTIONAL_LOW_PRIORITY": return { label: "후순위", tone: "slate", note: "있으면 도움되지만 현재 우선순위는 낮습니다. 실무 경험 보완이 먼저입니다." };
    case "NOT_NEEDED": return { label: "현 시점 불필요", tone: "slate", note: "이 공고에서는 자격증보다 프로젝트·배포·경험 보완이 더 중요합니다." };
    default: return null;
  }
}

function EvidenceBadge({ status, registration }: { status: string; registration: string | null }) {
  if (registration === "ABOLISHED_OR_CANCELLED") return <EvidencePill tone="red">등록 폐지</EvidencePill>;
  switch (status) {
    case "VERIFIED_CURRENT": return <EvidencePill tone="green">공식 일정 확인</EvidencePill>;
    case "PREANNOUNCED": return <EvidencePill tone="blue">사전공고 일정(안)</EvidencePill>;
    case "OFFICIAL_NO_SCHEDULE": return <EvidencePill tone="slate">올해 미편성</EvidencePill>;
    case "UPSTREAM_UNAVAILABLE": return <EvidencePill tone="amber">공식 서비스 확인 불가</EvidencePill>;
    case "MANUAL_REQUIRED": return <EvidencePill tone="blue">주관기관 확인 필요</EvidencePill>;
    case "NOT_APPLICABLE": return <EvidencePill tone="slate">시행기관 확인</EvidencePill>;
    default: return <EvidencePill tone="slate">확인 불가</EvidencePill>;
  }
}

function EvidencePill({ tone, children }: { tone: "green" | "slate" | "amber" | "blue" | "red"; children: string }) {
  const cls: Record<string, string> = {
    green: "border-green-200 bg-green-50 text-green-700",
    slate: "border-slate-200 bg-slate-100 text-slate-600",
    amber: "border-amber-200 bg-amber-50 text-amber-700",
    blue: "border-blue-200 bg-blue-50 text-blue-700",
    red: "border-red-200 bg-red-50 text-red-700",
  };
  return <span className={`shrink-0 rounded-full border px-2 py-0.5 text-[11px] font-semibold ${cls[tone]}`}>{children}</span>;
}

function fmtCertDate(value: string | null): string {
  if (!value || value.length !== 8) return "미정";
  return `${value.slice(0, 4)}.${value.slice(4, 6)}.${value.slice(6, 8)}`;
}

/** yyyymmdd → 플래너 datetime-local 형식(자정). 공식 회차 날짜를 그대로 사용 — 날짜를 만들어내지 않는다. */
function certDateToPlanner(value: string | null): string | null {
  if (!value || value.length !== 8) return null;
  return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}T00:00:00`;
}

/**
 * 확인된 회차(공식/사전공고)를 플래너 일정으로 추가 — 접수 시작=DEADLINE, 필기/시험일=EVENT.
 * 날짜가 확인된 상태(VERIFIED_CURRENT/PREANNOUNCED)에서만 노출한다(확인 못한 날짜로 일정 생성 금지).
 */
function CertScheduleToPlannerButton({ item, fitAnalysisId, applicationCaseId }: {
  item: { certName: string; scheduleStatus: string; message: string; scheduleRounds: { round: string | null; docRegStart: string | null; docRegEnd: string | null; docExam: string | null; pracExamStart: string | null }[] };
  fitAnalysisId: number;
  applicationCaseId: number;
}) {
  const [state, setState] = useState<"idle" | "saving" | "done">("idle");
  if (item.scheduleStatus !== "VERIFIED_CURRENT" && item.scheduleStatus !== "PREANNOUNCED") return null;
  const today = new Date();
  const todayKey = `${today.getFullYear()}${String(today.getMonth() + 1).padStart(2, "0")}${String(today.getDate()).padStart(2, "0")}`;
  // 다음 회차 = 시험일이 오늘 이후인 첫 회차. 없으면(모든 회차가 지남) 버튼을 노출하지 않는다 —
  // 과거 회차를 '다음 회차'로 플래너에 넣지 않기 위함(지난 날짜 일정 생성 금지).
  const next = item.scheduleRounds.find((round) => (round.docExam ?? "") >= todayKey);
  if (!next) return null;
  const preNote = item.scheduleStatus === "PREANNOUNCED" ? " (사전공고 기준 — 최종 공고 확인 필요)" : "";

  const add = async () => {
    setState("saving");
    try {
      const base = {
        description: `${next.round ?? "회차"}${preNote}`.trim(),
        status: "PLANNED",
        allDay: true,
        timingPrecision: "DAY",
        endAt: null as string | null,
        timezone: "Asia/Seoul",
        applicationCaseId,
        fitAnalysisId,
        sourceType: "CERTIFICATE_EXAM",
        sourceRef: `${item.certName}:${next.round ?? ""}`,
        overlayVisible: true,
        opacity: 0.96,
        pinned: false,
        clickThrough: false,
        reminders: [{ remindAt: null, offsetMinutes: 1440, channels: ["WEB_TOAST"] as PlannerReminderChannel[], soundEnabled: false, vibrationEnabled: false }],
      };
      let added = 0;
      // 접수 시작일이 이미 지났으면(진행 중이거나 마감) 과거 날짜 DEADLINE 을 만들지 않는다 — 미래 회차의 접수만 등록.
      const regStart = (next.docRegStart ?? "") >= todayKey ? certDateToPlanner(next.docRegStart) : null;
      if (regStart) {
        await createPlannerScheduleItem({ ...base, title: `${item.certName} 원서접수 시작`, kind: "DEADLINE", startAt: regStart, endAt: certDateToPlanner(next.docRegEnd) });
        added += 1;
      }
      const exam = certDateToPlanner(next.docExam);
      if (exam) {
        await createPlannerScheduleItem({ ...base, title: `${item.certName} 필기시험`, kind: "EVENT", startAt: exam });
        added += 1;
      }
      const prac = certDateToPlanner(next.pracExamStart);
      if (prac) {
        await createPlannerScheduleItem({ ...base, title: `${item.certName} 실기시험 시작`, kind: "EVENT", startAt: prac });
        added += 1;
      }
      if (added === 0) throw new Error("추가할 확인된 날짜가 없습니다.");
      setState("done");
      toast.success(`${item.certName} 일정 ${added}건을 플래너에 추가했습니다.`);
    } catch (requestError) {
      setState("idle");
      toast.error(requestError instanceof Error ? requestError.message : "일정을 추가하지 못했습니다.");
    }
  };

  return (
    <div className="mt-2">
      {state === "done" ? (
        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-green-700">
          <CheckCircle2 className="size-3.5" /> 플래너에 추가됨
        </span>
      ) : (
        <Button variant="outline" size="sm" className="h-7 gap-1 px-2 text-[11px]" onClick={add} disabled={state === "saving"}>
          <CalendarPlus className="size-3.5" />
          {state === "saving" ? "추가 중..." : "다음 회차 플래너에 추가"}
        </Button>
      )}
    </div>
  );
}

/** 주간 학습 계획: 미완료 과제를 우선순위(HIGH→LOW)·정렬순으로 골라 이번 주 목표 3개를 제안한다. */
function WeeklyPlanCard({ tasks }: { tasks: FitAnalysisLearningTask[] }) {
  const priorityRank: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };
  const weekly = tasks
    .slice()
    .filter((task) => !task.completed)
    .sort((a, b) => (priorityRank[a.priority] ?? 3) - (priorityRank[b.priority] ?? 3) || a.sortOrder - b.sortOrder)
    .slice(0, 3);
  if (weekly.length === 0) return null;

  return (
    <div className="rounded-lg border border-blue-100 bg-blue-50 p-3">
      <div className="flex items-center gap-1.5 text-sm font-semibold text-blue-900">
        <CalendarCheck className="size-4 text-blue-600" />
        이번 주 목표
      </div>
      <ol className="mt-1.5 space-y-1 text-xs leading-5 text-blue-800">
        {weekly.map((task, index) => (
          <li key={task.id}>
            {index + 1}. {task.title} <span className="text-blue-500">({task.expectedDuration})</span>
          </li>
        ))}
      </ol>
    </div>
  );
}

function LearningTaskList({ tasks, fallbackItems, updatingTaskId, onToggle }: {
  tasks: FitAnalysisLearningTask[];
  fallbackItems: string[];
  updatingTaskId: number | null;
  onToggle: (task: FitAnalysisLearningTask) => Promise<void>;
}) {
  return (
    <div>
      <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800">
        <BookOpen className="size-4 text-blue-600" />
        추천 학습 로드맵
      </div>
      <div className="space-y-2">
        {tasks.length > 0 ? tasks.map((task) => (
          <button
            key={task.id}
            type="button"
            disabled={updatingTaskId === task.id}
            onClick={() => void onToggle(task)}
            className="flex w-full items-start gap-3 rounded-lg border border-slate-100 p-3 text-left hover:border-blue-200 disabled:opacity-60"
          >
            {task.completed ? <CheckCircle2 className="mt-0.5 size-5 text-green-600" /> : <Circle className="mt-0.5 size-5 text-slate-300" />}
            <span className="min-w-0 flex-1">
              <span className={`block text-sm font-semibold ${task.completed ? "text-slate-400 line-through" : "text-slate-800"}`}>{task.title}</span>
              <span className="mt-1 block text-xs leading-5 text-slate-500">{task.practiceTask}</span>
              <span className="mt-1 block text-xs font-medium text-blue-600">{task.skill} · {task.expectedDuration}</span>
            </span>
          </button>
        )) : fallbackItems.length > 0 ? fallbackItems.map((item, index) => (
          <div key={`${item}-${index}`} className="rounded-lg bg-slate-50 p-3 text-sm text-slate-700">{item}</div>
        )) : <div className="rounded-lg bg-slate-50 p-3 text-sm text-slate-400">추천 학습이 아직 생성되지 않았습니다.</div>}
      </div>
    </div>
  );
}

/** 부족 역량을 이력서 문장 첨삭이 아닌 포트폴리오 결과물 과제로 전환한다(C 담당 경계). */
function PortfolioTaskCard({ gaps }: { gaps: FitGapRecommendation[] }) {
  const tasks = gaps
    .filter((gap) => gap.priority === "HIGH" || gap.category === "PREFERRED_GAP")
    .slice(0, 3)
    .map((gap) => ({
      skill: gap.skill,
      task: `${gap.skill}을(를) 활용한 작은 기능을 구현하고, README에 선택 이유·문제 해결·검증 결과를 정리합니다.`,
    }));
  if (tasks.length === 0) return null;

  return (
    <div className="rounded-lg border border-teal-100 bg-teal-50 p-3">
      <div className="flex items-center gap-1.5 text-sm font-semibold text-teal-900">
        <Hammer className="size-4 text-teal-600" />
        포트폴리오 보강 과제
      </div>
      <p className="mt-1 text-xs leading-5 text-teal-700">부족 역량을 실제 결과물과 설명 근거로 바꾸는 과제입니다.</p>
      <div className="mt-2 space-y-2">
        {tasks.map((item) => (
          <div key={item.skill} className="rounded-lg bg-card/80 p-2.5">
            <div className="text-xs font-bold text-teal-900">{item.skill}</div>
            <div className="mt-0.5 text-xs leading-5 text-slate-600">{item.task}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function CertificateList({ recommendations, fallbackItems }: { recommendations: FitCertificateRecommendation[]; fallbackItems: string[] }) {
  return (
    <div>
      <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800">
        <Award className="size-4 text-amber-600" />
        추천 자격증
      </div>
      <div className="space-y-2">
        {recommendations.length > 0 ? recommendations.map((item) => (
          <div key={item.name} className="rounded-lg bg-amber-50 p-3">
            <div className="text-sm font-semibold text-slate-800">{item.name}</div>
            <div className="mt-1 text-xs text-slate-500">{item.reason}</div>
          </div>
        )) : fallbackItems.length > 0 ? fallbackItems.map((item) => (
          <div key={item} className="rounded-lg bg-slate-50 p-3 text-sm text-slate-700">{item}</div>
        )) : <div className="rounded-lg bg-slate-50 p-3 text-sm text-slate-400">이 지원 건에는 우선 추천 자격증이 없습니다.</div>}
      </div>
    </div>
  );
}

function StateCard({ title, description, tone = "default" }: { title: string; description?: string; tone?: "default" | "error" }) {
  return (
    <Card className={`border ${tone === "error" ? "border-red-200 bg-red-50" : "border-slate-200 bg-card"}`}>
      <CardContent className="flex items-start gap-3 p-5">
        <GraduationCap className={`mt-0.5 size-5 ${tone === "error" ? "text-red-500" : "text-blue-600"}`} />
        <div>
          <div className="text-sm font-semibold text-slate-800">{title}</div>
          {description && <div className="mt-1 text-sm text-slate-500">{description}</div>}
        </div>
      </CardContent>
    </Card>
  );
}
