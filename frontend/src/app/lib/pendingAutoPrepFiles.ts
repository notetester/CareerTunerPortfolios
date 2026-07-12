import { api } from "./api";
import {
  PendingCollaborationFileRegistry,
  type PendingFileCleanupResult,
} from "./pendingCollaborationFiles";

// AutoPrep 첨부도 업로드→소비 사이에만 존재하므로 검증된 세대형 pending registry를 재사용한다.
const registry = new PendingCollaborationFileRegistry((fileId, keepalive) =>
  api<void>(`/file/${fileId}`, { method: "DELETE", keepalive }));

export function trackPendingAutoPrepUpload<T extends { id: number }>(request: Promise<T>): Promise<T> {
  return registry.trackUpload(request);
}

export function forgetPendingAutoPrepFiles(): void {
  registry.forget();
}

export async function deletePendingAutoPrepFile(fileId: number): Promise<void> {
  await registry.delete(fileId);
}

export function discardPendingAutoPrepFiles(
  fileIds?: Iterable<number>,
  options: { keepalive?: boolean } = {},
): Promise<PendingFileCleanupResult> {
  return registry.discard(fileIds, options);
}
