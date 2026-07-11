import { Link } from "react-router";
import { Compass } from "lucide-react";
import { Button } from "@/app/components/ui/button";

/**
 * catch-all 404. 죽은 링크·오타 URL 이 스타일 없는 라우터 기본 오류 화면 대신 안내와 복귀 경로를 보여준다.
 * (라우터 children 마지막의 path:"*" 로 연결 — 레이아웃(헤더/푸터) 안에서 렌더된다.)
 */
export function NotFoundPage() {
  return (
    <div className="mx-auto flex max-w-xl flex-col items-center px-4 py-24 text-center">
      <div className="flex size-14 items-center justify-center rounded-2xl bg-blue-50 text-blue-600">
        <Compass className="size-7" />
      </div>
      <h1 className="mt-5 text-2xl font-black text-slate-900">페이지를 찾을 수 없습니다</h1>
      <p className="mt-2 text-sm leading-6 text-slate-500">
        주소가 바뀌었거나 삭제된 페이지일 수 있습니다. 아래에서 이동할 곳을 선택해 주세요.
      </p>
      <div className="mt-6 flex flex-wrap justify-center gap-2">
        <Button asChild className="bg-blue-600 text-white hover:bg-blue-700">
          <Link to="/dashboard">대시보드로</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/applications">지원 건 관리</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/support">고객센터</Link>
        </Button>
      </div>
    </div>
  );
}
