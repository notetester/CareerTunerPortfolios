import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { Plus } from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { haptic } from "@/platform/haptics";
import { useApplicationCases } from "@/features/applications/hooks/useApplicationCases";
import { listInterviewSessions } from "../api/interviewApi";
import { getInterviewModeLabel } from "../types/interview";
import type { InterviewSession } from "../types/interview";

/**
 * 모바일 세션 리스트 (하단 탭 "세션") — Claude 앱의 채팅 히스토리 포지션.
 * 탭하면 스레드(/m/session/:id)로 진입해 이어서 진행한다.
 */
export function MobileSessionsPage() {
  const navigate = useNavigate();
  const { isAuthenticated, loading: authLoading } = useAuth();
  const cases = useApplicationCases(isAuthenticated);
  const [sessions, setSessions] = useState<InterviewSession[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (authLoading) return;
    if (!isAuthenticated) {
      navigate("/login");
      return;
    }
    void (async () => {
      try {
        const page = await listInterviewSessions(0, 50);
        setSessions(page.sessions);
      } catch {
        setSessions([]);
      } finally {
        setLoading(false);
      }
    })();
  }, [authLoading, isAuthenticated, navigate]);

  const caseLabel = useMemo(() => {
    const map = new Map(cases.applicationCases.map((c) => [c.id, `${c.companyName} · ${c.jobTitle}`]));
    return (id: number) => map.get(id) ?? `지원건 #${id}`;
  }, [cases.applicationCases]);

  const relTime = (iso: string | null | undefined) => {
    if (!iso) return "";
    const diff = Date.now() - new Date(iso).getTime();
    const min = Math.floor(diff / 60000);
    if (min < 1) return "방금";
    if (min < 60) return `${min}분 전`;
    const hr = Math.floor(min / 60);
    if (hr < 24) return `${hr}시간 전`;
    const day = Math.floor(hr / 24);
    if (day < 7) return `${day}일 전`;
    return new Date(iso).toLocaleDateString("ko-KR", { month: "short", day: "numeric" });
  };

  return (
    <div className="fixed inset-0 z-40 flex flex-col bg-[#050506] text-[#EDEDEF]">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(320px 240px at 50% -60px, rgba(94,106,210,0.10), transparent 70%)",
        }}
      />
      <div
        className="relative flex h-[52px] shrink-0 items-center gap-2 border-b border-white/[0.06] px-4"
        style={{ marginTop: "env(safe-area-inset-top)" }}
      >
        <span className="text-[15px] font-semibold tracking-tight">세션</span>
        <button
          onClick={() => {
            haptic("light");
            navigate("/interview");
          }}
          className="ml-auto flex items-center gap-1.5 rounded-[9px] bg-gradient-to-b from-[#7d88de] to-[#5E6AD2] px-3 py-1.5 text-[12px] font-semibold text-white shadow-[0_0_0_1px_rgba(94,106,210,0.5),0_3px_10px_rgba(94,106,210,0.25)]"
        >
          <Plus className="size-3.5" /> 새 면접 준비
        </button>
      </div>

      <div
        className="relative flex-1 overflow-y-auto"
        style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 64px)" }}
      >
        {loading && (
          <div className="flex items-center justify-center gap-3 py-14 text-[13px] text-[#8A8F98]">
            <span className="size-4 animate-spin rounded-full border-2 border-white/10 border-t-[#5E6AD2]" />
            불러오는 중
          </div>
        )}
        {!loading && sessions.length === 0 && (
          <div className="px-8 py-16 text-center text-[13px] leading-relaxed text-[#8A8F98]">
            아직 세션이 없습니다.
            <br />새 면접 준비로 시작해 보세요.
          </div>
        )}
        <div className="mx-auto max-w-xl px-2 pt-2">
          {sessions.map((s) => {
            const done = s.endedAt != null;
            return (
              <button
                key={s.id}
                onClick={() => {
                  haptic("light");
                  navigate(`/m/session/${s.id}`);
                }}
                className="flex w-full flex-col gap-1 rounded-xl border border-transparent px-3.5 py-3 text-left transition-colors hover:border-white/[0.06] hover:bg-white/[0.04]"
              >
                <span className="truncate text-[13.5px] font-semibold tracking-tight">
                  {caseLabel(s.applicationCaseId)}
                </span>
                <span className="flex items-center gap-2 text-[11.5px] text-[#8A8F98]">
                  <span
                    className={`size-1.5 rounded-full ${
                      done ? "bg-[#4cc38a]" : "bg-[#d6a24c] shadow-[0_0_8px_rgba(214,162,76,0.5)]"
                    }`}
                  />
                  {getInterviewModeLabel(s.mode)}
                  {" · "}
                  {done ? "완료" : "진행 중"}
                  {s.avgScore != null && ` · ${s.avgScore}점`}
                  {" · "}
                  {relTime(s.lastResumedAt ?? s.createdAt)}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
