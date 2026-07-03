import { useEffect, useState } from "react";
import { Megaphone, Save } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Label } from "@/app/components/ui/label";
import { Textarea } from "@/app/components/ui/textarea";
import { createAdminAd, listAdminAds, updateAdminAd, type AdCampaign, type AdCampaignRequest } from "@/features/ads/api/adsApi";
import { toast } from "@/features/notification/components/toast";

const emptyAd: AdCampaignRequest = {
  title: "",
  body: "",
  surface: "WEB",
  placement: "GLOBAL_TOP",
  creativeType: "BANNER",
  imageUrl: "",
  targetUrl: "",
  visibleToPlans: ["FREE"],
  priority: 100,
  active: true,
};

export function AdminAdsPage() {
  const [ads, setAds] = useState<AdCampaign[]>([]);
  const [form, setForm] = useState<AdCampaignRequest>(emptyAd);
  const [editingId, setEditingId] = useState<number | null>(null);

  const load = async () => setAds(await listAdminAds());
  useEffect(() => { void load(); }, []);

  const save = async () => {
    if (editingId) {
      await updateAdminAd(editingId, form);
      toast.success("광고 캠페인을 수정했습니다.");
    } else {
      await createAdminAd(form);
      toast.success("광고 캠페인을 추가했습니다.");
    }
    setEditingId(null);
    setForm(emptyAd);
    await load();
  };

  const edit = (ad: AdCampaign) => {
    setEditingId(ad.id);
    setForm({
      title: ad.title,
      body: ad.body ?? "",
      surface: ad.surface,
      placement: ad.placement,
      creativeType: ad.creativeType,
      imageUrl: ad.imageUrl ?? "",
      targetUrl: ad.targetUrl ?? "",
      visibleToPlans: ad.visibleToPlans?.length ? ad.visibleToPlans : ["FREE"],
      priority: ad.priority,
      active: ad.active,
    });
  };

  return (
    <div className="space-y-5">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-black text-slate-950"><Megaphone className="size-6" />광고 캠페인 관리</h1>
        <p className="mt-1 text-sm text-slate-500">웹, 모바일 앱, 데스크톱 앱 광고를 한 곳에서 관리합니다. 유료 플랜은 기본적으로 광고가 노출되지 않습니다.</p>
      </div>

      <Card>
        <CardHeader><CardTitle>{editingId ? "광고 수정" : "광고 추가"}</CardTitle></CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          <Field label="제목" value={form.title} onChange={(v) => setForm((p) => ({ ...p, title: v }))} />
          <Field label="대상 URL" value={form.targetUrl ?? ""} onChange={(v) => setForm((p) => ({ ...p, targetUrl: v }))} />
          <Field label="이미지 URL" value={form.imageUrl ?? ""} onChange={(v) => setForm((p) => ({ ...p, imageUrl: v }))} />
          <Field label="우선순위" type="number" value={String(form.priority ?? 100)} onChange={(v) => setForm((p) => ({ ...p, priority: Number(v) || 100 }))} />
          <div className="space-y-2">
            <Label>표면</Label>
            <select className="h-10 rounded-md border px-3" value={form.surface} onChange={(e) => setForm((p) => ({ ...p, surface: e.target.value }))}>
              <option value="WEB">WEB</option><option value="MOBILE">MOBILE</option><option value="DESKTOP">DESKTOP</option><option value="ALL">ALL</option>
            </select>
          </div>
          <div className="space-y-2">
            <Label>활성화</Label>
            <select className="h-10 rounded-md border px-3" value={form.active ? "true" : "false"} onChange={(e) => setForm((p) => ({ ...p, active: e.target.value === "true" }))}>
              <option value="true">ON</option><option value="false">OFF</option>
            </select>
          </div>
          <div className="space-y-2 md:col-span-2">
            <Label>본문</Label>
            <Textarea value={form.body ?? ""} onChange={(e) => setForm((p) => ({ ...p, body: e.target.value }))} />
          </div>
          <div className="md:col-span-2 flex justify-end">
            <Button onClick={save} disabled={!form.title.trim()}><Save className="size-4" />저장</Button>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-3">
        {ads.map((ad) => (
          <Card key={ad.id}>
            <CardContent className="flex flex-col gap-3 p-4 md:flex-row md:items-center md:justify-between">
              <div>
                <div className="font-semibold">{ad.title}</div>
                <div className="text-sm text-slate-500">{ad.surface} · {ad.placement} · {ad.targetUrl || "링크 없음"}</div>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant={ad.active ? "default" : "outline"}>{ad.active ? "ON" : "OFF"}</Badge>
                <Button size="sm" variant="outline" onClick={() => edit(ad)}>수정</Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

function Field({ label, value, onChange, type = "text" }: { label: string; value: string; onChange: (value: string) => void; type?: string }) {
  return <div className="space-y-2"><Label>{label}</Label><Input type={type} value={value} onChange={(e) => onChange(e.target.value)} /></div>;
}
