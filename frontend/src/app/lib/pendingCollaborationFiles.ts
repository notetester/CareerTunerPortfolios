import { api } from "./api";

export interface PendingFileCleanupResult {
  deletedIds: number[];
  failedIds: number[];
}

type DeletePendingFile = (fileId: number, keepalive?: boolean) => Promise<void>;
type EpochPhase = "active" | "draining" | "sealed";

interface RegistryEpoch {
  phase: EpochPhase;
  lifecycleVersion: number;
  readonly pendingFileIds: Set<number>;
  readonly fileVersions: Map<number, number>;
}

interface ActiveUpload {
  readonly epoch: number;
  settled: Promise<void>;
}

interface ActiveDeletion {
  readonly epoch: number;
  readonly promise: Promise<DeletionOutcome>;
}

type DeletionOutcome = { deleted: true } | { deleted: false; error?: unknown };

function validFileId(fileId: number): boolean {
  return Number.isSafeInteger(fileId) && fileId > 0;
}

function createEpoch(): RegistryEpoch {
  return {
    phase: "active",
    lifecycleVersion: 0,
    pendingFileIds: new Set<number>(),
    fileVersions: new Map<number, number>(),
  };
}

/** 작성 중 협업 첨부를 계정 세대별로 격리한다. */
export class PendingCollaborationFileRegistry {
  private currentEpoch = 0;
  private accountGeneration = 0;
  private readonly epochs = new Map<number, RegistryEpoch>([[0, createEpoch()]]);
  private readonly activeUploads = new Set<ActiveUpload>();
  private readonly activeDeletions = new Map<number, ActiveDeletion>();

  constructor(private readonly deletePendingFile: DeletePendingFile) {}

  /** 업로드 완료 전 로그아웃/페이지 이탈도 기다릴 수 있도록 요청과 결과 ID를 함께 추적한다. */
  trackUpload<T extends { id: number }>(request: Promise<T>): Promise<T> {
    const epoch = this.currentEpoch;
    const tracked = request.then((file) => {
      this.registerInEpoch(epoch, file.id);
      return file;
    });
    const upload: ActiveUpload = { epoch, settled: Promise.resolve() };
    upload.settled = tracked.then(() => undefined, () => undefined).finally(() => {
      this.activeUploads.delete(upload);
    });
    this.activeUploads.add(upload);
    return tracked;
  }

  /** 메시지 전송이 확정된 파일은 이후 페이지 정리 대상에서 제외한다. */
  markLinked(fileIds: Iterable<number>): void {
    for (const fileId of fileIds) {
      if (!validFileId(fileId)) continue;
      for (const epoch of this.epochs.values()) {
        if (epoch.pendingFileIds.has(fileId)) this.bumpFileVersion(epoch, fileId);
        epoch.pendingFileIds.delete(fileId);
      }
    }
    this.pruneSealedEpochs();
  }

  /**
   * 세션 종료 시 모든 이전 세대를 즉시 폐기한다.
   * 진행 중 업로드를 중단할 수 없으므로 늦은 완료는 ID를 등록하지 않고 서버 orphan 정리에 맡긴다.
   */
  forget(): void {
    this.epochs.clear();
    this.accountGeneration += 1;
    this.currentEpoch += 1;
    this.epochs.set(this.currentEpoch, createEpoch());
  }

  /** Messenger의 X 삭제도 레지스트리에 등록된 작성 중 첨부에만 허용한다. */
  async delete(fileId: number): Promise<boolean> {
    if (!validFileId(fileId)) return false;

    const active = this.activeDeletions.get(fileId);
    const outcome = active && this.epochs.has(active.epoch)
      ? await active.promise
      : await this.deleteRegisteredFile(fileId);
    if ("error" in outcome) throw outcome.error;
    return outcome.deleted;
  }

  /**
   * 호출 시점의 현재 세대를 새 업로드와 분리해 배수한다.
   * 호출 뒤 새 세대에 등록된 첨부는 이 정리의 대상이 아니다.
   */
  async discard(
    fileIds?: Iterable<number>,
    options: { keepalive?: boolean } = {},
  ): Promise<PendingFileCleanupResult> {
    const requestedIds = fileIds == null
      ? null
      : new Set([...fileIds].filter(validFileId));
    const drainingEpoch = this.currentEpoch;
    const accountGeneration = this.accountGeneration;
    this.rotateEpoch();

    const epoch = this.epochs.get(drainingEpoch);
    if (epoch) epoch.phase = "draining";

    const uploads = [...this.activeUploads]
      .filter((upload) => upload.epoch === drainingEpoch);
    await Promise.allSettled(uploads.map((upload) => upload.settled));

    const existingDeletions = [...this.activeDeletions.values()]
      .filter((deletion) => deletion.epoch === drainingEpoch);
    await Promise.allSettled(existingDeletions.map((deletion) => deletion.promise));

    const targets: Array<{ epoch: number; fileId: number }> = [];
    const liveDrainingEpoch = this.epochs.get(drainingEpoch);
    if (liveDrainingEpoch) {
      for (const fileId of liveDrainingEpoch.pendingFileIds) {
        if (
          requestedIds == null || requestedIds.has(fileId)
        ) {
          targets.push({ epoch: drainingEpoch, fileId });
        }
      }
    }

    const results = await Promise.all(targets.map(async ({ epoch, fileId }) => ({
      fileId,
      outcome: await this.deleteFromEpoch(epoch, fileId, options.keepalive),
    })));

    const completedEpoch = this.epochs.get(drainingEpoch);
    if (completedEpoch) {
      completedEpoch.phase = "sealed";
      completedEpoch.lifecycleVersion += 1;
      if (this.accountGeneration === accountGeneration) {
        for (const fileId of completedEpoch.pendingFileIds) {
          this.registerInEpoch(this.currentEpoch, fileId);
        }
      }
      this.epochs.delete(drainingEpoch);
    }
    this.pruneSealedEpochs();

    return {
      deletedIds: results.filter((result) => result.outcome.deleted).map((result) => result.fileId),
      failedIds: results.filter((result) => !result.outcome.deleted).map((result) => result.fileId),
    };
  }

  snapshot(): { pendingIds: number[]; activeUploadCount: number } {
    const pendingIds = new Set<number>();
    for (const epoch of this.epochs.values()) {
      for (const fileId of epoch.pendingFileIds) pendingIds.add(fileId);
    }
    return {
      pendingIds: [...pendingIds],
      activeUploadCount: [...this.activeUploads]
        .filter((upload) => this.epochs.has(upload.epoch)).length,
    };
  }

  private rotateEpoch(): void {
    this.currentEpoch += 1;
    this.epochs.set(this.currentEpoch, createEpoch());
  }

  private registerInEpoch(epochNumber: number, fileId: number): void {
    if (!validFileId(fileId)) return;
    const epoch = this.epochs.get(epochNumber);
    if (!epoch || epoch.phase === "sealed") return;
    this.bumpFileVersion(epoch, fileId);
    epoch.pendingFileIds.add(fileId);
  }

  private bumpFileVersion(epoch: RegistryEpoch, fileId: number): number {
    const nextVersion = (epoch.fileVersions.get(fileId) ?? 0) + 1;
    epoch.fileVersions.set(fileId, nextVersion);
    return nextVersion;
  }

  private findEpochWithPendingFile(fileId: number): [number, RegistryEpoch] | null {
    const entries = [...this.epochs.entries()];
    for (let index = entries.length - 1; index >= 0; index -= 1) {
      const [epochNumber, epoch] = entries[index];
      if (epoch.pendingFileIds.has(fileId)) return [epochNumber, epoch];
    }
    return null;
  }

  private deleteRegisteredFile(fileId: number): Promise<DeletionOutcome> {
    const owner = this.findEpochWithPendingFile(fileId);
    if (!owner) return Promise.resolve({ deleted: false });
    return this.deleteFromEpoch(owner[0], fileId, undefined);
  }

  private async deleteFromEpoch(
    epochNumber: number,
    fileId: number,
    keepalive?: boolean,
  ): Promise<DeletionOutcome> {
    const active = this.activeDeletions.get(fileId);
    if (active && active.epoch === epochNumber && this.epochs.has(epochNumber)) return active.promise;

    const epoch = this.epochs.get(epochNumber);
    if (!epoch || !epoch.pendingFileIds.has(fileId)) return { deleted: false };

    const fileVersion = epoch.fileVersions.get(fileId) ?? 0;
    const lifecycleVersion = epoch.lifecycleVersion;
    epoch.pendingFileIds.delete(fileId);
    let request: Promise<void>;
    try {
      request = this.deletePendingFile(fileId, keepalive);
    } catch (error) {
      request = Promise.reject(error);
    }
    let deletion!: Promise<DeletionOutcome>;
    deletion = request
      .then((): DeletionOutcome => ({ deleted: true }))
      .catch((error): DeletionOutcome => {
        const liveEpoch = this.epochs.get(epochNumber);
        if (
          liveEpoch === epoch
          && liveEpoch.lifecycleVersion === lifecycleVersion
          && liveEpoch.fileVersions.get(fileId) === fileVersion
        ) {
          liveEpoch.pendingFileIds.add(fileId);
        }
        return { deleted: false, error };
      })
      .finally(() => {
        const activeDeletion = this.activeDeletions.get(fileId);
        if (activeDeletion?.promise === deletion) this.activeDeletions.delete(fileId);
        this.pruneSealedEpochs();
      });
    this.activeDeletions.set(fileId, { epoch: epochNumber, promise: deletion });
    return deletion;
  }

  private pruneSealedEpochs(): void {
    for (const [epochNumber, epoch] of this.epochs) {
      if (
        epochNumber !== this.currentEpoch
        && epoch.phase === "sealed"
        && epoch.pendingFileIds.size === 0
      ) {
        this.epochs.delete(epochNumber);
      }
    }
  }
}

const registry = new PendingCollaborationFileRegistry((fileId, keepalive) =>
  api<void>(`/file/${fileId}`, { method: "DELETE", keepalive }));

export function trackPendingCollaborationUpload<T extends { id: number }>(request: Promise<T>): Promise<T> {
  return registry.trackUpload(request);
}

export function markCollaborationFilesLinked(fileIds: Iterable<number>): void {
  registry.markLinked(fileIds);
}

export function forgetPendingCollaborationFiles(): void {
  registry.forget();
}

export async function deletePendingCollaborationFile(fileId: number): Promise<void> {
  await registry.delete(fileId);
}

export function discardPendingCollaborationFiles(
  fileIds?: Iterable<number>,
  options: { keepalive?: boolean } = {},
): Promise<PendingFileCleanupResult> {
  return registry.discard(fileIds, options);
}
