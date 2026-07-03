import { useEffect, useMemo, useState } from "react";
import { Building2, CheckCircle2, ClipboardList, Plus, Save } from "lucide-react";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Textarea } from "../components/ui/textarea";
import { toast } from "@/features/notification/components/toast";
import {
  applyEnterprise,
  createEnterpriseJob,
  getEnterpriseStatus,
  listEnterpriseJobs,
  type EnterpriseJob,
  type EnterpriseJobRequest,
  type EnterpriseStatus,
} from "@/features/enterprise/api/enterpriseApi";

const emptyJob: EnterpriseJobRequest = {
  companyName: "",
  title: "",
  positionTitle: "",
  jobCategory: "",
  specialties: [],
  duties: "",
  qualifications: "",
  preferred: "",
  benefits: "",
  employmentType: "",
  experienceLevel: "",
  educationLevel: "",
  salaryType: "TEXT",
  salaryMin: null,
  salaryMax: null,
  salaryText: "",
  workLocation: "",
  workSchedule: "",
  headcount: "",
  applicationStartAt: null,
  applicationEndAt: null,
  applyUrl: "",
  contactEmail: "",
  contactPhone: "",
  visibility: "PUBLIC",
};

export function EnterprisePage() {
  const [status, setStatus] = useState<EnterpriseStatus | null>(null);
  const [jobs, setJobs] = useState<EnterpriseJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [appForm, setAppForm] = useState({ companyName: "", businessNumber: "", representativeName: "", contactName: "", contactEmail: "", contactPhone: "", websiteUrl: "", industry: "", employeeCount: "" });
  const [jobForm, setJobForm] = useState<EnterpriseJobRequest>(emptyJob);

  const reviewCopy = useMemo(() => {
    if (!status?.policy) return "운영 정책 확인 중";
    return `${status.policy.trusted ? "신뢰 기업" : "일반 기업"} · ${status.policy.createRequiresReview ? "등록 검토" : "등록 즉시 공개"} · ${status.policy.editRequiresReview ? "수정 검토" : "수정 즉시 반영"}`;
  }, [status]);

  const load = async () => {
    setLoading(true);
    try {
      const next = await getEnterpriseStatus();
      setStatus(next);
      setJobs(next.employer ? await listEnterpriseJobs() : []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const submitApplication = async () => {
    await applyEnterprise({ ...appForm, createRequiresReview: true, editRequiresReview: true });
    toast.success("기업 계정 전환 신청을 접수했습니다.");
    await load();
  };

  const submitJob = async () => {
    await createEnterpriseJob({
      ...jobForm,
      specialties: jobForm.specialties.length ? jobForm.specialties : (jobForm.jobCategory ? [jobForm.jobCategory] : []),
      applicationStartAt: jobForm.applicationStartAt || null,
      applicationEndAt: jobForm.applicationEndAt || null,
    });
    toast.success(status?.policy.createRequiresReview ? "공고를 검토 대기 상태로 등록했습니다." : "공고를 공개했습니다.");
    setJobForm({ ...emptyJob, companyName: jobForm.companyName });
    await load();
  };

  return (
    <div className="mx-auto w-full max-w-[1200px] space-y-6 px-4 py-8 sm:px-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-950"><Building2 className="size-6 text-blue-600" />기업 계정·공고 관리</h1>
          <p className="mt-1 text-sm text-slate-500">승인된 기업은 커리어튜너 정책에 따라 채용공고를 등록하고 수정할 수 있습니다.</p>
        </div>
        <Badge variant="outline">{loading ? "불러오는 중" : reviewCopy}</Badge>
      </div>

      {!status?.employer && (
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2"><ClipboardList className="size-5" />기업 계정 전환 신청</CardTitle></CardHeader>
          <CardContent className="grid gap-4 md:grid-cols-2">
            {[
              ["companyName", "회사명"], ["businessNumber", "사업자등록번호"], ["representativeName", "대표자명"], ["contactName", "담당자명"],
              ["contactEmail", "담당자 이메일"], ["contactPhone", "담당자 전화번호"], ["websiteUrl", "웹사이트"], ["industry", "업종"], ["employeeCount", "임직원 규모"],
            ].map(([key, label]) => (
              <div key={key} className="space-y-2">
                <Label>{label}</Label>
                <Input value={(appForm as Record<string, string>)[key]} onChange={(e) => setAppForm((p) => ({ ...p, [key]: e.target.value }))} />
              </div>
            ))}
            <div className="md:col-span-2 flex justify-end">
              <Button onClick={submitApplication} disabled={!appForm.companyName.trim()}><Save className="size-4" />신청 제출</Button>
            </div>
          </CardContent>
        </Card>
      )}

      {status?.employer && (
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2"><Plus className="size-5" />채용공고 등록</CardTitle></CardHeader>
          <CardContent className="grid gap-4 md:grid-cols-2">
            <Field label="회사명" value={jobForm.companyName} onChange={(v) => setJobForm((p) => ({ ...p, companyName: v }))} />
            <Field label="공고 제목" value={jobForm.title} onChange={(v) => setJobForm((p) => ({ ...p, title: v }))} />
            <Field label="모집 포지션" value={jobForm.positionTitle} onChange={(v) => setJobForm((p) => ({ ...p, positionTitle: v }))} />
            <Field label="직무/산업 카테고리" value={jobForm.jobCategory ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, jobCategory: v }))} />
            <Field label="고용형태" value={jobForm.employmentType ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, employmentType: v }))} />
            <Field label="경력" value={jobForm.experienceLevel ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, experienceLevel: v }))} />
            <Field label="학력" value={jobForm.educationLevel ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, educationLevel: v }))} />
            <Field label="근무지" value={jobForm.workLocation ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, workLocation: v }))} />
            <Field label="급여" value={jobForm.salaryText ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, salaryText: v }))} />
            <Field label="지원 링크" value={jobForm.applyUrl ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, applyUrl: v }))} />
            <Field label="접수 마감" type="datetime-local" value={(jobForm.applicationEndAt ?? "").slice(0, 16)} onChange={(v) => setJobForm((p) => ({ ...p, applicationEndAt: v || null }))} />
            <Field label="문의 이메일" value={jobForm.contactEmail ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, contactEmail: v }))} />
            <TextField label="주요 업무" value={jobForm.duties} onChange={(v) => setJobForm((p) => ({ ...p, duties: v }))} />
            <TextField label="자격 요건" value={jobForm.qualifications ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, qualifications: v }))} />
            <TextField label="우대 사항" value={jobForm.preferred ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, preferred: v }))} />
            <TextField label="복지/혜택" value={jobForm.benefits ?? ""} onChange={(v) => setJobForm((p) => ({ ...p, benefits: v }))} />
            <div className="md:col-span-2 flex justify-end">
              <Button onClick={submitJob} disabled={!jobForm.companyName || !jobForm.title || !jobForm.positionTitle || !jobForm.duties}>
                <CheckCircle2 className="size-4" />공고 등록
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {status?.employer && jobs.map((job) => (
        <Card key={job.id}>
          <CardContent className="flex flex-col gap-2 p-4 md:flex-row md:items-center md:justify-between">
            <div>
              <div className="font-semibold text-slate-950">{job.title}</div>
              <div className="text-sm text-slate-500">{job.companyName} · {job.positionTitle} · {job.workLocation || "지역 협의"}</div>
            </div>
            <Badge variant={job.status === "PUBLISHED" ? "default" : "outline"}>{job.status} / {job.reviewStatus}</Badge>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function Field({ label, value, onChange, type = "text" }: { label: string; value: string; onChange: (value: string) => void; type?: string }) {
  return <div className="space-y-2"><Label>{label}</Label><Input type={type} value={value} onChange={(e) => onChange(e.target.value)} /></div>;
}

function TextField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <div className="space-y-2 md:col-span-2"><Label>{label}</Label><Textarea rows={4} value={value} onChange={(e) => onChange(e.target.value)} /></div>;
}
