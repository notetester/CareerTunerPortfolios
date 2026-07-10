import { useEffect, useMemo, useState } from "react";
import { FileUser, RefreshCw, Search } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { getAdminProfile, getAdminProfiles } from "../api";
import type { AdminUserProfile } from "../types";

export function AdminProfilesPage() {
  const [rows, setRows] = useState<AdminUserProfile[]>([]);
  const [selected, setSelected] = useState<AdminUserProfile | null>(null);
  const [keyword, setKeyword] = useState("");
  const [hasResume, setHasResume] = useState("");
  const [hasSkills, setHasSkills] = useState("");
  const [updatedFrom, setUpdatedFrom] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const selectedUserId = selected?.userId;
  const skillCount = useMemo(() => asArray(selected?.skills).length, [selected?.skills]);
  const filteredRows = useMemo(() => rows.filter((row) => {
    if (hasResume === "YES" && !row.resumeText) return false;
    if (hasResume === "NO" && row.resumeText) return false;
    if (hasSkills === "YES" && asArray(row.skills).length === 0) return false;
    if (hasSkills === "NO" && asArray(row.skills).length > 0) return false;
    if (updatedFrom && row.updatedAt && row.updatedAt.slice(0, 10) < updatedFrom) return false;
    if (updatedFrom && !row.updatedAt) return false;
    return true;
  }), [rows, hasResume, hasSkills, updatedFrom]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const next = await getAdminProfiles({ keyword, limit: 100 });
      setRows(next);
      if (!selectedUserId && next[0]?.userId) setSelected(next[0]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "관리자 프로필 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const loadDetail = async (userId: number) => {
    setError(null);
    try {
      setSelected(await getAdminProfile(userId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로필 상세를 불러오지 못했습니다.");
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <AdminShell
      active="profiles"
      breadcrumb="프로필 관리"
      title="프로필/동의 관리"
      icon={FileUser}
      desc="사용자 프로필, 이력서 입력 상태, 직무 역량과 경험 데이터를 조회합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="grid gap-5 lg:grid-cols-[360px_1fr]">
        <section className="space-y-4">
          <Card className="border-slate-200 bg-card">
            <CardContent className="space-y-3 p-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="직무, 산업, 역량 검색" className="pl-9" />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <select value={hasResume} onChange={(event) => setHasResume(event.target.value)} className="h-10 rounded-md border border-slate-200 px-3 text-sm">
                  <option value="">이력서 전체</option>
                  <option value="YES">이력서 있음</option>
                  <option value="NO">이력서 없음</option>
                </select>
                <select value={hasSkills} onChange={(event) => setHasSkills(event.target.value)} className="h-10 rounded-md border border-slate-200 px-3 text-sm">
                  <option value="">역량 전체</option>
                  <option value="YES">역량 있음</option>
                  <option value="NO">역량 없음</option>
                </select>
              </div>
              <Input type="date" value={updatedFrom} onChange={(event) => setUpdatedFrom(event.target.value)} title="수정일 시작" />
              <Button className="w-full bg-blue-600 text-white hover:bg-blue-700" onClick={() => void load()}>
                검색
              </Button>
              <Button
                variant="outline"
                className="w-full"
                onClick={() => {
                  setKeyword("");
                  setHasResume("");
                  setHasSkills("");
                  setUpdatedFrom("");
                }}
              >
                필터 초기화
              </Button>
            </CardContent>
          </Card>

          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

          <div className="space-y-2">
            {filteredRows.map((row) => (
              <button
                key={row.userId}
                type="button"
                className={`w-full rounded-lg border bg-card p-3 text-left transition-colors ${
                  selected?.userId === row.userId ? "border-blue-300 ring-2 ring-blue-100" : "border-slate-200 hover:border-blue-200"
                }`}
                onClick={() => row.userId && void loadDetail(row.userId)}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-bold text-slate-950">사용자 #{row.userId}</div>
                    <div className="truncate text-xs text-slate-500">{row.desiredJob || "희망 직무 미입력"}</div>
                  </div>
                  <Badge className="bg-blue-100 text-blue-700">{asArray(row.skills).length} skills</Badge>
                </div>
              </button>
            ))}
            {!loading && filteredRows.length === 0 && <div className="rounded-lg bg-card p-6 text-center text-sm text-slate-500">조건에 맞는 프로필 데이터가 없습니다.</div>}
          </div>
        </section>

        <section className="min-w-0">
          <Card className="border-slate-200 bg-card">
            <CardHeader>
              <CardTitle className="text-lg text-slate-950">
                {selected ? `사용자 #${selected.userId} 프로필` : "프로필 상세"}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              {!selected ? (
                <div className="rounded-lg bg-slate-50 p-8 text-center text-sm text-slate-500">왼쪽에서 프로필을 선택하세요.</div>
              ) : (
                <>
                  <div className="grid gap-3 md:grid-cols-4">
                    <Info label="희망 직무" value={selected.desiredJob || "-"} />
                    <Info label="희망 산업" value={selected.desiredIndustry || "-"} />
                    <Info label="기술 수" value={String(skillCount)} />
                    <Info label="수정일" value={formatDate(selected.updatedAt)} />
                  </div>
                  <SkillBlock value={selected.skills} />
                  <ProfileListBlock title="학력" value={selected.education} type="education" />
                  <ProfileListBlock title="경력" value={selected.career} type="career" />
                  <ProfileListBlock title="경험/프로젝트/활동" value={selected.projects} type="project" />
                  <ProfileListBlock title="선호 조건" value={selected.preferences} type="preference" />
                  <TextBlock title="이력서 원문" value={selected.resumeText} />
                  <TextBlock title="자기소개" value={selected.selfIntro} />
                </>
              )}
            </CardContent>
          </Card>
        </section>
      </div>
    </AdminShell>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2">
      <div className="text-[11px] font-semibold uppercase text-slate-400">{label}</div>
      <div className="mt-1 break-words text-sm font-semibold text-slate-800">{value}</div>
    </div>
  );
}

function SkillBlock({ value }: { value: unknown }) {
  const skills = asArray(value)
    .map((item) => getDisplayText(item, ["name", "skill", "skillName", "label", "title", "value"]))
    .filter(Boolean);

  return (
    <div>
      <SectionHeader title="직무 역량/스킬" count={skills.length} />
      {skills.length > 0 ? (
        <div className="flex flex-wrap gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
          {skills.map((skill, index) => (
            <span key={`${skill}-${index}`} className="rounded-full bg-blue-100 px-3 py-1 text-xs font-semibold text-blue-700">
              {skill}
            </span>
          ))}
        </div>
      ) : (
        <EmptyBlock message="입력된 역량 정보가 없습니다." />
      )}
      <RawJsonDetails value={value} />
    </div>
  );
}

function ProfileListBlock({ title, value, type }: { title: string; value: unknown; type: "education" | "career" | "project" | "preference" }) {
  const items = asArray(value);

  return (
    <div>
      <SectionHeader title={title} count={items.length} />
      {items.length > 0 ? (
        <div className="grid gap-2">
          {items.map((item, index) => (
            <StructuredItem key={index} item={item} type={type} />
          ))}
        </div>
      ) : (
        <EmptyBlock message={`${title} 정보가 없습니다.`} />
      )}
      <RawJsonDetails value={value} />
    </div>
  );
}

function SectionHeader({ title, count }: { title: string; count: number }) {
  return (
    <div className="mb-2 flex items-center justify-between gap-2">
      <div className="text-xs font-bold text-slate-500">{title}</div>
      <Badge className="bg-slate-100 text-slate-600">{count}개</Badge>
    </div>
  );
}

function StructuredItem({ item, type }: { item: unknown; type: "education" | "career" | "project" | "preference" }) {
  const record = asRecord(item);
  if (!record) {
    return <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">{String(item)}</div>;
  }

  if (type === "education") {
    const school = readField(record, ["school", "schoolName", "university", "name"], "학교명 미입력");
    const major = readField(record, ["major", "department", "degree"], "전공 미입력");
    const status = readField(record, ["status", "graduationStatus"], "");
    return <ProfileCard title={school} meta={compact([major, status]).join(" · ")} period={periodText(record)} />;
  }

  if (type === "career") {
    const company = readField(record, ["company", "companyName", "organization", "name"], "회사명 미입력");
    const role = readField(record, ["role", "position", "jobTitle"], "직무 미입력");
    const detail = readField(record, ["tasks", "description", "achievement", "achievements"], "");
    return <ProfileCard title={company} meta={role} period={periodText(record)} detail={detail} />;
  }

  if (type === "project") {
    const title = readField(record, ["title", "projectName", "activityName", "name"], "활동명 미입력");
    const role = readField(record, ["role", "type", "position"], "");
    const detail = readField(record, ["description", "result", "outcome", "summary"], "");
    return <ProfileCard title={title} meta={role} period={periodText(record)} detail={detail} />;
  }

  return (
    <div className="grid gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 sm:grid-cols-2">
      {Object.entries(record).map(([key, entryValue]) => (
        <div key={key} className="min-w-0">
          <div className="text-[11px] font-semibold uppercase text-slate-400">{key}</div>
          <div className="mt-1 break-words text-sm text-slate-700">{formatScalar(entryValue)}</div>
        </div>
      ))}
    </div>
  );
}

function ProfileCard({ title, meta, period, detail }: { title: string; meta?: string; period?: string; detail?: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="break-words text-sm font-bold text-slate-900">{title}</div>
          {meta && <div className="mt-1 break-words text-xs text-slate-500">{meta}</div>}
        </div>
        {period && <Badge className="bg-card text-slate-600">{period}</Badge>}
      </div>
      {detail && <div className="mt-2 whitespace-pre-wrap text-sm leading-6 text-slate-700">{detail}</div>}
    </div>
  );
}

function RawJsonDetails({ value }: { value: unknown }) {
  return (
    <details className="mt-2 rounded-lg border border-dashed border-slate-200 bg-card px-3 py-2">
      <summary className="cursor-pointer text-xs font-semibold text-slate-500">원본 JSON 보기</summary>
      <pre className="mt-2 max-h-56 overflow-auto whitespace-pre-wrap rounded-md bg-slate-950 p-3 text-xs leading-5 text-slate-100">
        {pretty(value)}
      </pre>
    </details>
  );
}

function EmptyBlock({ message }: { message: string }) {
  return <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-500">{message}</div>;
}

function TextBlock({ title, value }: { title: string; value?: string | null }) {
  return (
    <div>
      <div className="mb-2 text-xs font-bold text-slate-500">{title}</div>
      <div className="whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm leading-6 text-slate-700">{value || "-"}</div>
    </div>
  );
}

function pretty(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "string") {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}

function asArray(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (typeof value === "string") {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return value ? [value] : [];
    }
  }
  return [];
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  return value as Record<string, unknown>;
}

function getDisplayText(value: unknown, keys: string[]): string {
  if (typeof value === "string") return value;
  const record = asRecord(value);
  if (!record) return "";
  return readField(record, keys, firstScalarValue(record));
}

function readField(record: Record<string, unknown>, keys: string[], fallback = ""): string {
  for (const key of keys) {
    const value = record[key];
    if (value !== null && value !== undefined && value !== "") return formatScalar(value);
  }
  return fallback;
}

function firstScalarValue(record: Record<string, unknown>): string {
  const value = Object.values(record).find((entry) => typeof entry === "string" || typeof entry === "number" || typeof entry === "boolean");
  return value === undefined ? "" : formatScalar(value);
}

function formatScalar(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return JSON.stringify(value);
}

function periodText(record: Record<string, unknown>): string {
  const start = readField(record, ["startDate", "startedAt", "from"], "");
  const end = readField(record, ["endDate", "endedAt", "to"], "");
  if (!start && !end) return "";
  return `${start || "시작일 미입력"} ~ ${end || "현재"}`;
}

function compact(values: string[]): string[] {
  return values.filter((value) => value && value !== "-");
}

function formatDate(value?: string | null): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
