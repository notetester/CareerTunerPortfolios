import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import {
  AlertCircle,
  CheckCircle2,
  FileClock,
  LoaderCircle,
  PenLine,
  RotateCcw,
  Sparkles,
  UserRound,
} from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { listApplicationCases } from "@/features/applications/api/applicationCasesApi";
import type { ApplicationCase } from "@/features/applications/types/applicationCase";
import { getInterviewAnswerCorrectionSource, warmupCorrectionModel } from "@/features/correction/api/correctionApi";
import { getProfile, type UserProfile } from "@/app/profile/profileApi";
import { AiChargeCostBadge } from "@/features/billing/components/AiChargeCostBadge";
import { ModelPicker, type AiModelChoice } from "@/app/components/ai/ModelPicker";
import { CorrectionHistoryList } from "@/features/correction/components/CorrectionHistoryList";
import { CorrectionResultCard } from "@/features/correction/components/CorrectionResultCard";
import { useCorrections } from "@/features/correction/hooks/useCorrections";
import {
  CORRECTION_TABS,
  CORRECTION_TYPE_BY_TAB,
  type CorrectionTab,
  type CorrectionInterviewSource,
} from "@/features/correction/types/correction";
import { Alert, AlertDescription, AlertTitle } from "@/app/components/ui/alert";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/app/components/ui/select";
import { Tabs, TabsList, TabsTrigger } from "@/app/components/ui/tabs";
import { Textarea } from "@/app/components/ui/textarea";
import {
  ORIGINAL_TEXT_MAX_LENGTH,
  QUESTION_TEXT_MAX_LENGTH,
  clearCorrectionDraft,
  emptyCorrectionDraft,
  getCorrectionDraftStorage,
  loadCorrectionDrafts,
  persistCorrectionDrafts,
  type CorrectionDraft,
} from "../lib/correctionDraftStorage";

export const CORRECTION_SECTION_PATHS: Record<CorrectionTab, string> = {
  answer: "/correction/answer",
  cover: "/correction/cover-letter",
  resume: "/correction/resume",
  portfolio: "/correction/portfolio",
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

export function CorrectionPage({ section }: { section?: CorrectionTab } = {}) {
  const { user } = useAuth();
  const userId = user?.id ?? null;
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const draftStorage = useMemo(() => getCorrectionDraftStorage(), []);
  const [draftOwnerId, setDraftOwnerId] = useState<number | null>(userId);
  const [drafts, setDrafts] = useState(() => loadCorrectionDrafts(draftStorage, userId));
  const [applicationCases, setApplicationCases] = useState<ApplicationCase[]>([]);
  const [casesLoading, setCasesLoading] = useState(true);
  const [casesError, setCasesError] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [linkedInterviewSource, setLinkedInterviewSource] = useState<CorrectionInterviewSource | null>(null);
  const [sourceLoading, setSourceLoading] = useState(false);
  const [sourceError, setSourceError] = useState<string | null>(null);
  const [profileData, setProfileData] = useState<UserProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileNotice, setProfileNotice] = useState<string | null>(null);

  const requestedTab = searchParams.get("tab") ?? "answer";
  const legacyTab: CorrectionTab = CORRECTION_TABS.includes(requestedTab as CorrectionTab)
    ? (requestedTab as CorrectionTab)
    : "answer";
  const activeTab = section ?? legacyTab;
  const active = correctionMeta[activeTab];
  const draft = drafts[activeTab];

  const requestedCaseId = parseCaseId(searchParams.get("caseId"));
  const requestedSourceRefId = parseCaseId(searchParams.get("sourceRefId"));
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
    deletingId,
    submitting,
    submitError,
    loadHistory,
    selectHistory,
    remove,
    submit,
  } = useCorrections(CORRECTION_TYPE_BY_TAB[activeTab], selectedCaseId);
  const [correctionModel, setCorrectionModel] = useState<AiModelChoice>("AUTO");

  useEffect(() => {
    if (draftOwnerId === userId) return;
    setDrafts(loadCorrectionDrafts(draftStorage, userId));
    setDraftOwnerId(userId);
  }, [draftOwnerId, draftStorage, userId]);

  useEffect(() => {
    if (userId == null || draftOwnerId !== userId) return;
    persistCorrectionDrafts(draftStorage, userId, drafts);
  }, [draftOwnerId, draftStorage, drafts, userId]);

  useEffect(() => {
    void warmupCorrectionModel().catch(() => {
      // 워밍업 실패는 실제 첨삭 요청의 8B → 3B → Haiku → OpenAI 폴백에 맡긴다.
    });
  }, []);

  useEffect(() => {
    setProfileNotice(null);
  }, [activeTab]);

  useEffect(() => {
    let activeRequest = true;
    if (activeTab !== "answer" || requestedSourceRefId === null) {
      setLinkedInterviewSource(null);
      setSourceLoading(false);
      setSourceError(null);
      return () => { activeRequest = false; };
    }

    setSourceLoading(true);
    setSourceError(null);
    void getInterviewAnswerCorrectionSource(requestedSourceRefId)
      .then((source) => {
        if (!activeRequest) return;
        setLinkedInterviewSource(source);
        setDrafts((current) => ({
          ...current,
          answer: { originalText: source.originalText, questionText: source.questionText },
        }));
        setSearchParams((current) => {
          if (current.get("caseId") === String(source.applicationCaseId)) return current;
          const next = new URLSearchParams(current);
          next.set("caseId", String(source.applicationCaseId));
          return next;
        }, { replace: true });
      })
      .catch((error: unknown) => {
        if (!activeRequest) return;
        setLinkedInterviewSource(null);
        setSourceError(error instanceof Error ? error.message : "면접 답변을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (activeRequest) setSourceLoading(false);
      });
    return () => { activeRequest = false; };
  }, [activeTab, requestedSourceRefId, setSearchParams]);

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

  const updateSearchParam = (key: "tab" | "caseId" | "sourceRefId", value: string | null) => {
    if (key === "tab" && section && value && CORRECTION_TABS.includes(value as CorrectionTab)) {
      const next = new URLSearchParams(searchParams);
      next.delete("tab");
      const query = next.toString();
      navigate(`${CORRECTION_SECTION_PATHS[value as CorrectionTab]}${query ? `?${query}` : ""}`);
      return;
    }
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (value) next.set(key, value);
      else next.delete(key);
      return next;
    });
  };

  const updateDraft = (key: keyof CorrectionDraft, value: string) => {
    setDrafts((current) => ({
      ...current,
      [activeTab]: { ...current[activeTab], [key]: value },
    }));
    setValidationError(null);
  };

  const clearActiveDraft = () => {
    clearCorrectionDraft(draftStorage, userId, activeTab);
    setDrafts((current) => ({
      ...current,
      [activeTab]: emptyCorrectionDraft(),
    }));
    setValidationError(null);
    setProfileNotice(null);
    setSourceError(null);
    if (linkedInterviewSource || requestedSourceRefId !== null) {
      setLinkedInterviewSource(null);
      updateSearchParam("sourceRefId", null);
    }
  };

  const handleResetDraft = () => {
    if (
      (draft.originalText.trim() || draft.questionText.trim() || linkedInterviewSource)
      && !window.confirm(`${active.title} 작성 내용을 초기화할까요?`)
    ) {
      return;
    }
    clearActiveDraft();
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

    const result = await submit({
      correctionType: CORRECTION_TYPE_BY_TAB[activeTab],
      applicationCaseId: selectedCaseId ?? undefined,
      originalText,
      sourceType: linkedInterviewSource ? "INTERVIEW_ANSWER" : "DIRECT_INPUT",
      sourceRefId: linkedInterviewSource?.sourceRefId,
      questionText: active.questionLabel && draft.questionText.trim() ? draft.questionText.trim() : undefined,
    }, correctionModel);
    if (result) clearActiveDraft();
  };

  const invalidRequestedCase = !casesLoading && requestedCaseId !== null && selectedCaseId === null;
  const visibleError = validationError ?? submitError;
  const detachInterviewSource = () => {
    setLinkedInterviewSource(null);
    updateSearchParam("sourceRefId", null);
  };

  const handleProfileLoad = async () => {
    setProfileNotice(null);
    setProfileLoading(true);
    try {
      const data = profileData ?? (await getProfile());
      setProfileData(data);
      const text = profilePrefillText(data, activeTab);
      if (!text) {
        setProfileNotice(`프로필에 저장된 ${active.title.replace(" 첨삭", "")} 내용이 없습니다. 내 프로필에서 먼저 작성해 주세요.`);
        return;
      }
      if (
        draft.originalText.trim() &&
        draft.originalText.trim() !== text &&
        !window.confirm("작성 중인 원문을 프로필 내용으로 덮어씁니다. 계속할까요?")
      ) {
        return;
      }
      updateDraft("originalText", text.slice(0, ORIGINAL_TEXT_MAX_LENGTH));
      setProfileNotice("프로필에서 불러왔습니다. 내용을 확인·수정한 뒤 실행하세요.");
    } catch (error: unknown) {
      setProfileNotice(error instanceof Error ? error.message : "프로필을 불러오지 못했습니다.");
    } finally {
      setProfileLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6 lg:px-8">
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
                      disabled={submitting || Boolean(linkedInterviewSource) || sourceLoading}
                    />
                    <p className="text-right text-xs text-slate-400">{draft.questionText.length.toLocaleString("ko-KR")} / {QUESTION_TEXT_MAX_LENGTH.toLocaleString("ko-KR")}</p>
                  </div>
                )}

                <div className="space-y-2">
                  <div className="flex items-center justify-between gap-3">
                    <label className="text-sm font-bold text-slate-800" htmlFor="correction-original">첨삭할 원문</label>
                    {activeTab !== "answer" && (
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => void handleProfileLoad()}
                        disabled={submitting || profileLoading}
                      >
                        {profileLoading ? <LoaderCircle className="size-3.5 animate-spin" /> : <UserRound className="size-3.5" />}
                        프로필에서 불러오기
                      </Button>
                    )}
                  </div>
                  <Textarea
                    id="correction-original"
                    value={draft.originalText}
                    maxLength={ORIGINAL_TEXT_MAX_LENGTH}
                    rows={12}
                    onChange={(event) => updateDraft("originalText", event.target.value)}
                    placeholder={active.placeholder}
                    className="min-h-64 resize-y leading-6"
                    disabled={submitting || Boolean(linkedInterviewSource) || sourceLoading}
                    aria-invalid={Boolean(visibleError)}
                  />
                  <div className="flex items-center justify-between gap-3 text-xs text-slate-400">
                    <span>지원 건을 연결하면 공고와 직무 맥락을 함께 반영합니다.</span>
                    <span className="shrink-0">{draft.originalText.length.toLocaleString("ko-KR")} / {ORIGINAL_TEXT_MAX_LENGTH.toLocaleString("ko-KR")}</span>
                  </div>
                  {sourceLoading && <p className="text-xs text-blue-700">면접 답변 원문을 불러오는 중입니다.</p>}
                  {sourceError && <p className="text-xs text-red-600">{sourceError}</p>}
                  {profileNotice && <p className="text-xs text-slate-500">{profileNotice}</p>}
                  {activeTab === "answer" && !linkedInterviewSource && !sourceLoading && (
                    <p className="text-xs text-slate-400">면접 연습의 답변 첨삭에서 열면 답변과 질문이 자동으로 채워집니다.</p>
                  )}
                  {linkedInterviewSource && (
                    <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-2 text-xs text-indigo-800">
                      <span>면접 답변 #{linkedInterviewSource.sourceRefId} 원문·평가 맥락을 사용합니다.</span>
                      <Button type="button" size="sm" variant="outline" onClick={detachInterviewSource}>연결 해제</Button>
                    </div>
                  )}
                </div>

                {visibleError && (
                  <Alert variant="destructive">
                    <AlertCircle />
                    <AlertTitle>첨삭을 진행하지 못했습니다</AlertTitle>
                    <AlertDescription>{visibleError}</AlertDescription>
                  </Alert>
                )}

                <div className="flex flex-wrap items-center gap-3">
                  <Button type="button" onClick={() => void handleSubmit()} disabled={submitting || sourceLoading || !draft.originalText.trim()}>
                    {submitting ? <LoaderCircle className="size-4 animate-spin" /> : <Sparkles className="size-4" />}
                    {submitting ? "첨삭 중" : `${active.title} 실행`}
                  </Button>
                  <ModelPicker value={correctionModel} onChange={setCorrectionModel} disabled={submitting} />
                  <Button
                    type="button"
                    variant="outline"
                    onClick={handleResetDraft}
                    disabled={submitting || sourceLoading || (!draft.originalText && !draft.questionText && !linkedInterviewSource)}
                  >
                    <RotateCcw className="size-4" />
                    입력 초기화
                  </Button>
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
                  deletingId={deletingId}
                  error={historyError}
                  onSelect={(id) => void selectHistory(id)}
                  onRetry={() => void loadHistory()}
                  onDelete={(id) => void remove(id)}
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

function profilePrefillText(profile: UserProfile, tab: CorrectionTab): string {
  if (tab === "cover") return (profile.selfIntro ?? "").trim();
  if (tab === "resume") return (profile.resumeText ?? "").trim();
  if (tab === "portfolio") return composePortfolioText(profile);
  return "";
}

/** 프로필 projects(JSON 배열)·portfolioLinks 를 첨삭 원문으로 합성. 필드가 없거나 형태가 달라도 조용히 건너뛴다. */
function composePortfolioText(profile: UserProfile): string {
  const projects = Array.isArray(profile.projects) ? profile.projects : [];
  const blocks = projects
    .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    .map((item) => {
      const text = (key: string) => (typeof item[key] === "string" ? (item[key] as string).trim() : "");
      const head = [text("title"), text("type"), text("role"), text("period")].filter(Boolean).join(" · ");
      const result = text("result");
      const body = [text("description"), result ? `성과: ${result}` : ""].filter(Boolean).join("\n");
      return [head, body].filter(Boolean).join("\n");
    })
    .filter(Boolean);
  const links = Array.isArray(profile.portfolioLinks)
    ? profile.portfolioLinks.filter((link): link is string => typeof link === "string" && link.trim() !== "")
    : [];
  if (links.length > 0) {
    blocks.push(`포트폴리오 링크: ${links.join(", ")}`);
  }
  return blocks.join("\n\n").trim();
}
