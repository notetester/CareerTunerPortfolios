const GUEST_ACCOUNT_KEY = "guest";

export function chatbotAccountKey(accountId) {
  return Number.isSafeInteger(accountId) && accountId > 0
    ? `user:${accountId}`
    : GUEST_ACCOUNT_KEY;
}

export function interviewHandoffStorageKey(accountId) {
  return Number.isSafeInteger(accountId) && accountId > 0
    ? `tunerbot:awaitInterview:${accountId}`
    : null;
}

/**
 * 계정과 대화 문맥에 비동기 요청을 묶는 작은 수명주기 관리자.
 * React state와 독립적이라 계정 변경을 render 시점에 즉시 반영하고,
 * 늦게 끝난 요청은 isCurrent()에서 일관되게 폐기할 수 있다.
 */
export class ChatbotRequestScope {
  #accountKey;
  #accountGeneration = 0;
  #conversationGeneration = 0;
  #lanes = new Map();

  constructor(accountId) {
    this.#accountKey = chatbotAccountKey(accountId);
  }

  get accountKey() {
    return this.#accountKey;
  }

  switchAccount(accountId) {
    const nextKey = chatbotAccountKey(accountId);
    if (nextKey === this.#accountKey) return false;
    this.abortAll();
    this.#accountKey = nextKey;
    this.#accountGeneration += 1;
    this.#conversationGeneration += 1;
    return true;
  }

  invalidateConversation() {
    this.#conversationGeneration += 1;
    for (const [lane, request] of this.#lanes) {
      if (!request.conversationBound) continue;
      request.controller.abort();
      this.#lanes.delete(lane);
    }
  }

  begin(lane, options = {}) {
    this.cancelLane(lane);
    const request = {
      lane,
      controller: new AbortController(),
      accountKey: this.#accountKey,
      accountGeneration: this.#accountGeneration,
      conversationGeneration: this.#conversationGeneration,
      conversationBound: options.conversationBound !== false,
    };
    this.#lanes.set(lane, request);
    return request;
  }

  isCurrent(request) {
    return Boolean(
      request
      && !request.controller.signal.aborted
      && this.#lanes.get(request.lane) === request
      && request.accountKey === this.#accountKey
      && request.accountGeneration === this.#accountGeneration
      && (!request.conversationBound
        || request.conversationGeneration === this.#conversationGeneration),
    );
  }

  finish(request) {
    if (this.#lanes.get(request.lane) === request) {
      this.#lanes.delete(request.lane);
    }
  }

  cancelLane(lane) {
    const request = this.#lanes.get(lane);
    if (!request) return;
    request.controller.abort();
    this.#lanes.delete(lane);
  }

  abortAll() {
    for (const request of this.#lanes.values()) {
      request.controller.abort();
    }
    this.#lanes.clear();
  }
}
