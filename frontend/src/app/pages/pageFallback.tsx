// C 페이지 lazy 로딩 중 표시하는 공용 폴백(C 담당 페이지 래퍼에서만 사용).
import { Loader2 } from "lucide-react";

export function PageFallback() {
  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto flex max-w-[1400px] items-center gap-3 px-4 py-10 text-sm text-slate-600 sm:px-6">
        <Loader2 className="size-5 animate-spin text-blue-600" />
        화면을 불러오는 중입니다.
      </div>
    </div>
  );
}
