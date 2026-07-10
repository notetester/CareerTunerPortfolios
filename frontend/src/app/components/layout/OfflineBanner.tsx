import { useEffect, useState } from "react";
import { WifiOff } from "lucide-react";

/** 네트워크 오프라인 시 상단 안내 배너. 민감 기능은 "네트워크 연결 필요" 안내(모바일 고려 §7.3). */
export function OfflineBanner() {
  const [online, setOnline] = useState(typeof navigator === "undefined" ? true : navigator.onLine);

  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener("online", up);
    window.addEventListener("offline", down);
    return () => {
      window.removeEventListener("online", up);
      window.removeEventListener("offline", down);
    };
  }, []);

  if (online) return null;

  return (
    <div className="flex items-center justify-center gap-2 bg-amber-500 px-4 py-1.5 text-xs font-semibold text-slate-950 dark:bg-amber-600 dark:text-white">
      <WifiOff className="size-3.5" />
      오프라인 상태입니다. 네트워크가 연결되면 자동으로 동기화됩니다.
    </div>
  );
}
