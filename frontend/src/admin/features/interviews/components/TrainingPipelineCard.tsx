import { useEffect, useState } from "react";
import { AlertCircle, Cpu, Database, Download, FlaskConical, Loader2 } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import {
  downloadTrainingJsonl,
  getTrainingStats,
  runEvalHarness,
  startFineTune,
} from "../trainingApi";
import type { EvalHarnessResult, FineTuneResult, TrainingStats } from "../types";

const MIN_SAMPLES_FOR_FT = 10;

/**
 * 면접 평가 학습 데이터 파이프라인 관리.
 * 누적 학습 샘플 통계, JSONL 내보내기, 평가 하니스(채점 일관성), 파인튜닝 잡 생성을 한곳에서 다룬다.
 * 자체 모델 LoRA 학습은 ml/interview-finetune 자산으로 별도 진행한다(로드맵 5장).
 */
export function TrainingPipelineCard() {
  const [stats, setStats] = useState<TrainingStats | null>(null);
  const [evalResult, setEvalResult] = useState<EvalHarnessResult | null>(null);
  const [fineTune, setFineTune] = useState<FineTuneResult | null>(null);
  const [busy, setBusy] = useState<null | "export" | "eval" | "ft">(null);
  const [error, setError] = useState<string | null>(null);

  const loadStats = async () => {
    setError(null);
    try {
      setStats(await getTrainingStats());
    } catch (err) {
      setError(err instanceof Error ? err.message : "학습 통계를 불러오지 못했습니다.");
    }
  };

  useEffect(() => {
    void loadStats();
  }, []);

  const handleExport = async () => {
    setBusy("export");
    setError(null);
    try {
      await downloadTrainingJsonl(2000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "내보내기에 실패했습니다.");
    } finally {
      setBusy(null);
    }
  };

  const handleEval = async () => {
    setBusy("eval");
    setError(null);
    try {
      setEvalResult(await runEvalHarness(20));
    } catch (err) {
      setError(err instanceof Error ? err.message : "평가 하니스 실행에 실패했습니다.");
    } finally {
      setBusy(null);
    }
  };

  const handleFineTune = async () => {
    if (!window.confirm("OpenAI 파인튜닝 잡을 생성합니다(비용 발생). 진행할까요?")) return;
    setBusy("ft");
    setError(null);
    try {
      const result = await startFineTune();
      setFineTune(result);
      void loadStats();
    } catch (err) {
      setError(err instanceof Error ? err.message : "파인튜닝 잡 생성에 실패했습니다.");
    } finally {
      setBusy(null);
    }
  };

  const sampleCount = stats?.sampleCount ?? 0;
  const canFineTune = sampleCount >= MIN_SAMPLES_FOR_FT;

  return (
    <Card className="border-slate-200 bg-card">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2 text-base font-bold text-slate-900">
          <Database className="size-4 text-indigo-600" /> 학습 파이프라인
        </CardTitle>
        <Badge className="bg-indigo-100 text-indigo-700">자체 LLM</Badge>
      </CardHeader>
      <CardContent className="space-y-3">
        {/* 통계 */}
        <div className="grid grid-cols-2 gap-3">
          <Stat label="누적 학습 샘플" value={`${sampleCount.toLocaleString()}건`} />
          <Stat
            label="평균 점수"
            value={stats?.averageScore != null ? `${stats.averageScore.toFixed(1)}점` : "-"}
          />
        </div>

        {error && (
          <p className="flex items-center gap-1.5 text-xs text-red-600">
            <AlertCircle className="size-3.5" /> {error}
          </p>
        )}

        {/* 액션 */}
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" className="gap-1.5" disabled={busy !== null} onClick={handleExport}>
            {busy === "export" ? <Loader2 className="size-3.5 animate-spin" /> : <Download className="size-3.5" />}
            JSONL 내보내기
          </Button>
          <Button size="sm" variant="outline" className="gap-1.5" disabled={busy !== null} onClick={handleEval}>
            {busy === "eval" ? <Loader2 className="size-3.5 animate-spin" /> : <FlaskConical className="size-3.5" />}
            평가 하니스
          </Button>
          <Button
            size="sm"
            className="gap-1.5 bg-indigo-600 text-white hover:bg-indigo-700"
            disabled={busy !== null || !canFineTune}
            onClick={handleFineTune}
            title={canFineTune ? undefined : `샘플 ${MIN_SAMPLES_FOR_FT}건 이상 필요`}
          >
            {busy === "ft" ? <Loader2 className="size-3.5 animate-spin" /> : <Cpu className="size-3.5" />}
            파인튜닝 시작
          </Button>
        </div>

        {/* 평가 하니스 결과 */}
        {evalResult && (
          <div className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
            <div className="mb-1 font-semibold text-slate-700">채점 일관성 (LLM-as-judge)</div>
            <div className="grid grid-cols-3 gap-2">
              <span>샘플 {evalResult.evaluated}건</span>
              <span>평균오차 {evalResult.meanAbsDiff.toFixed(1)}</span>
              <span>일치율 {(evalResult.agreementRate * 100).toFixed(0)}%</span>
            </div>
          </div>
        )}

        {/* 파인튜닝 결과 */}
        {fineTune && (
          <div className="rounded-lg border border-indigo-100 bg-indigo-50 p-3 text-xs text-slate-700">
            <div className="mb-1 font-semibold text-indigo-700">파인튜닝 잡 생성됨</div>
            <div>베이스: {fineTune.baseModel}</div>
            <div>잡 ID: {fineTune.jobId || "-"}</div>
            <div>상태: {fineTune.status}</div>
          </div>
        )}

        <p className="text-[11px] text-slate-400">
          자체 모델(Qwen2.5 LoRA) 학습은 <code>ml/interview-finetune</code> 자산으로 진행 → vLLM 서빙 후
          <code> eval.provider=oss</code> 로 연결.
        </p>
      </CardContent>
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <div className="mt-1 text-lg font-black text-slate-900">{value}</div>
    </div>
  );
}
