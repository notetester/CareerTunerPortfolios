import { useState } from "react";
import { Button } from "@/app/components/ui/button";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import {
  CAREER_LEVEL_LABELS,
  EDUCATION_LEVEL_LABELS,
  EMPLOYMENT_TYPE_LABELS,
  type JobPostingFields,
  type JobPostingUpsertPayload,
} from "../types/company";

/** 빈 폼 초기값. */
const EMPTY_FIELDS: JobPostingFields = {
  title: "",
  jobRole: "",
  employmentType: "FULL_TIME",
  careerLevel: "ANY",
  careerYearsMin: null,
  careerYearsMax: null,
  educationLevel: "ANY",
  salaryText: null,
  salaryNegotiable: false,
  workLocation: null,
  workHours: null,
  deadlineDate: null,
  alwaysOpen: false,
  mainTasks: null,
  requirements: null,
  preferred: null,
  benefits: null,
  hiringProcess: null,
  headcount: null,
  tags: [],
};

interface Props {
  initial?: Partial<JobPostingFields>;
  saving: boolean;
  /** submit=false 임시저장, true 제출(정책에 따라 검토 대기 또는 즉시 게시). */
  onSave: (payload: JobPostingUpsertPayload, submit: boolean) => void;
  onCancel: () => void;
}

const selectClass = "h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm";

/** 채용공고 작성/수정 폼 — 사람인식 상세 필드 전부. */
export function JobPostingForm({ initial, saving, onSave, onCancel }: Props) {
  const [fields, setFields] = useState<JobPostingFields>({ ...EMPTY_FIELDS, ...initial });
  const [tagsText, setTagsText] = useState((initial?.tags ?? []).join(", "));
  const [error, setError] = useState<string | null>(null);

  const set = <K extends keyof JobPostingFields>(key: K, value: JobPostingFields[K]) =>
    setFields((prev) => ({ ...prev, [key]: value }));

  const buildPayload = (submit: boolean): JobPostingUpsertPayload | null => {
    if (!fields.title.trim()) {
      setError("공고 제목을 입력해 주세요.");
      return null;
    }
    if (!fields.jobRole.trim()) {
      setError("직무명을 입력해 주세요.");
      return null;
    }
    setError(null);
    return {
      ...fields,
      title: fields.title.trim(),
      jobRole: fields.jobRole.trim(),
      tags: tagsText.split(",").map((tag) => tag.trim()).filter(Boolean),
      submit,
    };
  };

  const save = (submit: boolean) => {
    const payload = buildPayload(submit);
    if (payload) onSave(payload, submit);
  };

  return (
    <div className="space-y-4">
      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <div className="grid gap-3 md:grid-cols-2">
        <label className="space-y-1 md:col-span-2">
          <span className="text-sm font-medium text-slate-700">공고 제목 *</span>
          <Input value={fields.title} onChange={(e) => set("title", e.target.value)} placeholder="예) 백엔드 개발자 채용 (Java/Spring)" />
        </label>
        <label className="space-y-1">
          <span className="text-sm font-medium text-slate-700">직무명 *</span>
          <Input value={fields.jobRole} onChange={(e) => set("jobRole", e.target.value)} placeholder="예) 백엔드 개발자" />
        </label>
        <label className="space-y-1">
          <span className="text-sm font-medium text-slate-700">고용형태</span>
          <select className={selectClass} value={fields.employmentType} onChange={(e) => set("employmentType", e.target.value)}>
            {Object.entries(EMPLOYMENT_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
        <label className="space-y-1">
          <span className="text-sm font-medium text-slate-700">경력조건</span>
          <select className={selectClass} value={fields.careerLevel} onChange={(e) => set("careerLevel", e.target.value)}>
            {Object.entries(CAREER_LEVEL_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
        {fields.careerLevel === "EXPERIENCED" && (
          <div className="grid grid-cols-2 gap-2">
            <label className="space-y-1">
              <span className="text-sm font-medium text-slate-700">경력 최소(년)</span>
              <Input type="number" min={0} value={fields.careerYearsMin ?? ""} onChange={(e) => set("careerYearsMin", e.target.value === "" ? null : Number(e.target.value))} />
            </label>
            <label className="space-y-1">
              <span className="text-sm font-medium text-slate-700">경력 최대(년)</span>
              <Input type="number" min={0} value={fields.careerYearsMax ?? ""} onChange={(e) => set("careerYearsMax", e.target.value === "" ? null : Number(e.target.value))} />
            </label>
          </div>
        )}
        <label className="space-y-1">
          <span className="text-sm font-medium text-slate-700">학력</span>
          <select className={selectClass} value={fields.educationLevel} onChange={(e) => set("educationLevel", e.target.value)}>
            {Object.entries(EDUCATION_LEVEL_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
        <label className="space-y-1">
          <span className="text-sm font-medium text-slate-700">급여</span>
          <Input value={fields.salaryText ?? ""} onChange={(e) => set("salaryText", e.target.value || null)} placeholder="예) 4,000~5,500만원" />
        </label>
        <label className="flex items-center gap-2 pt-6 text-sm text-slate-700">
          <input type="checkbox" checked={fields.salaryNegotiable} onChange={(e) => set("salaryNegotiable", e.target.checked)} />
          급여 협의 가능
        </label>
        <label className="space-y-1">
          <span className="text-sm font-medium text-slate-700">근무지역</span>
          <Input value={fields.workLocation ?? ""} onChange={(e) => set("workLocation", e.target.value || null)} placeholder="예) 서울 강남구" />
        </label>
        <label className="space-y-1">
          <span className="text-sm font-medium text-slate-700">근무시간</span>
          <Input value={fields.workHours ?? ""} onChange={(e) => set("workHours", e.target.value || null)} placeholder="예) 주 5일 10:00~19:00" />
        </label>
        <label className="space-y-1">
          <span className="text-sm font-medium text-slate-700">마감일</span>
          <Input type="date" value={fields.deadlineDate ?? ""} onChange={(e) => set("deadlineDate", e.target.value || null)} disabled={fields.alwaysOpen} />
        </label>
        <label className="flex items-center gap-2 pt-6 text-sm text-slate-700">
          <input type="checkbox" checked={fields.alwaysOpen} onChange={(e) => set("alwaysOpen", e.target.checked)} />
          상시 채용
        </label>
        <label className="space-y-1">
          <span className="text-sm font-medium text-slate-700">채용인원</span>
          <Input value={fields.headcount ?? ""} onChange={(e) => set("headcount", e.target.value || null)} placeholder="예) 2명" />
        </label>
        <label className="space-y-1">
          <span className="text-sm font-medium text-slate-700">태그(쉼표 구분)</span>
          <Input value={tagsText} onChange={(e) => setTagsText(e.target.value)} placeholder="예) Java, Spring, MySQL" />
        </label>
      </div>

      {([
        ["mainTasks", "주요업무"],
        ["requirements", "자격요건"],
        ["preferred", "우대사항"],
        ["benefits", "복리후생"],
        ["hiringProcess", "전형절차"],
      ] as Array<[keyof JobPostingFields, string]>).map(([key, label]) => (
        <label key={key} className="block space-y-1">
          <span className="text-sm font-medium text-slate-700">{label}</span>
          <Textarea
            className="min-h-24"
            value={(fields[key] as string | null) ?? ""}
            onChange={(e) => set(key, (e.target.value || null) as never)}
          />
        </label>
      ))}

      <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4">
        <Button variant="outline" onClick={onCancel} disabled={saving}>취소</Button>
        <Button variant="outline" onClick={() => save(false)} disabled={saving}>임시저장</Button>
        <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => save(true)} disabled={saving}>
          제출하기
        </Button>
      </div>
      <p className="text-xs text-slate-500">
        제출 시 기업 신뢰등급 정책에 따라 즉시 게시되거나 관리자 검토 후 게시됩니다.
      </p>
    </div>
  );
}
