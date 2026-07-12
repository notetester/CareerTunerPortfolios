import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import {
  AlertCircle,
  CheckCircle2,
  FileClock,
  LoaderCircle,
  PenLine,
  Sparkles,
} from "lucide-react";
import { listApplicationCases } from "@/features/applications/api/applicationCasesApi";
import type { ApplicationCase } from "@/features/applications/types/applicationCase";
import { warmupCorrectionModel } from "@/features/correction/api/correctionApi";
import { AiChargeCostBadge } from "@/features/billing/components/AiChargeCostBadge";
import { ModelPicker, type AiModelChoice } from "@/app/components/ai/ModelPicker";
import { CorrectionHistoryList } from "@/features/correction/components/CorrectionHistoryList";
import { CorrectionResultCard } from "@/features/correction/components/CorrectionResultCard";
import { useCorrections } from "@/features/correction/hooks/useCorrections";
import {
  CORRECTION_TABS,
  CORRECTION_TYPE_BY_TAB,
  type CorrectionTab,
} from "@/features/correction/types/correction";
import { Alert, AlertDescription, AlertTitle } from "../components/ui/alert";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../components/ui/select";
import { Tabs, TabsList, TabsTrigger } from "../components/ui/tabs";
import { Textarea } from "../components/ui/textarea";

const ORIGINAL_TEXT_MAX_LENGTH = 12_000;
const QUESTION_TEXT_MAX_LENGTH = 1_000;

interface Draft {
  originalText: string;
  questionText: string;
}

const EMPTY_DRAFTS: Record<CorrectionTab, Draft> = {
  answer: { originalText: "", questionText: "" },
  cover: { originalText: "", questionText: "" },
  resume: { originalText: "", questionText: "" },
  portfolio: { originalText: "", questionText: "" },
};

const correctionMeta: Record<CorrectionTab, {
  title: string;
  desc: string;
  placeholder: string;
  questionLabel?: string;
  questionPlaceholder?: string;
}> = {
  answer: {
    title: "답변 첨삭",
    desc: "면접 답변을 직무 적합성, 구체성, 논리성 기준으로 개선합니다",
    placeholder: "고객 불만을 해결했던 경험이나 협업 갈등을 조정한 답변을 입력하세요.",
    questionLabel: "면접 질문",
    questionPlaceholder: "예: 갈등 상황을 해결한 경험을 설명해 주세요.",
  },
  cover: {
    title: "자기소개서 첨삭",
    desc: "문항 의도, 경험 구조, 성과 수치화, 지원 직무 연결성을 점검합니다",
    placeholder: "첨삭할 자기소개서 답변을 입력하세요.",
    questionLabel: "자기소개서 문항",
    questionPlaceholder: "예: 지원 동기와 입사 후 포부를 작성해 주세요.",
  },
  resume: {
    title: "이력서 첨삭",
    desc: "경험 표현, 직무 역량 정리, 성과 중심 문장을 보강합니다",
    placeholder: "이력서의 경력, 활동, 프로젝트 또는 실습 내용을 입력하세요.",
  },
  portfolio: {
    title: "포트폴리오 설명 첨삭",
    desc: "작업물의 배경, 역할, 문제 해결, 결과를 채용자가 읽기 좋게 다듬습니다",
    placeholder: "프로젝트나 포트폴리오 작업물 설명을 입력하세요.",
  },
};

const checklist = [
  "질문 의도와 답변 방향 일치",
  "경험의 맥락과 본인 역할 구분",
  "성과 수치 또는 비교 기준 포함",
  "지원 직무와의 연결성 강화",
];

export function CorrectionPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [drafts, setDrafts] = useState<Record<CorrectionTab, Draft>>(EMPTY_DRAFTS);
  const [applicationCases, setApplicationCases] = useState<ApplicationCase[]>([]);
  const [casesLoading, setCasesLoading] = useState(true);
  const [casesError, setCasesError] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const requestedTab = searchParams.get("tab") ?? "answer";
  const activeTab: CorrectionTab = CORRECTION_TABS.includes(requestedTab as CorrectionTab)
    ? (requestedTab as CorrectionTab)
    : "answer";
  const active = correctionMeta[activeTab];
  const draft = drafts[activeTab];

  const requestedCaseId = parseCaseId(searchParams.get("caseId"));
  const selectedCaseId = useMemo(() => {
    if (requestedCaseId === null) return null;
    if (casesLoading) return requestedCaseId;
    return applicationCases.some((item) => item.id === requestedCaseId) ? requestedCaseId : null;
  }, [applicationCases, casesLoading, requestedCaseId]);

  const {
    history,
    selected,
    historyLoading,
    historyError,
    detailLoadingId,
    submitting,
    submitError,
    loadHistory,
    selectHistory,
    submit,
  } = useCorrections(CORRECTION_TYPE_BY_TAB[activeTab], selectedCaseId);
  const [correctionModel, setCorrectionModel] = useState<AiModelChoice>("AUTO");

  useEffect(() => {
    void warmupCorrectionModel().catch(() => {
      // 워밍업 실패는 실제 첨삭 요청의 8B → 3B → Haiku → OpenAI 폴백에 맡긴다.
    });
  }, []);

  useEffect(() => {
    let activeRequest = true;
    setCasesLoading(true);
    setCasesError(null);
    void listApplicationCases(false)
      .then((rows) => {
        if (activeRequest) setApplicationCases(rows);
      })
      .catch((error: unknown) => {
        if (!activeRequest) return;
        setApplicationCases([]);
        setCasesError(error instanceof Error ? error.message : "지원 건을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (activeRequest) setCasesLoading(false);
      });
    return () => {
      activeRequest = false;
    };
  }, []);

  const updateSearchParam = (key: "tab" | "caseId", value: string | null) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (value) next.set(key, value);
      else next.delete(key);
      return next;
    });
  };

  const updateDraft = (key: keyof Draft, value: string) => {
    setDrafts((current) => ({
      ...current,
      [activeTab]: { ...current[activeTab], [key]: value },
    }));
    setValidationError(null);
  };

  const handleSubmit = async () => {
    const originalText = draft.originalText.trim();
    if (!originalText) {
      setValidationError("첨삭할 원문을 입력해 주세요.");
      return;
    }
    if (originalText.length > ORIGINAL_TEXT_MAX_LENGTH) {
      setValidationError(`원문은 ${ORIGINAL_TEXT_MAX_LENGTH.toLocaleString("ko-KR")}자까지 입력할 수 있습니다.`);
      return;
    }

    await submit({
      correctionType: CORRECTION_TYPE_BY_TAB[activeTab],
      applicationCaseId: selectedCaseId ?? undefined,
      originalText,
      sourceType: "DIRECT_INPUT",
      questionText: active.questionLabel && draft.questionText.trim() ? draft.questionText.trim() : undefined,
    }, correctionModel);
  };

  const invalidRequestedCase = !casesLoading && requestedCaseId !== null && selectedCaseId === null;
  const visibleError = validationError ?? submitError;

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1200px] space-y-6 px-4 py-8 sm:px-6">
        <header>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
            <PenLine className="size-6 text-blue-600" />
            AI 첨삭
          </h1>
          <p className="mt-1 text-sm text-slate-500">답변, 자기소개서, 이력서, 포트폴리오 설명을 지원 건 기준으로 다듬습니다</p>
        </header>

        <Tabs value={activeTab} onValueChange={(value) => updateSearchParam("tab", value)}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-card p-1">
            <TabsTrigger value="answer">답변 첨삭</TabsTrigger>
            <TabsTrigger value="cover">자기소개서 첨삭</TabsTrigger>
            <TabsTrigger value="resume">이력서 첨삭</TabsTrigger>
            <TabsTrigger value="portfolio">포트폴리오 설명</TabsTrigger>
          </TabsList>
        </Tabs>

        <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_340px]">
          <main className="min-w-0 space-y-5">
            <Card className="border border-slate-200 bg-card">
              <CardHeader>
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <CardTitle className="flex items-center gap-2 text-lg">
                      <Sparkles className="size-5 text-blue-600" />
                      {active.title}
                    </CardTitle>
                    <p className="mt-1 text-sm text-slate-500">{active.desc}</p>
                  </div>
                  <AiChargeCostBadge featureType={`CORRECTION_${CORRECTION_TYPE_BY_TAB[activeTab]}`} />
                </div>
              </CardHeader>
              <CardContent className="space-y-5">
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-800" htmlFor="correction-case">지원 건</label>
                  <Select
                    value={selectedCaseId === null ? "none" : String(selectedCaseId)}
                    onValueChange={(value) => updateSearchParam("caseId", value === "none" ? null : value)}
                    disabled={casesLoading || applicationCases.length === 0}
                  >
                    <SelectTrigger id="correction-case" className="h-10">
                      <SelectValue placeholder={casesLoading ? "지원 건을 불러오는 중" : "지원 건 없이 첨삭"} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">지원 건 없이 첨삭</SelectItem>
                      {applicationCases.map((item) => (
                        <SelectItem key={item.id} value={String(item.id)}>
                          {item.companyName} · {item.jobTitle}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {casesError && <p className="text-xs text-amber-700">지원 건 연결 없이 첨삭할 수 있습니다. {casesError}</p>}
                  {invalidRequestedCase && <p className="text-xs text-amber-700">요청한 지원 건을 찾을 수 없어 연결 없이 진행합니다.</p>}
                </div>

                {active.questionLabel && (
                  <div className="space-y-2">
                    <label className="text-sm font-bold text-slate-800" htmlFor="correction-question">{active.questionLabel}</label>
                    <Textarea
                      id="correction-question"
                      value={draft.questionText}
                      maxLength={QUESTION_TEXT_MAX_LENGTH}
                      rows={3}
                      onChange={(event) => updateDraft("questionText", event.target.value)}
                      placeholder={active.questionPlaceholder}
                      disabled={submitting}
                    />
                    <p className="text-right text-xs text-slate-400">{draft.questionText.length.toLocaleString("ko-KR")} / {QUESTION_TEXT_MAX_LENGTH.toLocaleString("ko-KR")}</p>
                  </div>
                )}

                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-800" htmlFor="correction-original">첨삭할 원문</label>
                  <Textarea
                    id="correction-original"
                    value={draft.originalText}
                    maxLength={ORIGINAL_TEXT_MAX_LENGTH}
                    rows={12}
                    onChange={(event) => updateDraft("originalText", event.target.value)}
                    placeholder={active.placeholder}
                    className="min-h-64 resize-y leading-6"
                    disabled={submitting}
                    aria-invalid={Boolean(visibleError)}
                  />
                  <div className="flex items-center justify-between gap-3 text-xs text-slate-400">
                    <span>지원 건을 연결하면 공고와 직무 맥락을 함께 반영합니다.</span>
                    <span className="shrink-0">{draft.originalText.length.toLocaleString("ko-KR")} / {ORIGINAL_TEXT_MAX_LENGTH.toLocaleString("ko-KR")}</span>
                  </div>
                </div>

                {visibleError && (
                  <Alert variant="destructive">
                    <AlertCircle />
                    <AlertTitle>첨삭을 진행하지 못했습니다</AlertTitle>
                    <AlertDescription>{visibleError}</AlertDescription>
                  </Alert>
                )}

                <div className="flex flex-wrap items-center gap-3">
                  <Button type="button" onClick={() => void handleSubmit()} disabled={submitting || !draft.originalText.trim()}>
                    {submitting ? <LoaderCircle className="size-4 animate-spin" /> : <Sparkles className="size-4" />}
                    {submitting ? "첨삭 중" : `${active.title} 실행`}
                  </Button>
                  <ModelPicker value={correctionModel} onChange={setCorrectionModel} disabled={submitting} />
                </div>
              </CardContent>
            </Card>

            {selected && <CorrectionResultCard result={selected} />}
          </main>

          <aside className="space-y-4 lg:sticky lg:top-4">
            <Card className="border border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <CheckCircle2 className="size-4 text-emerald-600" />
                  첨삭 기준
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                {checklist.map((item) => (
                  <div key={item} className="flex items-start gap-2 text-sm text-slate-700">
                    <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-600" />
                    {item}
                  </div>
                ))}
              </CardContent>
            </Card>

            <Card className="border border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <FileClock className="size-4 text-indigo-600" />
                  최근 첨삭 기록
                </CardTitle>
                <p className="text-xs leading-5 text-slate-500">현재 유형{selectedCaseId ? "과 지원 건" : ""}에 맞는 최근 20건입니다.</p>
              </CardHeader>
              <CardContent>
                <CorrectionHistoryList
                  items={history}
                  selectedId={selected?.id ?? null}
                  loading={historyLoading}
                  loadingId={detailLoadingId}
                  error={historyError}
                  onSelect={(id) => void selectHistory(id)}
                  onRetry={() => void loadHistory()}
                />
              </CardContent>
            </Card>
          </aside>
        </div>
      </div>
    </div>
  );
}

function parseCaseId(value: string | null) {
  if (!value) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}
