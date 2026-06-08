import { Link, useLocation } from "react-router";
import { LogIn } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";

interface LoginRequiredStateProps {
  title?: string;
  description?: string;
}

export function LoginRequiredState({
  title = "로그인이 필요합니다",
  description = "지원 건 데이터는 로그인 후 확인할 수 있습니다.",
}: LoginRequiredStateProps) {
  const location = useLocation();

  return (
    <div className="min-h-[calc(100vh-72px)] bg-slate-50 px-4 py-12">
      <Card className="mx-auto max-w-md border-slate-200 bg-white shadow-sm">
        <CardContent className="flex flex-col items-center gap-4 p-8 text-center">
          <div className="flex size-12 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
            <LogIn className="size-6" />
          </div>
          <div className="space-y-1">
            <h1 className="text-xl font-bold text-slate-900">{title}</h1>
            <p className="text-sm leading-6 text-slate-500">{description}</p>
          </div>
          <Button asChild className="w-full bg-blue-600 text-white hover:bg-blue-700">
            <Link to="/login" state={{ from: location.pathname }}>
              로그인으로 이동
            </Link>
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
