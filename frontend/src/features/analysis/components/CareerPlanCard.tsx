import { useEffect, useState } from "react";
import { CalendarCheck, CheckCircle2, Circle, Loader2, Plus, Save, Target } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import {
  createLearningPlan,
  createLearningPlanTask,
  getCareerPlan,
  updateCareerGoal,
  updateLearningPlanTask,
} from "../api/careerPlanApi";
import type { CareerPlan } from "../types/careerPlan";

export function CareerPlanCard({ hidden = false }: { hidden?: boolean }) {
  const [data, setData] = useState<CareerPlan | null>(null);
  const [targetJob, setTargetJob] = useState("");
  const [targetPeriod, setTargetPeriod] = useState("");
  const [prioritySkill, setPrioritySkill] = useState("");
  const [companyType, setCompanyType] = useState("");
  const [newPlanTitle, setNewPlanTitle] = useState("");
  const [newPlanSkill, setNewPlanSkill] = useState("");
  const [newTasks, setNewTasks] = useState<Record<number, string>>({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setError(null);
    try {
      const result = await getCareerPlan();
      setData(result);
      setTargetJob(result.goal?.targetJob ?? "");
      setTargetPeriod(result.goal?.targetPeriod ?? "");
      setPrioritySkill(result.goal?.prioritySkill ?? "");
      setCompanyType(result.goal?.preferredCompanyType ?? "");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "커리어 계획을 불러오지 못했습니다.");
    }
  };

  useEffect(() => { void load(); }, []);

  const saveGoal = async () => {
    setSaving(true);
    try {
      await updateCareerGoal({ targetJob, targetPeriod, prioritySkill, preferredCompanyType: companyType });
      await load();
    } finally {
      setSaving(false);
    }
  };

  const addPlan = async () => {
    if (!newPlanTitle.trim() || !newPlanSkill.trim()) return;
    setSaving(true);
    try {
      await createLearningPlan({ title: newPlanTitle.trim(), targetSkill: newPlanSkill.trim(), startDate: null, endDate: null });
      setNewPlanTitle("");
      setNewPlanSkill("");
      await load();
    } finally {
      setSaving(false);
    }
  };

  const addTask = async (planId: number) => {
    const task = newTasks[planId]?.trim();
    if (!task) return;
    await createLearningPlanTask(planId, task);
    setNewTasks((current) => ({ ...current, [planId]: "" }));
    await load();
  };

  const toggle = async (planId: number, taskId: number, done: boolean) => {
    const updated = await updateLearningPlanTask(planId, taskId, done);
    setData((current) => {
      if (!current) return current;
      return {
        ...current,
        learningPlans: current.learningPlans.map((plan) => {
          if (plan.id !== planId) return plan;
          const tasks = plan.tasks.map((task) => (task.id === taskId ? updated : task));
          const completed = tasks.filter((task) => task.done).length;
          return {
            ...plan,
            tasks,
            completionRate: tasks.length === 0 ? 0 : Math.round((completed * 100) / tasks.length),
          };
        }),
      };
    });
  };

  return (
    <Card className={`min-w-0 border border-indigo-200 bg-card ${hidden ? "hidden" : ""}`}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base"><Target className="size-4 text-indigo-600" />커리어 목표와 학습 계획</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {error && <div className="rounded-lg bg-red-50 p-3 text-xs text-red-700">{error}</div>}
        <div className="grid gap-2 sm:grid-cols-2">
          {[
            ["목표 직무", targetJob, setTargetJob],
            ["목표 기간", targetPeriod, setTargetPeriod],
            ["우선 역량", prioritySkill, setPrioritySkill],
            ["선호 기업 유형", companyType, setCompanyType],
          ].map(([label, value, setter]) => (
            <label key={String(label)} className="text-xs font-semibold text-slate-600">
              {String(label)}
              <input value={String(value)} onChange={(event) => (setter as (value: string) => void)(event.target.value)}
                className="mt-1 h-9 w-full rounded-md border border-slate-200 px-3 text-sm font-normal outline-none focus:border-indigo-400" />
            </label>
          ))}
        </div>
        <Button onClick={() => void saveGoal()} disabled={saving} className="h-9"><Save className="size-3.5" />목표 저장</Button>

        <div className="border-t border-slate-100 pt-4">
          <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800"><CalendarCheck className="size-4 text-blue-600" />학습 계획</div>
          <div className="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
            <input value={newPlanTitle} onChange={(e) => setNewPlanTitle(e.target.value)} placeholder="계획 제목"
              className="h-9 rounded-md border border-slate-200 px-3 text-sm" />
            <input value={newPlanSkill} onChange={(e) => setNewPlanSkill(e.target.value)} placeholder="목표 역량"
              className="h-9 rounded-md border border-slate-200 px-3 text-sm" />
            <Button onClick={() => void addPlan()} disabled={saving || !newPlanTitle.trim() || !newPlanSkill.trim()} className="h-9"><Plus className="size-3.5" />추가</Button>
          </div>
          {!data ? <Loader2 className="mt-4 size-4 animate-spin text-indigo-600" /> : (
            <div className="mt-3 space-y-3">
              {data.learningPlans.map((plan) => (
                <div key={plan.id} className="rounded-lg border border-slate-100 p-3">
                  <div className="flex items-center justify-between gap-2">
                    <div><div className="text-sm font-bold text-slate-800">{plan.title}</div><div className="text-xs text-slate-500">{plan.targetSkill}</div></div>
                    <span className="text-xs font-bold text-indigo-600">{plan.completionRate}%</span>
                  </div>
                  <Progress value={plan.completionRate} className="mt-2 h-1.5" />
                  <div className="mt-2 space-y-1">
                    {plan.tasks.map((task) => (
                      <button key={task.id} type="button" onClick={() => void toggle(plan.id, task.id, !task.done)}
                        className="flex w-full items-center gap-2 rounded px-1 py-1 text-left text-xs text-slate-600 hover:bg-slate-50">
                        {task.done ? <CheckCircle2 className="size-4 text-green-600" /> : <Circle className="size-4 text-slate-300" />}
                        <span className={task.done ? "line-through text-slate-400" : ""}>{task.task}</span>
                      </button>
                    ))}
                  </div>
                  <div className="mt-2 flex gap-2">
                    <input value={newTasks[plan.id] ?? ""} onChange={(e) => setNewTasks((current) => ({ ...current, [plan.id]: e.target.value }))}
                      placeholder="세부 과제 추가" className="h-8 min-w-0 flex-1 rounded-md border border-slate-200 px-2 text-xs" />
                    <Button variant="outline" onClick={() => void addTask(plan.id)} disabled={!newTasks[plan.id]?.trim()} className="h-8"><Plus className="size-3" />과제</Button>
                  </div>
                </div>
              ))}
              {data.learningPlans.length === 0 && <div className="rounded-lg bg-slate-50 p-3 text-xs text-slate-500">첫 학습 계획을 추가하세요.</div>}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
