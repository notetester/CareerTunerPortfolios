import { type FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router";
import { ArrowLeft, Briefcase, Loader2 } from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Checkbox } from "@/app/components/ui/checkbox";
import { Input } from "@/app/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import { createApplicationCase } from "../api/applicationCasesApi";
import { LoginRequiredState } from "../components/LoginRequiredState";
import type { ApplicationSourceType } from "../types/applicationCase";
import { APPLICATION_SOURCE_OPTIONS } from "../types/applicationCase";

interface FormState {
  companyName: string;
  jobTitle: string;
  postingDate: string;
  deadlineDate: string;
  sourceType: ApplicationSourceType;
  favorite: boolean;
}

export function NewApplicationPage() {
  const navigate = useNavigate();
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [form, setForm] = useState<FormState>({
    companyName: "",
    jobTitle: "",
    postingDate: "",
    deadlineDate: "",
    sourceType: "TEXT",
    favorite: false,
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const setField = <Key extends keyof FormState>(key: Key, value: FormState[Key]) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const companyName = form.companyName.trim();
    const jobTitle = form.jobTitle.trim();

    if (!companyName || !jobTitle) {
      setError("기업명과 직무명을 입력해주세요.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const created = await createApplicationCase({
        companyName,
        jobTitle,
        postingDate: form.postingDate || null,
        deadlineDate: form.deadlineDate || null,
        sourceType: form.sourceType,
        status: "DRAFT",
        favorite: form.favorite,
      });
      navigate(`/applications/${created.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건을 생성하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  if (authLoading) {
    return (
      <div className="min-h-[calc(100vh-72px)] bg-slate-50 px-4 py-10">
        <div className="mx-auto max-w-3xl">
          <div className="h-96 animate-pulse rounded-lg bg-slate-200" />
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <LoginRequiredState
        title="새 지원 건은 로그인 후 만들 수 있습니다"
        description="지원 건은 사용자별 데이터로 저장됩니다."
      />
    );
  }

  return (
    <div className="min-h-[calc(100vh-72px)] bg-slate-50">
      <div className="mx-auto max-w-3xl space-y-6 px-4 py-8 sm:px-6">
        <Button asChild variant="ghost" className="px-0 text-slate-600 hover:bg-transparent hover:text-blue-700">
          <Link to="/applications">
            <ArrowLeft className="size-4" />
            목록으로
          </Link>
        </Button>

        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold text-slate-950">
            <Briefcase className="size-6 text-blue-600" />
            새 지원 건
          </h1>
          <p className="mt-1 text-sm text-slate-500">기업과 직무 정보를 입력합니다.</p>
        </div>

        <Card className="border-slate-200 bg-white">
          <CardHeader>
            <CardTitle className="text-lg font-bold text-slate-900">기본 정보</CardTitle>
          </CardHeader>
          <CardContent>
            <form className="space-y-5" onSubmit={(event) => void handleSubmit(event)}>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700" htmlFor="companyName">
                    기업명
                  </label>
                  <Input
                    id="companyName"
                    value={form.companyName}
                    onChange={(event) => setField("companyName", event.target.value)}
                    placeholder="예: 카카오페이"
                    autoComplete="organization"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700" htmlFor="jobTitle">
                    직무명
                  </label>
                  <Input
                    id="jobTitle"
                    value={form.jobTitle}
                    onChange={(event) => setField("jobTitle", event.target.value)}
                    placeholder="예: 프론트엔드 개발자"
                  />
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700" htmlFor="postingDate">
                    공고일
                  </label>
                  <Input
                    id="postingDate"
                    type="date"
                    value={form.postingDate}
                    onChange={(event) => setField("postingDate", event.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700" htmlFor="deadlineDate">
                    마감일
                  </label>
                  <Input
                    id="deadlineDate"
                    type="date"
                    value={form.deadlineDate}
                    onChange={(event) => setField("deadlineDate", event.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700">등록 방식</label>
                  <Select
                    value={form.sourceType}
                    onValueChange={(value) => setField("sourceType", value as ApplicationSourceType)}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {APPLICATION_SOURCE_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <label className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
                <Checkbox
                  checked={form.favorite}
                  onCheckedChange={(checked) => setField("favorite", Boolean(checked))}
                />
                즐겨찾기
              </label>

              {error && (
                <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                  {error}
                </div>
              )}

              <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <Button type="button" variant="outline" onClick={() => navigate("/applications")}>
                  취소
                </Button>
                <Button type="submit" className="bg-blue-600 text-white hover:bg-blue-700" disabled={submitting}>
                  {submitting && <Loader2 className="size-4 animate-spin" />}
                  지원 건 생성
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
