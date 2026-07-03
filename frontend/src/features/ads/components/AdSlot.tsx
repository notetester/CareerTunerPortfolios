import { useEffect, useState } from "react";
import { ExternalLink } from "lucide-react";
import { getAds, recordAdEvent, type AdCampaign } from "../api/adsApi";
import { isNativeApp } from "@/platform/capacitor";

function surface(): "WEB" | "MOBILE" {
  return isNativeApp() ? "MOBILE" : "WEB";
}

export function AdSlot() {
  const [ad, setAd] = useState<AdCampaign | null>(null);
  const currentSurface = surface();

  useEffect(() => {
    let ignore = false;
    getAds(currentSurface)
      .then((rows) => {
        if (ignore) return;
        const first = rows[0] ?? null;
        setAd(first);
        if (first) void recordAdEvent(first.id, currentSurface, "IMPRESSION").catch(() => undefined);
      })
      .catch(() => setAd(null));
    return () => { ignore = true; };
  }, [currentSurface]);

  if (!ad) return null;

  const content = (
    <div className="mx-auto flex w-full max-w-[1400px] items-center gap-3 px-4 py-2 text-sm sm:px-6">
      {ad.imageUrl && (
        <img src={ad.imageUrl} alt="" className="h-9 w-16 shrink-0 rounded object-cover" loading="lazy" />
      )}
      <div className="min-w-0 flex-1">
        <div className="truncate font-semibold text-slate-950">{ad.title}</div>
        {ad.body && <div className="truncate text-xs text-slate-600">{ad.body}</div>}
      </div>
      {ad.targetUrl && <ExternalLink className="size-4 shrink-0 text-slate-500" />}
    </div>
  );

  if (!ad.targetUrl) {
    return <div className="border-b border-slate-200 bg-white">{content}</div>;
  }
  return (
    <a
      href={ad.targetUrl}
      target="_blank"
      rel="noreferrer"
      className="block border-b border-slate-200 bg-white transition hover:bg-slate-50"
      onClick={() => void recordAdEvent(ad.id, currentSurface, "CLICK").catch(() => undefined)}
    >
      {content}
    </a>
  );
}
