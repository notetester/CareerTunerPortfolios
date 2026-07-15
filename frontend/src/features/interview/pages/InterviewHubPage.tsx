import { useMemo } from "react";
import { Link, useSearchParams } from "react-router";
import {
  ArrowRight,
  BarChart3,
  ClipboardCheck,
  FileQuestion,
  Loader2,
  MessageSquare,
  Mic,
  PlayCircle,
  RefreshCw,
  Settings2,
  Video,
  type LucideIcon,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { useAuth } from "@/app/auth/AuthContext";
import { useApplicationCases } from "@/features/applications/hooks/useApplicationCases";
import { useInterviewSessions } from "../hooks/useInterviewSessions";
import {
  INTERVIEW_SECTIONS,
  INTERVIEW_SECTION_META,
  INTERVIEW_SECTION_PATHS,
  interviewSectionHref,
  parseInterviewTab,
  type InterviewSection,
} from "../lib/interviewSections";
import { getInterviewModeLabel, getScoreColor, type InterviewSession } from "../types/interview";
import { InterviewPage } from "./InterviewPage";

const sectionIcons: Record<InterviewSection, LucideIcon> = {
  modes: Settings2,
  questions: FileQuestion,
  practice: ClipboardCheck,
  live: Mic,
  avatar: Video,
  evaluation: MessageSquare,
  report: BarChart3,
};

const legacyQueryKeys = ["tutorial", "demo", "auto", "session", "caseId"] as const;

/** 인터뷰 대분류 허브. 기존 query 기반 딥링크는 기존 화면에 위임해 호환한다. */
export function InterviewHubPage() {
  const [searchParams] = useSearchParams();
  const isLegacyEntry = parseInterviewTab(searchParams.get("tab")) != null
    || legacyQueryKeys.some((key) => searchParams.has(key));

  return isLegacyEntry ? <InterviewPage /> : <InterviewHubContent />;
}

function InterviewHubContent() {
  const { user, isAuthenticated } = useAuth();
  const cases = useApplicationCases(isAuthenticated, false, user?.id ?? null);
  const sessions = useInterviewSessions(isAuthenticated);
  const caseById = useMemo(
    () => new Map(cases.applicationCases.map((item) => [item.id, item])),
    [cases.applicationCases],
  );
  const recentSessions = sessions.sessions.slice(0, 5);
  const latestSession = recentSessions[0] ?? null;
  const continueHref = latestSession
    ? interviewSectionHref("questions", {
        sessionId: latestSession.id,
        caseId: latestSession.applicationCaseId,
      })
    : INTERVIEW_SECTION_PATHS.modes;

  return (
    <main className="min-h-[calc(100vh-72px)] bg-background">
      <div className="mx-auto w-full max-w-[1440px] space-y-7 px-4 py-8 sm:px-6 lg:px-8">
        <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-black text-foreground">
              <MessageSquare className="size-6 text-primary" />
              AI 가상 면접
            </h1>
            <p className="mt-1 text-sm text-muted-foreground">
              면접 준비 단계별 기능을 선택하거나 최근 세션에서 바로 이어갑니다.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void cases.refresh();
                void sessions.refresh();
              }}
              disabled={cases.loading || sessions.loading}
            >
              <RefreshCw className={`size-4 ${cases.loading || sessions.loading ? "animate-spin" : ""}`} />
              새로고침
            </Button>
            {latestSession && (
              <Button asChild variant="outline">
                <Link to={continueHref}>
                  <PlayCircle className="size-4" /> 최근 면접 이어보기
                </Link>
              </Button>
            )}
            <Button asChild>
              <Link to={INTERVIEW_SECTION_PATHS.modes}>
                <Settings2 className="size-4" /> 새 면접 시작
              </Link>
            </Button>
          </div>
        </header>

        {(cases.error || sessions.error) && (
          <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {cases.error ?? sessions.error}
          </div>
        )}

        <section>
          <div className="mb-3 flex flex-wrap items-end justify-between gap-2">
            <div>
              <h2 className="text-lg font-bold text-foreground">면접 기능</h2>
              <p className="mt-0.5 text-xs text-muted-foreground">각 기능은 독립된 화면으로 열리고, 선택한 세션은 화면을 옮겨도 유지됩니다.</p>
            </div>
            {latestSession && <Badge variant="secondary">최근 세션 #{latestSession.id} 연결 가능</Badge>}
          </div>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {INTERVIEW_SECTIONS.map((section) => {
              const meta = INTERVIEW_SECTION_META[section];
              const Icon = sectionIcons[section];
              const href = section === "modes" || !latestSession
                ? INTERVIEW_SECTION_PATHS[section]
                : interviewSectionHref(section, {
                    sessionId: latestSession.id,
                    caseId: latestSession.applicationCaseId,
                  });

              return (
                <Link
                  key={section}
                  to={href}
                  className="group rounded-xl border border-border bg-card p-5 shadow-[var(--shadow-card)] transition hover:-translate-y-0.5 hover:border-primary/50"
                >
                  <div className="flex items-start justify-between gap-3">
                    <span className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                      <Icon className="size-5" />
                    </span>
                    <ArrowRight className="size-4 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-primary" />
                  </div>
                  <h3 className="mt-4 font-bold text-foreground group-hover:text-primary">{meta.label}</h3>
                  <p className="mt-1 min-h-12 text-sm leading-6 text-muted-foreground">{meta.description}</p>
                  <div className="mt-3 text-xs font-semibold text-primary">
                    {section === "modes"
                      ? "지원 건·모드 선택"
                      : latestSession
                        ? "최근 세션으로 열기"
                        : "세션 선택 후 열기"}
                  </div>
                </Link>
              );
            })}
          </div>
        </section>

        <section>
          <div className="mb-3 flex items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-foreground">최근 면접 세션</h2>
              <p className="mt-0.5 text-xs text-muted-foreground">실제 저장된 세션을 선택해 질문부터 다시 보거나 리포트를 확인합니다.</p>
            </div>
            {!sessions.loading && <Badge variant="outline">총 {sessions.total.toLocaleString("ko-KR")}개</Badge>}
          </div>
          <Card className="border-border">
            <CardContent className="p-0">
              {sessions.loading ? (
                <div className="flex min-h-64 items-center justify-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="size-5 animate-spin" /> 면접 기록을 불러오는 중입니다.
                </div>
              ) : recentSessions.length > 0 ? (
                <div className="divide-y divide-border">
                  {recentSessions.map((session) => (
                    <RecentSessionRow
                      key={session.id}
                      session={session}
                      companyName={caseById.get(session.applicationCaseId)?.companyName}
                      jobTitle={caseById.get(session.applicationCaseId)?.jobTitle}
                    />
                  ))}
                </div>
              ) : (
                <div className="flex min-h-64 flex-col items-center justify-center px-6 text-center">
                  <MessageSquare className="size-9 text-muted-foreground" />
                  <div className="mt-3 font-semibold text-foreground">아직 면접 세션이 없습니다</div>
                  <p className="mt-1 text-sm text-muted-foreground">지원 건과 면접 모드를 선택하면 세션 기록이 이곳에 쌓입니다.</p>
                  <Button asChild className="mt-4">
                    <Link to={INTERVIEW_SECTION_PATHS.modes}>첫 면접 시작</Link>
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        </section>
      </div>
    </main>
  );
}

function RecentSessionRow({
  session,
  companyName,
  jobTitle,
}: {
  session: InterviewSession;
  companyName?: string;
  jobTitle?: string;
}) {
  const score = session.totalScore ?? session.avgScore;
  const questionsHref = interviewSectionHref("questions", {
    sessionId: session.id,
    caseId: session.applicationCaseId,
  });
  const reportHref = interviewSectionHref("report", {
    sessionId: session.id,
    caseId: session.applicationCaseId,
  });

  return (
    <div className="flex flex-col gap-3 p-4 md:flex-row md:items-center">
      <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
        <PlayCircle className="size-5" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate font-bold text-foreground">
            {companyName && jobTitle ? `${companyName} · ${jobTitle}` : `지원 건 #${session.applicationCaseId}`}
          </span>
          <Badge variant={session.finished ? "secondary" : "outline"}>
            {session.finished ? "답변 완료" : session.answeredQuestions > 0 ? "진행 중" : "준비됨"}
          </Badge>
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
          <span>{getInterviewModeLabel(session.mode)}</span>
          <span>·</span>
          <span>{formatDate(session.startedAt ?? session.createdAt)}</span>
          <span>·</span>
          <span>{session.answeredQuestions}/{session.totalQuestions} 답변</span>
          {score != null && (
            <>
              <span>·</span>
              <span className={`font-black ${getScoreColor(score)}`}>{score}점</span>
            </>
          )}
        </div>
      </div>
      <div className="flex shrink-0 gap-2 pl-12 md:pl-0">
        <Button asChild size="sm" variant="outline">
          <Link to={questionsHref}>질문 보기</Link>
        </Button>
        <Button asChild size="sm">
          <Link to={reportHref}>리포트</Link>
        </Button>
      </div>
    </div>
  );
}

function formatDate(value: string | null | undefined): string {
  if (!value) return "날짜 없음";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(parsed);
}
