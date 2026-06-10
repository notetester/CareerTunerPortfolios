import { useEffect, useRef, useState } from "react";
import { Loader2, Square, Upload, Video } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { fetchFileObjectUrl, uploadFile } from "../api/interviewApi";
import type { FileAsset } from "../types/interview";

type Phase = "idle" | "recording" | "recorded" | "uploading" | "uploaded";

/**
 * 영상 면접 녹화 → 업로드(file_asset, kind=VIDEO) → 재생.
 * 웹캠/마이크 스트림을 라이브로 미리보기하고, MediaRecorder 로 녹화한다.
 * 영상은 용량이 크므로 서버 multipart 한도(MEDIA_MAX_FILE_SIZE_BYTES /
 * SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE)를 함께 올려야 큰 녹화도 저장된다.
 */
export function AvatarTab() {
  const [phase, setPhase] = useState<Phase>("idle");
  const [error, setError] = useState<string | null>(null);
  const [localUrl, setLocalUrl] = useState<string | null>(null);
  const [remoteUrl, setRemoteUrl] = useState<string | null>(null);
  const [asset, setAsset] = useState<FileAsset | null>(null);

  const livePreviewRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const blobRef = useRef<Blob | null>(null);

  // object URL / 스트림 누수 방지.
  useEffect(() => {
    return () => {
      if (localUrl) URL.revokeObjectURL(localUrl);
      if (remoteUrl) URL.revokeObjectURL(remoteUrl);
      streamRef.current?.getTracks().forEach((t) => t.stop());
    };
  }, [localUrl, remoteUrl]);

  const supported =
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices &&
    typeof window !== "undefined" &&
    "MediaRecorder" in window;

  const pickMimeType = (): string | undefined => {
    if (typeof MediaRecorder === "undefined") return undefined;
    const candidates = ["video/webm;codecs=vp9,opus", "video/webm;codecs=vp8,opus", "video/webm", "video/mp4"];
    return candidates.find((t) => MediaRecorder.isTypeSupported(t));
  };

  const startRecording = async () => {
    setError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      streamRef.current = stream;
      if (livePreviewRef.current) {
        livePreviewRef.current.srcObject = stream;
      }
      const mimeType = pickMimeType();
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        streamRef.current = null;
        if (livePreviewRef.current) livePreviewRef.current.srcObject = null;
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType || "video/webm" });
        blobRef.current = blob;
        if (localUrl) URL.revokeObjectURL(localUrl);
        setLocalUrl(URL.createObjectURL(blob));
        setPhase("recorded");
      };
      recorder.start();
      recorderRef.current = recorder;
      setPhase("recording");
    } catch {
      setError("카메라/마이크 권한을 확인해 주세요. 녹화를 시작할 수 없습니다.");
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
      const uploaded = await uploadFile(blobRef.current, "VIDEO", { fileName: `interview-video.${ext}` });
      setAsset(uploaded);
      const url = await fetchFileObjectUrl(uploaded.id);
      setRemoteUrl(url);
      setPhase("uploaded");
    } catch (err) {
      setError(
        err instanceof Error
          ? `${err.message} (영상이 크면 서버 업로드 한도를 올려야 합니다.)`
          : "업로드에 실패했습니다.",
      );
      setPhase("recorded");
    }
  };

  const reset = () => {
    if (localUrl) URL.revokeObjectURL(localUrl);
    if (remoteUrl) URL.revokeObjectURL(remoteUrl);
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (livePreviewRef.current) livePreviewRef.current.srcObject = null;
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
          <Video className="size-4 text-purple-600" />
          영상 면접
          <Badge className="bg-purple-100 text-purple-700">녹화 가능</Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-slate-500">
          웹캠으로 답변을 녹화하고 서버에 저장합니다. 저장된 영상은 면접 기록과 함께 관리됩니다.
        </p>

        {!supported && (
          <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-700">
            이 브라우저는 영상 녹화를 지원하지 않습니다. 최신 Chrome/Edge 에서 이용해 주세요.
          </p>
        )}

        {supported && (
          <>
            <div className="overflow-hidden rounded-xl bg-slate-900">
              {phase === "recording" ? (
                <video ref={livePreviewRef} autoPlay muted playsInline className="aspect-video w-full object-cover" />
              ) : phase === "uploaded" && remoteUrl ? (
                <video src={remoteUrl} controls playsInline className="aspect-video w-full object-cover" />
              ) : localUrl ? (
                <video src={localUrl} controls playsInline className="aspect-video w-full object-cover" />
              ) : (
                <div className="flex aspect-video w-full items-center justify-center text-slate-400">
                  <div className="text-center">
                    <Video className="mx-auto size-10" />
                    <div className="mt-2 text-sm">녹화를 시작하면 화면이 여기에 표시됩니다</div>
                  </div>
                </div>
              )}
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {phase === "idle" && (
                <Button onClick={startRecording} className="gap-1.5 bg-purple-600 hover:bg-purple-700">
                  <Video className="size-4" /> 녹화 시작
                </Button>
              )}
              {phase === "recording" && (
                <>
                  <Button onClick={stopRecording} variant="destructive" className="gap-1.5">
                    <Square className="size-4" /> 녹화 중지
                  </Button>
                  <span className="flex items-center gap-1.5 text-sm text-red-500">
                    <span className="size-2 animate-pulse rounded-full bg-red-500" /> 녹화 중…
                  </span>
                </>
              )}
              {(phase === "recorded" || phase === "uploading") && (
                <>
                  <Button onClick={handleUpload} disabled={phase === "uploading"} className="gap-1.5">
                    {phase === "uploading" ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <Upload className="size-4" />
                    )}
                    {phase === "uploading" ? "업로드 중…" : "녹화 저장(업로드)"}
                  </Button>
                  <Button onClick={reset} variant="outline" disabled={phase === "uploading"}>
                    다시 녹화
                  </Button>
                </>
              )}
              {phase === "uploaded" && (
                <Button onClick={reset} variant="outline">
                  다시 녹화
                </Button>
              )}
            </div>
          </>
        )}

        {error && <p className="text-sm text-red-500">{error}</p>}

        {phase === "uploaded" && asset && (
          <p className="text-xs font-semibold text-green-700">
            저장 완료 · 파일 #{asset.id} · {formatSize(asset.sizeBytes)}
          </p>
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
