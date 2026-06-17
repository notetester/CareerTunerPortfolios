import { useEffect, useMemo, useState } from "react";
import { Check, ChevronDown, Copy, FileText, RefreshCw, Search } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/app/components/ui/tabs";
import * as promptApi from "../api";
import type { AdminPromptView } from "../types";
import AdminShell from "../../../components/AdminShell";

type PromptTab = {
  value: "job-analysis" | "company-analysis";
  label: string;
  title: string;
  aliases: string[];
};

type PromptFailure = {
  feature: string;
  label: string;
  message: string;
};

type PromptLoadResult = {
  prompts: AdminPromptView[];
  failures: PromptFailure[];
};

type PromptApiWithSettled = typeof promptApi & {
  getBPromptViewsSettled?: () => Promise<unknown>;
  getBPromptViewResults?: () => Promise<unknown>;
  getBPromptViewsAllSettled?: () => Promise<unknown>;
  getSettledBPromptViews?: () => Promise<unknown>;
};

const PROMPT_TABS: PromptTab[] = [
  {
    value: "job-analysis",
    label: "공고",
    title: "공고 분석",
    aliases: ["job-analysis", "job_analysis", "JOB_ANALYSIS", "공고"],
  },
  {
    value: "company-analysis",
    label: "기업",
    title: "기업 분석",
    aliases: ["company-analysis", "company_analysis", "COMPANY_ANALYSIS", "COMPANY_RESEARCH", "기업"],
  },
];

export function AdminPromptsPage() {
  const [prompts, setPrompts] = useState<AdminPromptView[]>([]);
  const [failures, setFailures] = useState<PromptFailure[]>([]);
  const [activeTab, setActiveTab] = useState<PromptTab["value"]>("job-analysis");
  const [query, setQuery] = useState("");
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const promptByTab = useMemo(() => {
    return PROMPT_TABS.reduce<Record<PromptTab["value"], AdminPromptView | null>>((acc, tab) => {
      acc[tab.value] = prompts.find((prompt) => matchesPromptTab(prompt, tab)) ?? null;
      return acc;
    }, { "job-analysis": null, "company-analysis": null });
  }, [prompts]);

  const failureByTab = useMemo(() => {
    return PROMPT_TABS.reduce<Record<PromptTab["value"], PromptFailure | null>>((acc, tab) => {
      acc[tab.value] = failures.find((failure) => matchesFeatureTab(failure.feature, failure.label, tab)) ?? null;
      return acc;
    }, { "job-analysis": null, "company-analysis": null });
  }, [failures]);

  const activePrompt = promptByTab[activeTab];
  const searchQuery = query.trim();
  const activeMatchCount = activePrompt ? countPromptMatches(activePrompt, searchQuery) : 0;
  const loadedCount = PROMPT_TABS.filter((tab) => promptByTab[tab.value]).length;
  const partialFailure = failures.length > 0 && prompts.length > 0;

  const load = async () => {
    setLoading(true);
    setError(null);
    setFailures([]);
    try {
      const apiModule = promptApi as PromptApiWithSettled;
      const loadSettled =
        apiModule.getBPromptViewsSettled ??
        apiModule.getBPromptViewResults ??
        apiModule.getBPromptViewsAllSettled ??
        apiModule.getSettledBPromptViews;
      const result = await (loadSettled ? loadSettled() : promptApi.getBPromptViews());
      const normalized = normalizePromptResult(result);

      setPrompts(sortPrompts(normalized.prompts));
      setFailures(normalized.failures);
    } catch (err) {
      setPrompts([]);
      setFailures([]);
      setError(err instanceof Error ? err.message : "프롬프트를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const handleCopy = async (value: string, key: string) => {
    if (!value) return;

    try {
      await navigator.clipboard.writeText(value);
      setCopiedKey(key);
      window.setTimeout(() => {
        setCopiedKey((current) => (current === key ? null : current));
      }, 1400);
    } catch {
      setError("클립보드에 복사하지 못했습니다.");
    }
  };

  return (
    <AdminShell
      active="prompts"
      breadcrumb="프롬프트 템플릿"
      title="B 프롬프트 확인"
      icon={FileText}
      desc="공고 분석과 기업 분석에 적용되는 프롬프트와 출력 스키마를 확인합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-5">
        {error && <Alert tone="red" title="불러오기 실패" message={error} />}
        {partialFailure && (
          <Alert
            tone="amber"
            title="일부 프롬프트를 불러오지 못했습니다"
            message={failures.map((failure) => `${failure.label}: ${failure.message}`).join(" · ")}
          />
        )}

        <Card className="border-slate-200 bg-card">
          <CardContent className="p-4">
            <div className="grid gap-3 lg:grid-cols-[auto_minmax(260px,420px)] lg:items-center lg:justify-between">
              <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as PromptTab["value"])}>
                <TabsList className="w-full rounded-lg lg:w-auto">
                  {PROMPT_TABS.map((tab) => (
                    <TabsTrigger key={tab.value} value={tab.value} className="min-w-24 rounded-md">
                      {tab.label}
                      {failureByTab[tab.value] && <span className="size-1.5 rounded-full bg-red-500" aria-label="불러오기 실패" />}
                    </TabsTrigger>
                  ))}
                </TabsList>
              </Tabs>
              <label className="space-y-1.5 text-sm font-semibold text-slate-700">
                <span>프롬프트 내 검색</span>
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="System Prompt 또는 스키마 검색"
                    className="pl-9"
                  />
                </div>
              </label>
            </div>
            <div className="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
              <span>로드됨 {loadedCount}/{PROMPT_TABS.length}</span>
              {searchQuery && <span>현재 탭 검색 결과 {activeMatchCount}개</span>}
            </div>
          </CardContent>
        </Card>

        <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as PromptTab["value"])}>
          {PROMPT_TABS.map((tab) => {
            const prompt = promptByTab[tab.value];
            const failure = failureByTab[tab.value];

            return (
              <TabsContent key={tab.value} value={tab.value} className="mt-0">
                {loading ? (
                  <div className="h-64 animate-pulse rounded-lg bg-slate-200" />
                ) : prompt ? (
                  <PromptDetail
                    prompt={prompt}
                    query={searchQuery}
                    matchCount={activeTab === tab.value ? activeMatchCount : countPromptMatches(prompt, searchQuery)}
                    copiedKey={copiedKey}
                    onCopy={handleCopy}
                  />
                ) : failure ? (
                  <EmptyState title={`${tab.title} 프롬프트를 불러오지 못했습니다.`} message={failure.message} tone="red" />
                ) : (
                  <EmptyState title={`${tab.title} 프롬프트가 없습니다.`} message="API 응답에 표시할 프롬프트 템플릿이 없습니다." />
                )}
              </TabsContent>
            );
          })}
        </Tabs>
      </div>
    </AdminShell>
  );
}

function PromptDetail({
  prompt,
  query,
  matchCount,
  copiedKey,
  onCopy,
}: {
  prompt: AdminPromptView;
  query: string;
  matchCount: number;
  copiedKey: string | null;
  onCopy: (value: string, key: string) => void | Promise<void>;
}) {
  return (
    <Card className="border-slate-200 bg-card">
      <CardHeader>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <CardTitle className="text-lg text-slate-950">{prompt.name}</CardTitle>
            <p className="mt-1 text-sm leading-6 text-slate-500">{prompt.purpose}</p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <Badge className="bg-blue-100 text-blue-700">{prompt.version}</Badge>
            {query && (
              <Badge variant="outline" className={matchCount > 0 ? "border-emerald-200 text-emerald-700" : "border-slate-200 text-slate-500"}>
                {matchCount} matches
              </Badge>
            )}
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {query && matchCount === 0 && (
          <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
            현재 탭의 프롬프트와 스키마에 검색어가 없습니다.
          </div>
        )}
        <PromptBlock
          title="출력 스키마"
          value={prompt.schemaSummary || "-"}
          query={query}
          tone="light"
          copyKey={`${prompt.feature}-schema`}
          copiedKey={copiedKey}
          onCopy={onCopy}
        />
        <PromptBlock
          title="System Prompt"
          value={prompt.systemPrompt || "-"}
          query={query}
          tone="dark"
          copyKey={`${prompt.feature}-system`}
          copiedKey={copiedKey}
          onCopy={onCopy}
        />
      </CardContent>
    </Card>
  );
}

function PromptBlock({
  title,
  value,
  query,
  tone,
  copyKey,
  copiedKey,
  onCopy,
}: {
  title: string;
  value: string;
  query: string;
  tone: "light" | "dark";
  copyKey: string;
  copiedKey: string | null;
  onCopy: (value: string, key: string) => void | Promise<void>;
}) {
  const [open, setOpen] = useState(true);
  const copied = copiedKey === copyKey;
  const preClassName =
    tone === "dark"
      ? "border-slate-200 bg-slate-950 text-slate-100"
      : "border-slate-200 bg-slate-50 text-slate-700";

  return (
    <section className="rounded-lg border border-slate-200">
      <div className="flex items-center justify-between gap-2 border-b border-slate-200 px-3 py-2">
        <button
          type="button"
          className="flex min-w-0 items-center gap-2 text-left text-sm font-semibold text-slate-800"
          aria-expanded={open}
          onClick={() => setOpen((current) => !current)}
        >
          <ChevronDown className={`size-4 shrink-0 transition-transform ${open ? "" : "-rotate-90"}`} />
          <span className="truncate">{title}</span>
        </button>
        <Button size="sm" variant="outline" onClick={() => void onCopy(value, copyKey)}>
          {copied ? <Check className="size-4 text-emerald-600" /> : <Copy className="size-4" />}
          {copied ? "복사됨" : "복사"}
        </Button>
      </div>
      {open && (
        <pre className={`max-h-[520px] overflow-auto whitespace-pre-wrap break-words rounded-b-lg border-t-0 p-4 text-xs leading-5 ${preClassName}`}>
          <HighlightedText text={value} query={query} />
        </pre>
      )}
    </section>
  );
}

function HighlightedText({ text, query }: { text: string; query: string }) {
  if (!query) return <>{text}</>;

  const escaped = escapeRegExp(query);
  const parts = text.split(new RegExp(`(${escaped})`, "gi"));
  const normalizedQuery = query.toLowerCase();

  return (
    <>
      {parts.map((part, index) =>
        part.toLowerCase() === normalizedQuery ? (
          <mark key={`${part}-${index}`} className="rounded bg-yellow-200 px-0.5 text-slate-950">
            {part}
          </mark>
        ) : (
          <span key={`${part}-${index}`}>{part}</span>
        ),
      )}
    </>
  );
}

function Alert({ tone, title, message }: { tone: "red" | "amber"; title: string; message: string }) {
  const className =
    tone === "red"
      ? "border-red-200 bg-red-50 text-red-700"
      : "border-amber-200 bg-amber-50 text-amber-800";

  return (
    <div className={`rounded-lg border px-4 py-3 text-sm ${className}`}>
      <div className="font-semibold">{title}</div>
      <div className="mt-1 line-clamp-2">{message}</div>
    </div>
  );
}

function EmptyState({ title, message, tone = "slate" }: { title: string; message: string; tone?: "slate" | "red" }) {
  const className = tone === "red" ? "border-red-200 bg-red-50 text-red-700" : "border-slate-200 bg-card text-slate-600";

  return (
    <Card className={className}>
      <CardContent className="p-8 text-center">
        <div className="text-sm font-bold">{title}</div>
        <div className="mt-2 text-sm opacity-80">{message}</div>
      </CardContent>
    </Card>
  );
}

function normalizePromptResult(result: unknown): PromptLoadResult {
  if (Array.isArray(result)) {
    return normalizePromptItems(result);
  }

  if (isPromptView(result)) {
    return { prompts: [result], failures: [] };
  }

  if (!isRecord(result)) {
    return {
      prompts: [],
      failures: [{ feature: "unknown", label: "프롬프트", message: "프롬프트 응답 형식을 해석할 수 없습니다." }],
    };
  }

  const promptSource = firstArray(result.prompts, result.views, result.data, result.items, result.results, result.successes);
  const failureSource = firstArray(result.failures, result.errors, result.rejections, result.rejected);
  const prompts = promptSource ? normalizePromptItems(promptSource) : { prompts: [], failures: [] };
  const failures = failureSource ? failureSource.map((item, index) => normalizeFailure(item, index)) : [];

  return {
    prompts: prompts.prompts,
    failures: [...prompts.failures, ...failures],
  };
}

function normalizePromptItems(items: unknown[]): PromptLoadResult {
  return items.reduce<PromptLoadResult>((acc, item, index) => {
    if (isPromptView(item)) {
      acc.prompts.push(item);
      return acc;
    }

    if (isRecord(item) && item.status === "fulfilled") {
      const fulfilled = normalizePromptResult(item.value);
      acc.prompts.push(...fulfilled.prompts);
      acc.failures.push(...fulfilled.failures);
      return acc;
    }

    if (isRecord(item) && item.status === "rejected") {
      acc.failures.push(normalizeFailure(item.reason, index));
      return acc;
    }

    if (isRecord(item) && isPromptView(item.prompt)) {
      acc.prompts.push(item.prompt);
      return acc;
    }

    if (isRecord(item) && isPromptView(item.view)) {
      acc.prompts.push(item.view);
      return acc;
    }

    if (isRecord(item) && isPromptView(item.data)) {
      acc.prompts.push(item.data);
      return acc;
    }

    if (isRecord(item) && ("error" in item || "reason" in item || "message" in item)) {
      acc.failures.push(normalizeFailure(item, index));
    }

    return acc;
  }, { prompts: [], failures: [] });
}

function normalizeFailure(value: unknown, index: number): PromptFailure {
  const feature = extractFeature(value) ?? PROMPT_TABS[index]?.value ?? "unknown";
  const tab = getTabForFeature(feature);

  return {
    feature,
    label: tab?.title ?? extractLabel(value) ?? "프롬프트",
    message: extractMessage(value),
  };
}

function sortPrompts(items: AdminPromptView[]): AdminPromptView[] {
  return [...items].sort((a, b) => {
    const aIndex = PROMPT_TABS.findIndex((tab) => matchesPromptTab(a, tab));
    const bIndex = PROMPT_TABS.findIndex((tab) => matchesPromptTab(b, tab));
    return normalizeSortIndex(aIndex) - normalizeSortIndex(bIndex);
  });
}

function normalizeSortIndex(index: number): number {
  return index === -1 ? Number.MAX_SAFE_INTEGER : index;
}

function matchesPromptTab(prompt: AdminPromptView, tab: PromptTab): boolean {
  return matchesFeatureTab(prompt.feature, `${prompt.name} ${prompt.purpose}`, tab);
}

function matchesFeatureTab(feature: string, label: string, tab: PromptTab): boolean {
  const text = `${feature} ${label}`.toLowerCase();
  return tab.aliases.some((alias) => text.includes(alias.toLowerCase()));
}

function getTabForFeature(feature: string): PromptTab | undefined {
  return PROMPT_TABS.find((tab) => matchesFeatureTab(feature, "", tab));
}

function countPromptMatches(prompt: AdminPromptView, query: string): number {
  if (!query) return 0;
  return countMatches(prompt.schemaSummary, query) + countMatches(prompt.systemPrompt, query);
}

function countMatches(text: string, query: string): number {
  const escaped = escapeRegExp(query);
  return text.match(new RegExp(escaped, "gi"))?.length ?? 0;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function firstArray(...values: unknown[]): unknown[] | null {
  return values.find((value): value is unknown[] => Array.isArray(value)) ?? null;
}

function isPromptView(value: unknown): value is AdminPromptView {
  return (
    isRecord(value) &&
    typeof value.feature === "string" &&
    typeof value.name === "string" &&
    typeof value.version === "string" &&
    typeof value.purpose === "string" &&
    typeof value.systemPrompt === "string" &&
    typeof value.schemaSummary === "string"
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function extractFeature(value: unknown): string | null {
  if (!isRecord(value)) return null;
  const feature = value.feature ?? value.key ?? value.name;
  return typeof feature === "string" ? feature : null;
}

function extractLabel(value: unknown): string | null {
  if (!isRecord(value)) return null;
  const label = value.label ?? value.title;
  return typeof label === "string" ? label : null;
}

function extractMessage(value: unknown): string {
  if (value instanceof Error) return value.message;
  if (typeof value === "string") return value;
  if (!isRecord(value)) return "요청이 실패했습니다.";

  const message = value.message ?? value.error ?? value.reason;
  if (message instanceof Error) return message.message;
  if (typeof message === "string") return message;
  return "요청이 실패했습니다.";
}
