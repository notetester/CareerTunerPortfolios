import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { MessageSquare } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/app/components/ui/tabs";
import { Badge } from "@/app/components/ui/badge";
import { useAuth } from "@/app/auth/AuthContext";
import { useApplicationCases } from "@/features/applications/hooks/useApplicationCases";
import { ModeSelectTab } from "../components/ModeSelectTab";
import { AutoSetupPanel } from "../components/AutoSetupPanel";
import { ExpectedQuestionsTab } from "../components/ExpectedQuestionsTab";
import { PracticeTab } from "../components/PracticeTab";
import { VoiceInterviewTab } from "../components/VoiceInterviewTab";
import { AvatarInterviewTab } from "../components/AvatarInterviewTab";
import { EvaluationCriteriaTab } from "../components/EvaluationCriteriaTab";
import { CorrectionInfoTab } from "../components/CorrectionInfoTab";
import { InterviewReportTab } from "../components/InterviewReportTab";
import { useTutorialStore } from "../tutorial/tutorialStore";
import { dummySession } from "../tutorial/dummyData";
import { TutorialOverlay } from "../tutorial/TutorialOverlay";
import { TUT_STEPS } from "../tutorial/tutSteps";
import { markSessionResumed } from "../api/interviewApi";
import { getInterviewModeLabel } from "../types/interview";
import type { InterviewMode, InterviewSession } from "../types/interview";

const INTERVIEW_TABS = [
  "modes",
  "questions",
  "practice",
  "live",
  "avatar",
  "evaluation",
  "correction",
  "report",
] as const;
type InterviewTab = (typeof INTERVIEW_TABS)[number];

export function InterviewPage() {
  const { isAuthenticated } = useAuth();
  const mode = useTutorialStore((s) => s.mode);
  const tutStep = useTutorialStore((s) => s.step);
  const startTutorial = useTutorialStore((s) => s.startTutorial);
  const startDemo = useTutorialStore((s) => s.startDemo);
  const stopDemo = useTutorialStore((s) => s.stop);
  const notifyTab = useTutorialStore((s) => s.notifyTab);
  const mockActive = mode !== "off"; // 데모/튜토리얼 둘 다 더미·게이트 우회
  const isTutorial = mode === "tutorial"; // 풍선·자동 진행은 튜토리얼만
  const [searchParams, setSearchParams] = useSearchParams();

  // 모드 선택 탭과 질문/리포트 탭이 공유하는 상태.
  const [selectedMode, setSelectedMode] = useState<InterviewMode | null>(null);
  const [selectedCaseId, setSelectedCaseId] = useState<number | null>(null);
  const [activeSession, setActiveSession] = useState<InterviewSession | null>(null);
  // 현재 활성 세션이 새로 시작한 것인지(new), 과거 기록을 복원(복습)한 것인지(resumed).
  const [sessionOrigin, setSessionOrigin] = useState<"new" | "resumed" | null>(null);
  // 홈 마누스 검색창에서 넘어온 요청(자동 셋업 진입).
  const [autoPrompt] = useState(() => sessionStorage.getItem("interview.autoPrompt") ?? "");

  const cases = useApplicationCases(isAuthenticated);

  const requested = searchParams.get("tab") ?? "modes";
  const activeTab: InterviewTab = INTERVIEW_TABS.includes(requested as InterviewTab)
    ? (requested as InterviewTab)
    : "modes";
  const autoMode = searchParams.get("auto") === "1" && autoPrompt.length > 0;

  const goTab = (tab: string) => setSearchParams(tab === "modes" ? {} : { tab });

  const wantTutorial = searchParams.get("tutorial") === "1";
  const wantDemo = searchParams.get("demo") === "1";

  // 데모/튜토리얼 모드에서는 실제 세션 없이도 더미 세션으로 흐름을 보여준다.
  const effectiveSession = mockActive ? (activeSession ?? dummySession) : activeSession;
  const activeCase = effectiveSession
    ? cases.applicationCases.find((c) => c.id === effectiveSession.applicationCaseId)
    : undefined;
  // 실제 진행 중 세션 라벨(데모/튜토리얼 제외). 새 면접 시작 시 전환 경고용.
  const activeSessionLabel =
    !mockActive && effectiveSession
      ? `${activeCase ? `${activeCase.companyName} · ${activeCase.jobTitle} · ` : ""}${getInterviewModeLabel(effectiveSession.mode)}`
      : null;

  // ?tutorial=1 / ?demo=1 로 진입하면 해당 모드를 자동 시작한다.
  useEffect(() => {
    if (mode !== "off") return;
    if (wantTutorial) startTutorial();
    else if (wantDemo) startDemo();
  }, [wantTutorial, wantDemo, mode, startTutorial, startDemo]);

  // 튜토리얼: 현재 step 에 tab 이 지정돼 있으면 그 탭으로 자동 전환한다.
  useEffect(() => {
    if (!isTutorial) return;
    const target = TUT_STEPS[tutStep]?.tab;
    if (target && target !== activeTab) goTab(target);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isTutorial, tutStep]);

  // 튜토리얼: 사용자의 탭 이동을 엔진에 알린다 (awaitTab step 자동 진행).
  useEffect(() => {
    if (isTutorial) notifyTab(activeTab);
  }, [activeTab, isTutorial, notifyTab]);

  if (!isAuthenticated && !mockActive && !wantTutorial && !wantDemo) {
    return (
      <div className="min-h-[calc(100vh-72px)] bg-slate-50 px-4 py-12">
        <div className="mx-auto max-w-md rounded-xl border border-slate-200 bg-card p-8 text-center shadow-sm">
          <div className="mx-auto flex size-12 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
            <MessageSquare className="size-6" />
          </div>
          <h1 className="mt-4 text-xl font-bold text-slate-900">AI 가상 면접</h1>
          <p className="mt-1 text-sm leading-6 text-slate-500">
            로그인 없이 먼저 둘러볼 수 있어요. 가이드와 함께 전체 흐름을 살펴보세요.
          </p>
          <div className="mt-5 flex flex-col gap-2">
            <button
              onClick={startTutorial}
              className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-indigo-700"
            >
              가이드와 둘러보기
            </button>
            <a href="/login" className="mt-1 text-sm text-slate-500 transition-colors hover:text-slate-700">
              로그인하고 실제 면접 시작 →
            </a>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[1400px] space-y-6 px-4 py-6 md:space-y-8 md:px-6 md:py-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="flex items-center gap-2 text-xl font-black text-slate-900 md:text-2xl">
              <MessageSquare className="size-5 text-blue-600 md:size-6" />
              AI 가상 면접
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              지원 건과 모드를 고르면 AI가 질문을 만들고, 답변을 평가해 리포트를 제공합니다
            </p>
          </div>
          <div className="flex shrink-0 gap-2">
            {mode === "off" ? (
              <button
                onClick={startTutorial}
                className="rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-2 text-sm font-semibold text-indigo-600 transition-colors hover:bg-indigo-100"
              >
                둘러보기
              </button>
            ) : (
              <button
                onClick={stopDemo}
                className="rounded-lg border border-slate-300 bg-card px-3 py-2 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50"
              >
                {mode === "demo" ? "체험 종료" : "둘러보기 종료"}
              </button>
            )}
          </div>
        </div>

        {effectiveSession && (
          <div className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-200 bg-card px-4 py-2.5">
            <span className="text-xs text-slate-400">현재 진행</span>
            <span className="text-sm font-semibold text-slate-800">
              {activeCase ? `${activeCase.companyName} · ${activeCase.jobTitle} · ` : ""}
              {getInterviewModeLabel(effectiveSession.mode)}
            </span>
            {sessionOrigin === "resumed" && <Badge className="bg-indigo-100 text-indigo-700">복습 중</Badge>}
            {sessionOrigin === "new" && <Badge className="bg-green-100 text-green-700">새 세션</Badge>}
          </div>
        )}

        <Tabs value={activeTab} onValueChange={goTab}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-card p-1">
            <TabsTrigger value="modes" data-tut="tut-tab-modes">면접 모드 선택</TabsTrigger>
            <TabsTrigger value="questions" data-tut="tut-tab-questions">예상 면접 질문</TabsTrigger>
            <TabsTrigger value="practice" data-tut="tut-tab-practice">복습 테스트</TabsTrigger>
            <TabsTrigger value="live" data-tut="tut-tab-live">음성 모의면접</TabsTrigger>
            <TabsTrigger value="avatar" data-tut="tut-tab-avatar">아바타 화상 면접</TabsTrigger>
            <TabsTrigger value="evaluation" data-tut="tut-tab-evaluation">답변 평가 기준</TabsTrigger>
            <TabsTrigger value="correction" data-tut="tut-tab-correction">AI 첨삭</TabsTrigger>
            <TabsTrigger value="report" data-tut="tut-tab-report">면접 리포트</TabsTrigger>
          </TabsList>

          <TabsContent value="modes" data-tut="tut-panel-modes" className="mt-6">
            {autoMode ? (
              <AutoSetupPanel
                cases={cases.applicationCases}
                casesLoading={cases.loading}
                prompt={autoPrompt}
                onReady={(session) => {
                  setActiveSession(session);
                  setSessionOrigin("new");
                  sessionStorage.removeItem("interview.autoPrompt");
                  goTab("practice");
                }}
                onManual={() => setSearchParams({})}
              />
            ) : (
              <ModeSelectTab
                cases={cases.applicationCases}
                casesLoading={cases.loading}
                casesError={cases.error}
                selectedCaseId={selectedCaseId}
                selectedMode={selectedMode}
                onSelectCase={setSelectedCaseId}
                onSelectMode={setSelectedMode}
                onSessionStarted={(session) => {
                  setActiveSession(session);
                  setSessionOrigin("new");
                  goTab("questions");
                }}
                onResume={(session) => {
                  setActiveSession(session);
                  setSessionOrigin("resumed");
                  setSelectedCaseId(session.applicationCaseId);
                  setSelectedMode(session.mode);
                  // 복원 = 복습. 마지막 복습 시각을 기록한다(실패해도 흐름은 진행).
                  void markSessionResumed(session.id);
                  goTab("questions");
                }}
                activeSessionLabel={activeSessionLabel}
              />
            )}
          </TabsContent>

          <TabsContent value="questions" className="mt-6">
            <ExpectedQuestionsTab session={effectiveSession} onGoToPractice={() => goTab("practice")} />
          </TabsContent>

          <TabsContent value="practice" data-tut="tut-panel-practice" className="mt-6">
            <PracticeTab session={effectiveSession} onGoToReport={() => goTab("report")} />
          </TabsContent>

          <TabsContent value="live" data-tut="tut-panel-live" className="mt-6">
            <VoiceInterviewTab session={effectiveSession} />
          </TabsContent>

          <TabsContent value="avatar" data-tut="tut-panel-avatar" className="mt-6">
            <AvatarInterviewTab session={effectiveSession} />
          </TabsContent>

          <TabsContent value="evaluation" data-tut="tut-panel-evaluation" className="mt-6">
            <EvaluationCriteriaTab />
          </TabsContent>

          <TabsContent value="correction" data-tut="tut-panel-correction" className="mt-6">
            <CorrectionInfoTab />
          </TabsContent>

          <TabsContent value="report" className="mt-6">
            <InterviewReportTab session={effectiveSession} />
          </TabsContent>
        </Tabs>
      </div>
      <TutorialOverlay />
    </div>
  );
}
