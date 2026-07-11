import { useCallback, useEffect, useState } from "react";
import { GraduationCap, Plus, RefreshCw, Save, Trash2 } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import { getResumeDetail, saveResumeDetail } from "../api/nicknameProfileApi";
import type {
  ResumeActivity,
  ResumeAward,
  ResumeCareer,
  ResumeCertificate,
  ResumeDesiredCondition,
  ResumeDetail,
  ResumeDetailPayload,
  ResumeEducation,
  ResumeLanguage,
  ResumePortfolio,
} from "../types/nicknameProfile";

const emptyEducation = (): ResumeEducation => ({ school: "", major: "", gpa: "", gpaScale: "4.5", graduationStatus: "", startDate: "", endDate: "" });
const emptyCareer = (): ResumeCareer => ({ company: "", role: "", employmentType: "", startDate: "", endDate: "", description: "" });
const emptyCertificate = (): ResumeCertificate => ({ name: "", issuer: "", acquiredAt: "" });
const emptyLanguage = (): ResumeLanguage => ({ test: "", score: "", acquiredAt: "" });
const emptyAward = (): ResumeAward => ({ title: "", host: "", awardedAt: "", description: "" });
const emptyActivity = (): ResumeActivity => ({ title: "", organization: "", role: "", startDate: "", endDate: "", description: "" });
const emptyPortfolio = (): ResumePortfolio => ({ label: "", url: "" });
const emptyDesired = (): ResumeDesiredCondition => ({
  jobCategoryLarge: "", jobCategoryMedium: "", employmentType: "", region: "", salaryMin: "", salaryMax: "", remote: false,
});

interface FormState {
  education: ResumeEducation[];
  career: ResumeCareer[];
  certificates: ResumeCertificate[];
  languages: ResumeLanguage[];
  awards: ResumeAward[];
  activities: ResumeActivity[];
  skillsText: string;
  portfolios: ResumePortfolio[];
  desiredCondition: ResumeDesiredCondition;
}

const emptyForm: FormState = {
  education: [],
  career: [],
  certificates: [],
  languages: [],
  awards: [],
  activities: [],
  skillsText: "",
  portfolios: [],
  desiredCondition: emptyDesired(),
};

/**
 * 이력서 상세 스펙 편집 폼 — 사람인/잡코리아식 섹션별 add/remove 리스트.
 *
 * user_profile 의 기본 프로필과 별개로, 분석 정확도용 상세 스펙(학력/경력/자격증/어학/수상/
 * 대외활동/기술스택/포트폴리오/희망 근무조건)을 관리한다.
 */
export function ResumeDetailForm() {
  const [form, setForm] = useState<FormState>(emptyForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setForm(toForm(await getResumeDetail()));
    } catch (e) {
      setError(e instanceof Error ? e.message : "이력서 상세를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const save = async () => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await saveResumeDetail(toPayload(form));
      setMessage("이력서 상세 스펙을 저장했습니다.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <Card className="border-slate-200">
        <CardContent className="py-10 text-center text-sm text-slate-500">불러오는 중...</CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-bold text-slate-900">
          <GraduationCap className="size-5 text-blue-600" />
          이력서 상세 스펙
        </h2>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading}>
            <RefreshCw className="size-4" />
          </Button>
          <Button size="sm" className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => void save()} disabled={saving}>
            <Save className="size-4" />
            {saving ? "저장 중..." : "저장"}
          </Button>
        </div>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {message && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

      <ListSection
        title="학력"
        items={form.education}
        onAdd={() => setForm((p) => ({ ...p, education: [...p.education, emptyEducation()] }))}
        onRemove={(i) => setForm((p) => ({ ...p, education: removeAt(p.education, i) }))}
        render={(item, i) => (
          <div className="grid gap-3 md:grid-cols-2">
            <FieldInput label="학교" value={item.school} onChange={(v) => setForm((p) => ({ ...p, education: patch(p.education, i, "school", v) }))} />
            <FieldInput label="전공" value={item.major} onChange={(v) => setForm((p) => ({ ...p, education: patch(p.education, i, "major", v) }))} />
            <FieldInput label="학점" value={item.gpa} onChange={(v) => setForm((p) => ({ ...p, education: patch(p.education, i, "gpa", v) }))} placeholder="예: 3.8" />
            <FieldInput label="기준 학점" value={item.gpaScale} onChange={(v) => setForm((p) => ({ ...p, education: patch(p.education, i, "gpaScale", v) }))} placeholder="예: 4.5" />
            <FieldSelect
              label="졸업 여부"
              value={item.graduationStatus}
              options={["재학", "졸업예정", "졸업", "수료", "중퇴"]}
              onChange={(v) => setForm((p) => ({ ...p, education: patch(p.education, i, "graduationStatus", v) }))}
            />
            <div className="grid grid-cols-2 gap-2">
              <FieldInput label="입학" type="month" value={item.startDate} onChange={(v) => setForm((p) => ({ ...p, education: patch(p.education, i, "startDate", v) }))} />
              <FieldInput label="졸업" type="month" value={item.endDate} onChange={(v) => setForm((p) => ({ ...p, education: patch(p.education, i, "endDate", v) }))} />
            </div>
          </div>
        )}
      />

      <ListSection
        title="경력"
        items={form.career}
        onAdd={() => setForm((p) => ({ ...p, career: [...p.career, emptyCareer()] }))}
        onRemove={(i) => setForm((p) => ({ ...p, career: removeAt(p.career, i) }))}
        render={(item, i) => (
          <div className="grid gap-3 md:grid-cols-2">
            <FieldInput label="회사" value={item.company} onChange={(v) => setForm((p) => ({ ...p, career: patch(p.career, i, "company", v) }))} />
            <FieldInput label="직무" value={item.role} onChange={(v) => setForm((p) => ({ ...p, career: patch(p.career, i, "role", v) }))} />
            <FieldSelect
              label="고용형태"
              value={item.employmentType}
              options={["정규직", "계약직", "인턴", "프리랜서", "파견"]}
              onChange={(v) => setForm((p) => ({ ...p, career: patch(p.career, i, "employmentType", v) }))}
            />
            <div className="grid grid-cols-2 gap-2">
              <FieldInput label="입사" type="month" value={item.startDate} onChange={(v) => setForm((p) => ({ ...p, career: patch(p.career, i, "startDate", v) }))} />
              <FieldInput label="퇴사" type="month" value={item.endDate} onChange={(v) => setForm((p) => ({ ...p, career: patch(p.career, i, "endDate", v) }))} />
            </div>
            <FieldTextarea label="담당 업무/성과" className="md:col-span-2" value={item.description} onChange={(v) => setForm((p) => ({ ...p, career: patch(p.career, i, "description", v) }))} />
          </div>
        )}
      />

      <ListSection
        title="자격증"
        items={form.certificates}
        onAdd={() => setForm((p) => ({ ...p, certificates: [...p.certificates, emptyCertificate()] }))}
        onRemove={(i) => setForm((p) => ({ ...p, certificates: removeAt(p.certificates, i) }))}
        render={(item, i) => (
          <div className="grid gap-3 md:grid-cols-3">
            <FieldInput label="자격증명" value={item.name} onChange={(v) => setForm((p) => ({ ...p, certificates: patch(p.certificates, i, "name", v) }))} />
            <FieldInput label="발급기관" value={item.issuer} onChange={(v) => setForm((p) => ({ ...p, certificates: patch(p.certificates, i, "issuer", v) }))} />
            <FieldInput label="취득일" type="month" value={item.acquiredAt} onChange={(v) => setForm((p) => ({ ...p, certificates: patch(p.certificates, i, "acquiredAt", v) }))} />
          </div>
        )}
      />

      <ListSection
        title="어학"
        items={form.languages}
        onAdd={() => setForm((p) => ({ ...p, languages: [...p.languages, emptyLanguage()] }))}
        onRemove={(i) => setForm((p) => ({ ...p, languages: removeAt(p.languages, i) }))}
        render={(item, i) => (
          <div className="grid gap-3 md:grid-cols-3">
            <FieldInput label="시험" value={item.test} onChange={(v) => setForm((p) => ({ ...p, languages: patch(p.languages, i, "test", v) }))} placeholder="예: TOEIC" />
            <FieldInput label="점수/급수" value={item.score} onChange={(v) => setForm((p) => ({ ...p, languages: patch(p.languages, i, "score", v) }))} placeholder="예: 900" />
            <FieldInput label="취득일" type="month" value={item.acquiredAt} onChange={(v) => setForm((p) => ({ ...p, languages: patch(p.languages, i, "acquiredAt", v) }))} />
          </div>
        )}
      />

      <ListSection
        title="수상"
        items={form.awards}
        onAdd={() => setForm((p) => ({ ...p, awards: [...p.awards, emptyAward()] }))}
        onRemove={(i) => setForm((p) => ({ ...p, awards: removeAt(p.awards, i) }))}
        render={(item, i) => (
          <div className="grid gap-3 md:grid-cols-2">
            <FieldInput label="수상명" value={item.title} onChange={(v) => setForm((p) => ({ ...p, awards: patch(p.awards, i, "title", v) }))} />
            <FieldInput label="주최" value={item.host} onChange={(v) => setForm((p) => ({ ...p, awards: patch(p.awards, i, "host", v) }))} />
            <FieldInput label="수상일" type="month" value={item.awardedAt} onChange={(v) => setForm((p) => ({ ...p, awards: patch(p.awards, i, "awardedAt", v) }))} />
            <FieldTextarea label="내용" className="md:col-span-2" value={item.description} onChange={(v) => setForm((p) => ({ ...p, awards: patch(p.awards, i, "description", v) }))} />
          </div>
        )}
      />

      <ListSection
        title="대외활동"
        items={form.activities}
        onAdd={() => setForm((p) => ({ ...p, activities: [...p.activities, emptyActivity()] }))}
        onRemove={(i) => setForm((p) => ({ ...p, activities: removeAt(p.activities, i) }))}
        render={(item, i) => (
          <div className="grid gap-3 md:grid-cols-2">
            <FieldInput label="활동명" value={item.title} onChange={(v) => setForm((p) => ({ ...p, activities: patch(p.activities, i, "title", v) }))} />
            <FieldInput label="기관/단체" value={item.organization} onChange={(v) => setForm((p) => ({ ...p, activities: patch(p.activities, i, "organization", v) }))} />
            <FieldInput label="역할" value={item.role} onChange={(v) => setForm((p) => ({ ...p, activities: patch(p.activities, i, "role", v) }))} />
            <div className="grid grid-cols-2 gap-2">
              <FieldInput label="시작" type="month" value={item.startDate} onChange={(v) => setForm((p) => ({ ...p, activities: patch(p.activities, i, "startDate", v) }))} />
              <FieldInput label="종료" type="month" value={item.endDate} onChange={(v) => setForm((p) => ({ ...p, activities: patch(p.activities, i, "endDate", v) }))} />
            </div>
            <FieldTextarea label="내용" className="md:col-span-2" value={item.description} onChange={(v) => setForm((p) => ({ ...p, activities: patch(p.activities, i, "description", v) }))} />
          </div>
        )}
      />

      <Card className="border-slate-200">
        <CardHeader>
          <CardTitle className="text-base">기술 스택</CardTitle>
        </CardHeader>
        <CardContent>
          <Textarea
            value={form.skillsText}
            rows={4}
            onChange={(e) => setForm((p) => ({ ...p, skillsText: e.target.value }))}
            placeholder="한 줄에 하나씩 입력 (예: React / Spring / SQL)"
          />
        </CardContent>
      </Card>

      <ListSection
        title="포트폴리오 링크"
        items={form.portfolios}
        onAdd={() => setForm((p) => ({ ...p, portfolios: [...p.portfolios, emptyPortfolio()] }))}
        onRemove={(i) => setForm((p) => ({ ...p, portfolios: removeAt(p.portfolios, i) }))}
        render={(item, i) => (
          <div className="grid gap-3 md:grid-cols-2">
            <FieldInput label="이름" value={item.label} onChange={(v) => setForm((p) => ({ ...p, portfolios: patch(p.portfolios, i, "label", v) }))} placeholder="예: GitHub, 블로그" />
            <FieldInput label="URL" value={item.url} onChange={(v) => setForm((p) => ({ ...p, portfolios: patch(p.portfolios, i, "url", v) }))} placeholder="https://" />
          </div>
        )}
      />

      <Card className="border-slate-200">
        <CardHeader>
          <CardTitle className="text-base">희망 근무조건</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-2">
          <FieldInput label="직무 대분류" value={form.desiredCondition.jobCategoryLarge} onChange={(v) => setForm((p) => ({ ...p, desiredCondition: { ...p.desiredCondition, jobCategoryLarge: v } }))} placeholder="예: 개발" />
          <FieldInput label="직무 중분류" value={form.desiredCondition.jobCategoryMedium} onChange={(v) => setForm((p) => ({ ...p, desiredCondition: { ...p.desiredCondition, jobCategoryMedium: v } }))} placeholder="예: 백엔드" />
          <FieldSelect
            label="고용형태"
            value={form.desiredCondition.employmentType}
            options={["정규직", "계약직", "인턴", "프리랜서"]}
            onChange={(v) => setForm((p) => ({ ...p, desiredCondition: { ...p.desiredCondition, employmentType: v } }))}
          />
          <FieldInput label="희망 지역" value={form.desiredCondition.region} onChange={(v) => setForm((p) => ({ ...p, desiredCondition: { ...p.desiredCondition, region: v } }))} placeholder="예: 서울" />
          <FieldInput label="희망 연봉 하한(만원)" value={form.desiredCondition.salaryMin} onChange={(v) => setForm((p) => ({ ...p, desiredCondition: { ...p.desiredCondition, salaryMin: v } }))} placeholder="예: 3200" />
          <FieldInput label="희망 연봉 상한(만원)" value={form.desiredCondition.salaryMax} onChange={(v) => setForm((p) => ({ ...p, desiredCondition: { ...p.desiredCondition, salaryMax: v } }))} placeholder="예: 4500" />
          <label className="flex items-center gap-2 text-sm text-slate-700 md:col-span-2">
            <input
              type="checkbox"
              className="size-4 rounded border-slate-300"
              checked={form.desiredCondition.remote}
              onChange={(e) => setForm((p) => ({ ...p, desiredCondition: { ...p.desiredCondition, remote: e.target.checked } }))}
            />
            재택/원격 근무 희망
          </label>
        </CardContent>
      </Card>
    </div>
  );
}

// ── 재사용 리스트 섹션 ──

function ListSection<T>({
  title,
  items,
  onAdd,
  onRemove,
  render,
}: {
  title: string;
  items: T[];
  onAdd(): void;
  onRemove(index: number): void;
  render(item: T, index: number): React.ReactNode;
}) {
  return (
    <Card className="border-slate-200">
      <CardHeader className="flex flex-row items-center justify-between gap-3">
        <CardTitle className="text-base">{title}</CardTitle>
        <Button variant="outline" size="sm" onClick={onAdd}>
          <Plus className="size-4" />
          추가
        </Button>
      </CardHeader>
      <CardContent className="space-y-3">
        {items.length === 0 ? (
          <p className="py-3 text-center text-sm text-slate-400">항목이 없습니다. 추가를 눌러 입력하세요.</p>
        ) : (
          items.map((item, index) => (
            <div key={index} className="rounded-lg border border-slate-200 bg-slate-50 p-4">
              <div className="mb-3 flex items-center justify-between">
                <span className="text-sm font-bold text-slate-700">{title} {index + 1}</span>
                <Button variant="ghost" size="sm" onClick={() => onRemove(index)}>
                  <Trash2 className="size-4 text-red-500" />
                </Button>
              </div>
              {render(item, index)}
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
}

function FieldInput({
  label,
  value,
  onChange,
  placeholder,
  type,
  className,
}: {
  label: string;
  value: string;
  onChange(v: string): void;
  placeholder?: string;
  type?: string;
  className?: string;
}) {
  return (
    <label className={`block space-y-1 ${className ?? ""}`}>
      <span className="text-xs font-semibold text-slate-600">{label}</span>
      <Input type={type} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
    </label>
  );
}

function FieldTextarea({
  label,
  value,
  onChange,
  className,
}: {
  label: string;
  value: string;
  onChange(v: string): void;
  className?: string;
}) {
  return (
    <label className={`block space-y-1 ${className ?? ""}`}>
      <span className="text-xs font-semibold text-slate-600">{label}</span>
      <Textarea value={value} rows={3} onChange={(e) => onChange(e.target.value)} />
    </label>
  );
}

function FieldSelect({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: string[];
  onChange(v: string): void;
}) {
  return (
    <label className="block space-y-1">
      <span className="text-xs font-semibold text-slate-600">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="h-9 w-full rounded-md border border-slate-200 bg-card px-3 text-sm"
      >
        <option value="">선택</option>
        {options.map((opt) => (
          <option key={opt} value={opt}>{opt}</option>
        ))}
      </select>
    </label>
  );
}

// ── 상태 헬퍼 ──

function patch<T, K extends keyof T>(items: T[], index: number, key: K, value: T[K]): T[] {
  return items.map((item, i) => (i === index ? { ...item, [key]: value } : item));
}

function removeAt<T>(items: T[], index: number): T[] {
  return items.filter((_, i) => i !== index);
}

function toForm(detail: ResumeDetail): FormState {
  return {
    education: detail.education ?? [],
    career: detail.career ?? [],
    certificates: detail.certificates ?? [],
    languages: detail.languages ?? [],
    awards: detail.awards ?? [],
    activities: detail.activities ?? [],
    skillsText: (detail.skills ?? []).join("\n"),
    portfolios: detail.portfolios ?? [],
    desiredCondition: detail.desiredCondition ?? emptyDesired(),
  };
}

function toPayload(form: FormState): ResumeDetailPayload {
  return {
    education: form.education,
    career: form.career,
    certificates: form.certificates,
    languages: form.languages,
    awards: form.awards,
    activities: form.activities,
    skills: form.skillsText.split("\n").map((s) => s.trim()).filter(Boolean),
    portfolios: form.portfolios,
    desiredCondition: form.desiredCondition,
  };
}
