import { useEffect, useState } from "react";
import { AlertTriangle, Award, BookOpen, CalendarCheck, CheckCircle2, Circle, GraduationCap, Hammer, RefreshCw } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import { updateFitAnalysisLearningTask } from "@/features/analysis/api/fitAnalysisApi";
import type {
  FitAnalysisDetail,
  FitAnalysisLearningTask,
  FitCertificateRecommendation,
  FitGapRecommendation,
} from "@/features/analysis/types/fitAnalysis";
import { parseJsonList, parseJsonValue } from "@/features/analysis/types/fitAnalysis";

interface LearningRecommendationPanelProps {
  analyses: FitAnalysisDetail[];
  loading: boolean;
  error: string | null;
  /** 학습 80% 이상 완료 시 재분석 유도 버튼에 연결. 미전달이면 안내 문구만 표시한다. */
  onReanalyze?: () => void;
  reanalyzing?: boolean;
}

export function LearningRecommendationPanel({ analyses, loading, error, onReanalyze, reanalyzing = false }: LearningRecommendationPanelProps) {
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
      <div>
        <h2 className="text-lg font-bold text-slate-900">학습/자격증 추천</h2>
        <p className="mt-1 text-sm text-slate-500">지원 건별 부족 역량을 학습 과제와 자격증 추천으로 연결합니다.</p>
      </div>
      {taskError && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-600">{taskError}</div>}

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
                      <Button
                        size="sm"
                        className="shrink-0 bg-green-600 text-white hover:bg-green-700"
                        disabled={reanalyzing}
                        onClick={onReanalyze}
                      >
                        <RefreshCw className={`size-3.5 ${reanalyzing ? "animate-spin" : ""}`} />
                        {reanalyzing ? "재분석 중..." : "적합도 재분석"}
                      </Button>
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
              </CardContent>
            </Card>
          );
        })}
      </div>
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
