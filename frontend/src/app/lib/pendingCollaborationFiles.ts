import { api } from "./api";

export interface PendingFileCleanupResult {
  deletedIds: number[];
  failedIds: number[];
}

const pendingFileIds = new Set<number>();
const inFlightUploads = new Set<Promise<void>>();

/** 업로드 완료 전 로그아웃/페이지 이탈도 기다릴 수 있도록 요청과 결과 ID를 함께 추적한다. */
export function trackPendingCollaborationUpload<T extends { id: number }>(request: Promise<T>): Promise<T> {
  const tracked = request.then((file) => {
    if (Number.isSafeInteger(file.id) && file.id > 0) pendingFileIds.add(file.id);
    return file;
  });
  let settled: Promise<void>;
  settled = tracked.then(() => undefined, () => undefined).finally(() => inFlightUploads.delete(settled));
  inFlightUploads.add(settled);
  return tracked;
}

/** 메시지 전송이 확정된 파일은 이후 페이지 정리 대상에서 제외한다. */
export function markCollaborationFilesLinked(fileIds: Iterable<number>): void {
  for (const fileId of fileIds) pendingFileIds.delete(fileId);
}

/** 세션 종료 뒤 이전 사용자 파일 ID가 다음 로그인 세션으로 넘어가지 않게 메모리 추적을 비운다. */
export function forgetPendingCollaborationFiles(): void {
  pendingFileIds.clear();
}

/** 사용자가 X로 제거한 단일 전송 대기 파일을 DB와 저장소에서 함께 삭제한다. */
export async function deletePendingCollaborationFile(fileId: number): Promise<void> {
  pendingFileIds.delete(fileId);
  try {
    await api<void>(`/file/${fileId}`, { method: "DELETE" });
  } catch (error) {
    pendingFileIds.add(fileId);
    throw error;
  }
}

/**
 * 현재 탭에서 만든 전송 대기 첨부만 best-effort로 정리한다.
 * 다른 기기/탭의 작성 중 파일은 ID 집합에 없으므로 건드리지 않는다.
 */
export async function discardPendingCollaborationFiles(
  fileIds?: Iterable<number>,
  options: { keepalive?: boolean } = {},
): Promise<PendingFileCleanupResult> {
  while (inFlightUploads.size > 0) {
    await Promise.allSettled([...inFlightUploads]);
  }

  const targets = [...new Set(fileIds ? [...fileIds] : [...pendingFileIds])]
    .filter((fileId) => Number.isSafeInteger(fileId) && fileId > 0);
  targets.forEach((fileId) => pendingFileIds.delete(fileId));

  const results = await Promise.allSettled(targets.map((fileId) =>
    api<void>(`/file/${fileId}`, { method: "DELETE", keepalive: options.keepalive }),
  ));
  const deletedIds: number[] = [];
  const failedIds: number[] = [];
  results.forEach((result, index) => {
    const fileId = targets[index];
    if (result.status === "fulfilled") {
      deletedIds.push(fileId);
    } else {
      failedIds.push(fileId);
      pendingFileIds.add(fileId);
    }
  });
  return { deletedIds, failedIds };
}
