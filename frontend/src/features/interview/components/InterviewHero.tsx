import { useState } from "react";
import { useNavigate } from "react-router";
import { ArrowRight, Sparkles, Wand2 } from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";

const EXAMPLES = [
  "네이버 백엔드 직무 면접 준비해줘",
  "압박 면접 연습하고 싶어",
  "인성 면접 대비하게 도와줘",
];

/**
 * 마누스형 면접 진입 검색창. 한 줄 요청을 던지면 자동 셋업(요청 분석 → 모드 선정 → 세션·질문 생성)
 * 으로 넘어간다. 요청 문장은 sessionStorage 로 전달하고 /interview?auto=1 로 이동한다.
 */
export function InterviewHero() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [value, setValue] = useState("");

  const submit = (text: string) => {
    const prompt = text.trim();
    if (!prompt) return;
    sessionStorage.setItem("interview.autoPrompt", prompt);
    navigate(isAuthenticated ? "/interview?auto=1" : "/login");
  };

  return (
    <section className="relative overflow-hidden rounded-2xl border border-border bg-card p-6 sm:p-8 shadow-[var(--shadow-card)]">
      <div className="pointer-events-none absolute -right-16 -top-16 size-56 rounded-full bg-primary/10 blur-3xl" />
      <div className="relative space-y-4">
        <div className="flex items-center gap-2 text-xs font-bold text-primary">
          <Sparkles className="size-4" /> AI 면접 에이전트
        </div>
        <h2 className="text-xl font-semibold leading-snug tracking-tight text-foreground sm:text-2xl">
          한 줄이면 끝. <span className="text-primary">AI가 면접을 통째로 준비합니다.</span>
        </h2>
        <p className="text-sm text-muted-foreground">
          원하는 면접을 말로 던지면 에이전트가 모드 선정부터 질문 생성까지 알아서 진행합니다.
        </p>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            submit(value);
          }}
          className="flex flex-col gap-2 sm:flex-row"
        >
          <div className="relative flex-1">
            <Wand2 className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <input
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder="예: 네이버 백엔드 직무 면접 준비해줘"
              className="w-full rounded-xl border border-border bg-background py-3 pl-9 pr-3 text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary focus:ring-2 focus:ring-ring/40"
            />
          </div>
          <button
            type="submit"
            disabled={!value.trim()}
            className="flex items-center justify-center gap-1.5 rounded-xl bg-primary px-5 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:bg-secondary disabled:text-muted-foreground"
          >
            맡기기 <ArrowRight className="size-4" />
          </button>
        </form>

        <div className="flex flex-wrap gap-2">
          {EXAMPLES.map((ex) => (
            <button
              key={ex}
              type="button"
              onClick={() => submit(ex)}
              className="rounded-full border border-border bg-secondary px-3 py-1 text-xs text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
            >
              {ex}
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}
