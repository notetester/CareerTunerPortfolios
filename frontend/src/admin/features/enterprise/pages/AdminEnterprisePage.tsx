import { useEffect, useState } from "react";
import { Building2, CheckCircle2, XCircle } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import {
  listAdminEnterpriseApplications,
  listAdminEnterpriseJobs,
  reviewAdminEnterpriseApplication,
  reviewAdminEnterpriseJob,
  type EnterpriseApplication,
  type EnterpriseJob,
} from "@/features/enterprise/api/enterpriseApi";
import { toast } from "@/features/notification/components/toast";

export function AdminEnterprisePage() {
  const [applications, setApplications] = useState<EnterpriseApplication[]>([]);
  const [jobs, setJobs] = useState<EnterpriseJob[]>([]);

  const load = async () => {
    const [appRows, jobRows] = await Promise.all([
      listAdminEnterpriseApplications(),
      listAdminEnterpriseJobs(),
    ]);
    setApplications(appRows);
    setJobs(jobRows);
  };

  useEffect(() => { void load(); }, []);

  const reviewApplication = async (id: number, status: "APPROVED" | "REJECTED") => {
    await reviewAdminEnterpriseApplication(id, {
      status,
      trusted: status === "APPROVED",
      createRequiresReview: status === "APPROVED",
      editRequiresReview: false,
      maxActivePosts: 10,
      reviewMemo: status === "APPROVED" ? "기업 정보 확인 후 승인" : "기업 정보 확인 필요",
    });
    toast.success(status === "APPROVED" ? "기업 계정을 승인했습니다." : "기업 신청을 반려했습니다.");
    await load();
  };

  const reviewJob = async (id: number, action: "APPROVE" | "REJECT") => {
    await reviewAdminEnterpriseJob(id, { action, reviewMemo: action === "APPROVE" ? "공고 검수 승인" : "공고 수정 필요" });
    toast.success(action === "APPROVE" ? "공고를 승인했습니다." : "공고를 반려했습니다.");
    await load();
  };

  return (
    <div className="space-y-5">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-black text-slate-950"><Building2 className="size-6" />기업 신청·공고 검수</h1>
        <p className="mt-1 text-sm text-slate-500">기업 계정 전환과 기업 직접 등록 공고를 운영 정책에 맞게 승인합니다.</p>
      </div>

      <Card>
        <CardHeader><CardTitle>기업 계정 신청</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          {applications.map((app) => (
            <div key={app.id} className="flex flex-col gap-3 rounded border p-3 md:flex-row md:items-center md:justify-between">
              <div>
                <div className="font-semibold">{app.companyName}</div>
                <div className="text-sm text-slate-500">{app.userEmail} · {app.businessNumber || "사업자번호 미입력"} · {app.industry || "업종 미입력"}</div>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant={app.status === "PENDING" ? "outline" : "default"}>{app.status}</Badge>
                <Button size="sm" onClick={() => reviewApplication(app.id, "APPROVED")} disabled={app.status !== "PENDING"}><CheckCircle2 className="size-4" />승인</Button>
                <Button size="sm" variant="outline" onClick={() => reviewApplication(app.id, "REJECTED")} disabled={app.status !== "PENDING"}><XCircle className="size-4" />반려</Button>
              </div>
            </div>
          ))}
          {applications.length === 0 && <div className="py-6 text-center text-sm text-slate-500">신청 내역이 없습니다.</div>}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>기업 등록 공고</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          {jobs.map((job) => (
            <div key={job.id} className="flex flex-col gap-3 rounded border p-3 md:flex-row md:items-center md:justify-between">
              <div>
                <div className="font-semibold">{job.title}</div>
                <div className="text-sm text-slate-500">{job.companyName} · {job.positionTitle} · {job.workLocation || "지역 협의"}</div>
                {job.reviewMemo && <div className="mt-1 text-xs text-slate-500">{job.reviewMemo}</div>}
              </div>
              <div className="flex items-center gap-2">
                <Badge variant={job.status === "PUBLISHED" ? "default" : "outline"}>{job.status} / {job.reviewStatus}</Badge>
                <Button size="sm" onClick={() => reviewJob(job.id, "APPROVE")} disabled={job.reviewStatus !== "PENDING"}>승인</Button>
                <Button size="sm" variant="outline" onClick={() => reviewJob(job.id, "REJECT")} disabled={job.reviewStatus !== "PENDING"}>반려</Button>
              </div>
            </div>
          ))}
          {jobs.length === 0 && <div className="py-6 text-center text-sm text-slate-500">공고 내역이 없습니다.</div>}
        </CardContent>
      </Card>
    </div>
  );
}
