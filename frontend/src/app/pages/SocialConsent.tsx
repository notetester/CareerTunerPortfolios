import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router";
import { ShieldCheck } from "lucide-react";
import { saveMyConsents } from "../auth/consentApi";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Checkbox } from "../components/ui/checkbox";

export function SocialConsentPage() {
  const navigate = useNavigate();
  const [terms, setTerms] = useState(true);
  const [privacy, setPrivacy] = useState(true);
  const [aiData, setAiData] = useState(true);
  const [marketing, setMarketing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!terms || !privacy) {
      setError("필수 약관과 개인정보 처리방침 동의가 필요합니다.");
      return;
    }

    try {
      setSaving(true);
      await saveMyConsents({
        termsAgreed: terms,
        privacyAgreed: privacy,
        aiDataAgreed: aiData,
        marketingAgreed: marketing,
      });
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "동의 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex min-h-[calc(100vh-120px)] items-center justify-center bg-slate-50 px-4 py-10">
      <Card className="w-full max-w-lg border border-slate-200 bg-white shadow-lg">
        <CardContent className="space-y-6 p-8">
          <div className="space-y-2 text-center">
            <div className="mx-auto flex size-12 items-center justify-center rounded-xl bg-blue-600 text-white">
              <ShieldCheck className="size-6" />
            </div>
            <h1 className="text-2xl font-black text-slate-950">소셜 가입 약관 동의</h1>
            <p className="text-sm leading-6 text-slate-500">
              소셜 계정으로 처음 가입한 경우 서비스 이용에 필요한 약관 동의를 저장해야 합니다.
            </p>
          </div>

          <form className="space-y-4" onSubmit={submit}>
            <ConsentRow label="서비스 이용약관 동의" required checked={terms} onChange={setTerms} />
            <ConsentRow label="개인정보 처리방침 동의" required checked={privacy} onChange={setPrivacy} />
            <ConsentRow label="AI 데이터 사용 동의" checked={aiData} onChange={setAiData} />
            <ConsentRow label="마케팅 정보 수신 동의" checked={marketing} onChange={setMarketing} />

            {error && <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
            <Button type="submit" disabled={saving} className="h-11 w-full bg-blue-600 text-white hover:bg-blue-700">
              {saving ? "저장 중..." : "동의하고 계속하기"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

function ConsentRow({
  label,
  required = false,
  checked,
  onChange,
}: {
  label: string;
  required?: boolean;
  checked: boolean;
  onChange(next: boolean): void;
}) {
  return (
    <label className="flex items-center justify-between rounded-lg border border-slate-200 p-3 text-sm">
      <span className="font-medium text-slate-700">
        {label} {required ? <b className="text-blue-600">(필수)</b> : <span className="text-slate-400">(선택)</span>}
      </span>
      <Checkbox checked={checked} onCheckedChange={(value) => onChange(value === true)} />
    </label>
  );
}
