import { useEffect, useState } from "react";
import { Award, BookOpen, CheckCircle2, Circle, GraduationCap } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { updateFitAnalysisLearningTask } from "@/features/analysis/api/fitAnalysisApi";
import type { FitAnalysisDetail, FitAnalysisLearningTask, FitCertificateRecommendation } from "@/features/analysis/types/fitAnalysis";
import { parseJsonList, parseJsonValue } from "@/features/analysis/types/fitAnalysis";

interface LearningRecommendationPanelProps {
  analyses: FitAnalysisDetail[];
  loading: boolean;
  error: string | null;
}

export function LearningRecommendationPanel({ analyses, loading, error }: LearningRecommendationPanelProps) {
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
          const learningTasks = tasks[analysis.id] ?? [];

          return (
            <Card key={analysis.id} className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <CardTitle className="text-base">{analysis.application.companyName} · {analysis.application.jobTitle}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <LearningTaskList tasks={learningTasks} fallbackItems={studyItems} updatingTaskId={updatingTaskId} onToggle={toggleTask} />
                <CertificateList recommendations={detailedCertificates} fallbackItems={certificates} />
              </CardContent>
            </Card>
          );
        })}
      </div>
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
    <Card className={`border ${tone === "error" ? "border-red-200 bg-red-50" : "border-slate-200 bg-white"}`}>
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
