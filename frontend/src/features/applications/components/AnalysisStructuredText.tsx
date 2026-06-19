import { parseJsonArrayOrText } from "../types/analysis";

interface AnalysisStructuredTextProps {
  title: string;
  value: string | null;
  tone?: "default" | "blue";
}

export function AnalysisStructuredText({
  title,
  value,
  tone = "default",
}: AnalysisStructuredTextProps) {
  const parsed = parseJsonArrayOrText(value);
  const isBlue = tone === "blue";

  return (
    <div className={`rounded-lg border p-4 ${isBlue ? "border-blue-100 bg-blue-50" : "border-slate-200 bg-card"}`}>
      <div className={`text-sm font-semibold ${isBlue ? "text-blue-950" : "text-slate-900"}`}>{title}</div>
      {parsed.kind === "list" ? (
        <ul className={`mt-2 space-y-1.5 text-sm leading-6 ${isBlue ? "text-blue-900" : "text-slate-600"}`}>
          {parsed.items.map((item, index) => (
            <li key={`${item}-${index}`} className="flex gap-2">
              <span className="mt-2 size-1.5 shrink-0 rounded-full bg-current" />
              <span className="min-w-0 break-words">{item}</span>
            </li>
          ))}
        </ul>
      ) : parsed.kind === "text" ? (
        <p className={`mt-2 whitespace-pre-line text-sm leading-6 ${isBlue ? "text-blue-900" : "text-slate-600"}`}>
          {parsed.text}
        </p>
      ) : (
        <div className={`mt-2 text-sm ${isBlue ? "text-blue-300" : "text-slate-400"}`}>내용 없음</div>
      )}
    </div>
  );
}
