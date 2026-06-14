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
    <section className="relative overflow-hidden rounded-2xl border border-indigo-200/60 bg-[linear-gradient(135deg,#0f172a_0%,#1e1b4b_55%,#4338ca_100%)] p-6 sm:p-8 text-white shadow-sm">
      <div className="pointer-events-none absolute -right-16 -top-16 size-56 rounded-full bg-indigo-500/20 blur-3xl" />
      <div className="relative space-y-4">
        <div className="flex items-center gap-2 text-xs font-bold text-indigo-200">
          <Sparkles className="size-4" /> AI 면접 에이전트
        </div>
        <h2 className="text-xl font-black leading-snug sm:text-2xl">
          한 줄이면 끝. <span className="text-indigo-300">AI가 면접을 통째로 준비합니다.</span>
        </h2>
        <p className="text-sm text-indigo-100/80">
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
            <Wand2 className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-indigo-300" />
            <input
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder="예: 네이버 백엔드 직무 면접 준비해줘"
              className="w-full rounded-xl border border-white/10 bg-white/10 py-3 pl-9 pr-3 text-sm text-white placeholder:text-indigo-200/60 outline-none backdrop-blur focus:border-indigo-300"
            />
          </div>
          <button
            type="submit"
            disabled={!value.trim()}
            className="flex items-center justify-center gap-1.5 rounded-xl bg-white px-5 py-3 text-sm font-bold text-indigo-700 transition-colors hover:bg-indigo-50 disabled:opacity-50"
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
              className="rounded-full border border-white/15 bg-white/5 px-3 py-1 text-xs text-indigo-100 transition-colors hover:bg-white/10"
            >
              {ex}
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}
