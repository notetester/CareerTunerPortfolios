interface ActiveOperation<T, M extends string> {
  model: M;
  promise: Promise<T>;
}

/**
 * 같은 대상·모델의 중복 클릭은 하나의 Promise로 합치고, 실행 중 모델 변경은
 * 앞 요청이 정리된 뒤 별도 사용자 의도로 직렬 실행한다.
 */
export class ModelAwareSingleFlight<T, M extends string> {
  private readonly active = new Map<number, ActiveOperation<T, M>>();

  run(targetId: number, model: M, operation: () => Promise<T>): Promise<T> {
    const current = this.active.get(targetId);
    if (current) {
      if (current.model === model) return current.promise;
      const queued = current.promise.then(operation, operation);
      return this.track(targetId, model, queued);
    }
    return this.track(targetId, model, operation());
  }

  clear(): void {
    this.active.clear();
  }

  private track(targetId: number, model: M, promise: Promise<T>): Promise<T> {
    const entry = { model, promise };
    this.active.set(targetId, entry);
    void promise.finally(() => {
      if (this.active.get(targetId) === entry) this.active.delete(targetId);
    }).catch(() => undefined);
    return promise;
  }
}
