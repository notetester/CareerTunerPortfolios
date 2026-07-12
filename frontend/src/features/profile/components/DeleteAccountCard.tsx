import { useEffect, useState } from "react";
import { AlertTriangle, Trash2 } from "lucide-react";
import { useNavigate } from "react-router";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/app/components/ui/alert-dialog";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { useAuth } from "@/app/auth/AuthContext";
import { deleteOwnAccount, getAccountInfo } from "../api/nicknameProfileApi";

const DELETE_CONFIRMATION = "회원탈퇴";

/**
 * 계정 데이터는 FK 보존을 위해 서버에서 소프트 삭제한다. 사용자가 현재 로그인 수단과
 * 명시적 확인 문구를 다시 제시한 경우에만 요청을 보내 실수로 인한 탈퇴를 막는다.
 */
export function DeleteAccountCard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [passwordEnabled, setPasswordEnabled] = useState<boolean | null>(null);
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setPasswordEnabled(null);
    void getAccountInfo()
      .then((info) => {
        if (!cancelled && info.userId === user?.id) {
          setPasswordEnabled(info.passwordEnabled);
          setError(null);
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof Error ? cause.message : "계정 정보를 확인하지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [user?.id]);

  const close = () => {
    if (deleting) return;
    setOpen(false);
    setPassword("");
    setConfirmation("");
    setError(null);
  };

  const canDelete = passwordEnabled !== null
    && confirmation.trim() === DELETE_CONFIRMATION
    && (!passwordEnabled || password.length > 0)
    && !deleting;

  const submit = async () => {
    if (!canDelete) return;
    setDeleting(true);
    setError(null);
    try {
      await deleteOwnAccount(passwordEnabled ? password : null, confirmation.trim());
      // 서버가 refresh token을 전부 폐기한 뒤 로컬 토큰과 계정별 임시 업로드 상태도 제거한다.
      await logout();
      navigate("/", { replace: true });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "회원 탈퇴에 실패했습니다.");
      setDeleting(false);
    }
  };

  return (
    <Card className="border-destructive/30 bg-destructive/5">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base text-destructive">
          <AlertTriangle className="size-5" aria-hidden="true" />
          계정 삭제
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-sm leading-6 text-muted-foreground">
          탈퇴 즉시 모든 기기에서 로그아웃되고 계정을 다시 사용할 수 없습니다. 이메일·전화번호·로그인 수단과
          프로필 사진은 제거됩니다. 게시글·댓글·협업 메시지와 결제·감사 기록은 관계 무결성과 법적 보존을 위해
          남을 수 있지만, 일반 화면의 작성자 정보는 “탈퇴한 사용자”로 비식별 표시됩니다.
        </p>
        {error && !open && (
          <p role="alert" className="rounded-md border border-destructive/30 bg-background px-3 py-2 text-sm text-destructive">
            {error}
          </p>
        )}
        <Button
          type="button"
          variant="destructive"
          onClick={() => {
            setError(null);
            setOpen(true);
          }}
          disabled={loading || passwordEnabled === null}
        >
          <Trash2 className="size-4" aria-hidden="true" />
          {loading ? "계정 확인 중..." : "회원 탈퇴"}
        </Button>
      </CardContent>

      <AlertDialog open={open} onOpenChange={(next) => (next ? setOpen(true) : close())}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>계정을 정말 삭제할까요?</AlertDialogTitle>
            <AlertDialogDescription className="leading-6">
              이 작업은 설정 화면에서 되돌릴 수 없습니다. 아래에 <strong className="text-foreground">회원탈퇴</strong>를
              정확히 입력해 주세요. 저장된 콘텐츠 자체가 모두 삭제되는 것은 아니며 공개 신원은 비식별 처리됩니다.
            </AlertDialogDescription>
          </AlertDialogHeader>

          <div className="space-y-4">
            {passwordEnabled && (
              <label className="grid gap-1.5 text-sm font-medium text-foreground">
                현재 비밀번호
                <Input
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  disabled={deleting}
                />
              </label>
            )}
            <label className="grid gap-1.5 text-sm font-medium text-foreground">
              확인 문구
              <Input
                value={confirmation}
                onChange={(event) => setConfirmation(event.target.value)}
                placeholder={DELETE_CONFIRMATION}
                autoComplete="off"
                disabled={deleting}
              />
            </label>
            {error && (
              <p role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                {error}
              </p>
            )}
          </div>

          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>취소</AlertDialogCancel>
            <Button type="button" variant="destructive" disabled={!canDelete} onClick={() => void submit()}>
              {deleting ? "삭제 중..." : "계정 영구 비활성화"}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
