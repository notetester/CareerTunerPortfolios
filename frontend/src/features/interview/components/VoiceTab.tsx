import { useEffect, useRef, useState } from "react";
import { Loader2, Mic, Square, Upload } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { fetchFileObjectUrl, uploadFile } from "../api/interviewApi";
import type { FileAsset } from "../types/interview";

type Phase = "idle" | "recording" | "recorded" | "uploading" | "uploaded";

/**
 * 음성 녹음 → 업로드(file_asset, kind=AUDIO) → 재생.
 * 브라우저 MediaRecorder 로 녹음하고, 업로드 후에는 인증 헤더로 다시 받아 재생한다.
 */
export function VoiceTab() {
  const [phase, setPhase] = useState<Phase>("idle");
  const [error, setError] = useState<string | null>(null);
  const [localUrl, setLocalUrl] = useState<string | null>(null);
  const [remoteUrl, setRemoteUrl] = useState<string | null>(null);
  const [asset, setAsset] = useState<FileAsset | null>(null);

  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const blobRef = useRef<Blob | null>(null);

  // object URL 누수 방지: 교체/언마운트 시 해제.
  useEffect(() => {
    return () => {
      if (localUrl) URL.revokeObjectURL(localUrl);
      if (remoteUrl) URL.revokeObjectURL(remoteUrl);
    };
  }, [localUrl, remoteUrl]);

  const supported =
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices &&
    typeof window !== "undefined" &&
    "MediaRecorder" in window;

  const startRecording = async () => {
    setError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType || "audio/webm" });
        blobRef.current = blob;
        if (localUrl) URL.revokeObjectURL(localUrl);
        setLocalUrl(URL.createObjectURL(blob));
        setPhase("recorded");
      };
      recorder.start();
      recorderRef.current = recorder;
      setPhase("recording");
    } catch {
      setError("마이크 권한을 확인해 주세요. 녹음을 시작할 수 없습니다.");
    }
  };

  const stopRecording = () => {
    recorderRef.current?.stop();
  };

  const handleUpload = async () => {
    if (!blobRef.current) return;
    setPhase("uploading");
    setError(null);
    try {
      const ext = (blobRef.current.type.split("/")[1] ?? "webm").split(";")[0];
      const uploaded = await uploadFile(blobRef.current, "AUDIO", { fileName: `interview-voice.${ext}` });
      setAsset(uploaded);
      const url = await fetchFileObjectUrl(uploaded.id);
      setRemoteUrl(url);
      setPhase("uploaded");
    } catch (err) {
      setError(err instanceof Error ? err.message : "업로드에 실패했습니다.");
      setPhase("recorded");
    }
  };

  const reset = () => {
    if (localUrl) URL.revokeObjectURL(localUrl);
    if (remoteUrl) URL.revokeObjectURL(remoteUrl);
    setLocalUrl(null);
    setRemoteUrl(null);
    setAsset(null);
    blobRef.current = null;
    setPhase("idle");
    setError(null);
  };

  return (
    <Card className="border border-slate-200 bg-white">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Mic className="size-4 text-purple-600" />
          음성 면접
          <Badge className="bg-purple-100 text-purple-700">녹음 가능</Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-slate-500">
          마이크로 답변을 녹음하고 서버에 저장합니다. 저장된 음성은 면접 기록과 함께 관리됩니다.
        </p>

        {!supported && (
          <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-700">
            이 브라우저는 녹음을 지원하지 않습니다. 최신 Chrome/Edge 에서 이용해 주세요.
          </p>
        )}

        {supported && (
          <div className="flex flex-wrap items-center gap-2">
            {phase === "idle" && (
              <Button onClick={startRecording} className="gap-1.5 bg-purple-600 hover:bg-purple-700">
                <Mic className="size-4" /> 녹음 시작
              </Button>
            )}
            {phase === "recording" && (
              <Button onClick={stopRecording} variant="destructive" className="gap-1.5">
                <Square className="size-4" /> 녹음 중지
              </Button>
            )}
            {(phase === "recorded" || phase === "uploading") && (
              <>
                <Button onClick={handleUpload} disabled={phase === "uploading"} className="gap-1.5">
                  {phase === "uploading" ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <Upload className="size-4" />
                  )}
                  {phase === "uploading" ? "업로드 중…" : "녹음 저장(업로드)"}
                </Button>
                <Button onClick={reset} variant="outline" disabled={phase === "uploading"}>
                  다시 녹음
                </Button>
              </>
            )}
            {phase === "uploaded" && (
              <Button onClick={reset} variant="outline">
                다시 녹음
              </Button>
            )}
            {phase === "recording" && (
              <span className="flex items-center gap-1.5 text-sm text-red-500">
                <span className="size-2 animate-pulse rounded-full bg-red-500" /> 녹음 중…
              </span>
            )}
          </div>
        )}

        {error && <p className="text-sm text-red-500">{error}</p>}

        {localUrl && phase !== "uploaded" && (
          <div className="space-y-1">
            <div className="text-xs font-semibold text-slate-500">녹음 미리듣기</div>
            <audio src={localUrl} controls className="w-full" />
          </div>
        )}

        {phase === "uploaded" && remoteUrl && (
          <div className="space-y-1 rounded-lg border border-green-100 bg-green-50 p-3">
            <div className="text-xs font-semibold text-green-700">
              저장 완료 (파일 #{asset?.id} · {formatSize(asset?.sizeBytes)})
            </div>
            <audio src={remoteUrl} controls className="w-full" />
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function formatSize(bytes: number | null | undefined): string {
  if (!bytes || bytes <= 0) return "0 KB";
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
