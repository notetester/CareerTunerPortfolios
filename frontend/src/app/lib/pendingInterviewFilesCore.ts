export interface PendingInterviewFileCleanupResult {
  deletedIds: number[];
  failedIds: number[];
}

type DeletePendingFile = (fileId: number, keepalive: boolean) => Promise<void>;

type EpochPhase = "active" | "draining" | "sealed";

interface RegistryEpoch {
  phase: EpochPhase;
  readonly pendingFileIds: Set<number>;
  readonly protectedFileIds: Set<number>;
  readonly fileVersions: Map<number, number>;
}

interface ActiveUpload {
  readonly epoch: number;
  readonly controller: AbortController;
  settled: Promise<void>;
}

interface ActiveDeletion {
  readonly epoch: number;
  readonly promise: Promise<boolean>;
}

function validFileId(fileId: number): boolean {
  return Number.isSafeInteger(fileId) && fileId > 0;
}

function createEpoch(): RegistryEpoch {
  return {
    phase: "active",
    pendingFileIds: new Set<number>(),
    protectedFileIds: new Set<number>(),
    fileVersions: new Map<number, number>(),
  };
}

/**
 * 답변에 아직 연결되지 않은 면접 원본의 계정 수명을 관리한다.
 * 제출 중/결과 불명 파일은 protected 집합에 남겨 정리 요청과 경쟁하지 않게 한다.
 */
export class PendingInterviewFileRegistry {
  private currentEpoch = 0;
  private readonly epochs = new Map<number, RegistryEpoch>([[0, createEpoch()]]);
  private readonly activeUploads = new Set<ActiveUpload>();
  private readonly activeDeletions = new Map<number, ActiveDeletion>();

  constructor(private readonly deletePendingFile: DeletePendingFile) {}

  trackUpload<T extends { id: number }>(request: Promise<T>, controller: AbortController): Promise<T> {
    const epoch = this.currentEpoch;
    const tracked = request.then((file) => {
      this.registerInEpoch(epoch, file.id);
      return file;
    });
    const upload: ActiveUpload = {
      epoch,
      controller,
      settled: Promise.resolve(),
    };
    upload.settled = tracked.then(() => undefined, () => undefined).finally(() => {
      this.activeUploads.delete(upload);
    });
    this.activeUploads.add(upload);
    return tracked;
  }

  register(fileId: number): void {
    this.registerInEpoch(this.currentEpoch, fileId);
  }

  protect(fileId: number): void {
    if (!validFileId(fileId)) return;
    const owner = this.findEpochWithPendingFile(fileId);
    if (!owner) return;
    const [, epoch] = owner;
    epoch.protectedFileIds.add(fileId);
  }

  release(fileId: number): void {
    for (const epoch of this.epochs.values()) epoch.protectedFileIds.delete(fileId);
  }

  markLinked(fileId: number): void {
    if (!validFileId(fileId)) return;
    for (const epoch of this.epochs.values()) {
      if (epoch.pendingFileIds.has(fileId) || epoch.protectedFileIds.has(fileId)) {
        this.bumpFileVersion(epoch, fileId);
      }
      epoch.protectedFileIds.delete(fileId);
      epoch.pendingFileIds.delete(fileId);
    }
    this.pruneSealedEpochs();
  }

  isProtected(fileId: number): boolean {
    for (const epoch of this.epochs.values()) {
      if (epoch.protectedFileIds.has(fileId)) return true;
    }
    return false;
  }

  async delete(fileId: number, keepalive = false): Promise<boolean> {
    if (!validFileId(fileId)) return false;

    const active = this.activeDeletions.get(fileId);
    if (active && this.epochs.has(active.epoch)) return active.promise;

    const owner = this.findEpochWithPendingFile(fileId);
    if (!owner) return false;
    return this.deleteFromEpoch(owner[0], fileId, keepalive);
  }

  /**
   * 호출 시점의 세대들을 새 업로드와 분리한 뒤 업로드/삭제를 기다려 안전한 pending만 정리한다.
   * 보호된 결과 불명 파일은 삭제하지 않고 봉인해 두며, 로그아웃의 forget에서 서버 orphan 정리에 맡긴다.
   */
  async abortUploadsAndDiscard(keepalive = false): Promise<PendingInterviewFileCleanupResult> {
    const drainingEpochs = new Set(this.epochs.keys());
    this.rotateEpoch();

    for (const epochNumber of drainingEpochs) {
      const epoch = this.epochs.get(epochNumber);
      if (epoch?.phase === "active") epoch.phase = "draining";
    }

    const uploads = [...this.activeUploads]
      .filter((upload) => drainingEpochs.has(upload.epoch));
    for (const upload of uploads) upload.controller.abort();
    await Promise.allSettled(uploads.map((upload) => upload.settled));

    const existingDeletions = [...this.activeDeletions.values()]
      .filter((deletion) => drainingEpochs.has(deletion.epoch));
    await Promise.allSettled(existingDeletions.map((deletion) => deletion.promise));

    const targets: Array<{ epoch: number; fileId: number }> = [];
    for (const epochNumber of drainingEpochs) {
      const epoch = this.epochs.get(epochNumber);
      if (!epoch) continue;
      for (const fileId of epoch.pendingFileIds) {
        if (!epoch.protectedFileIds.has(fileId)) targets.push({ epoch: epochNumber, fileId });
      }
    }

    const results = await Promise.all(targets.map(async ({ epoch, fileId }) => ({
      fileId,
      deleted: await this.deleteFromEpoch(epoch, fileId, keepalive),
    })));

    for (const epochNumber of drainingEpochs) {
      const epoch = this.epochs.get(epochNumber);
      if (epoch) epoch.phase = "sealed";
    }
    this.pruneSealedEpochs();

    return {
      deletedIds: results.filter((result) => result.deleted).map((result) => result.fileId),
      failedIds: results.filter((result) => !result.deleted).map((result) => result.fileId),
    };
  }

  /**
   * 계정 경계를 즉시 넘긴다. 기존 요청은 중단만 하고 서버 삭제를 시도하지 않으며,
   * 이후 완료되는 업로드/삭제 콜백은 폐기된 세대에 파일을 되살릴 수 없다.
   */
  forget(): void {
    for (const upload of this.activeUploads) upload.controller.abort();
    this.epochs.clear();
    this.currentEpoch += 1;
    this.epochs.set(this.currentEpoch, createEpoch());
  }

  snapshot(): { pendingIds: number[]; protectedIds: number[]; activeUploadCount: number } {
    const pendingIds = new Set<number>();
    const protectedIds = new Set<number>();
    for (const epoch of this.epochs.values()) {
      for (const fileId of epoch.pendingFileIds) pendingIds.add(fileId);
      for (const fileId of epoch.protectedFileIds) protectedIds.add(fileId);
    }
    return {
      pendingIds: [...pendingIds],
      protectedIds: [...protectedIds],
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

  private async deleteFromEpoch(epochNumber: number, fileId: number, keepalive: boolean): Promise<boolean> {
    const active = this.activeDeletions.get(fileId);
    if (active && active.epoch === epochNumber && this.epochs.has(epochNumber)) return active.promise;

    const epoch = this.epochs.get(epochNumber);
    if (!epoch || !epoch.pendingFileIds.has(fileId) || epoch.protectedFileIds.has(fileId)) return false;

    const fileVersion = epoch.fileVersions.get(fileId) ?? 0;
    epoch.pendingFileIds.delete(fileId);
    let deletion!: Promise<boolean>;
    deletion = this.deletePendingFile(fileId, keepalive)
      .then(() => true)
      .catch(() => {
        const liveEpoch = this.epochs.get(epochNumber);
        if (liveEpoch === epoch && liveEpoch.fileVersions.get(fileId) === fileVersion) {
          liveEpoch.pendingFileIds.add(fileId);
        }
        return false;
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
        && epoch.protectedFileIds.size === 0
      ) {
        this.epochs.delete(epochNumber);
      }
    }
  }
}
