import assert from "node:assert/strict";
import test from "node:test";

import {
  ChatbotRequestScope,
  chatbotAccountKey,
  interviewHandoffStorageKey,
} from "../src/features/support/hooks/chatbotAccountScopeCore.mjs";

test("계정별 챗봇과 면접 인계 저장소 키를 분리한다", () => {
  assert.equal(chatbotAccountKey(101), "user:101");
  assert.equal(chatbotAccountKey(202), "user:202");
  assert.equal(chatbotAccountKey(null), "guest");
  assert.equal(interviewHandoffStorageKey(101), "tunerbot:awaitInterview:101");
  assert.equal(interviewHandoffStorageKey(202), "tunerbot:awaitInterview:202");
  assert.equal(interviewHandoffStorageKey(null), null);
});

test("계정 전환은 진행 중인 모든 요청을 즉시 중단하고 이전 snapshot을 폐기한다", () => {
  const scope = new ChatbotRequestScope(101);
  const reply = scope.begin("reply");
  const sessions = scope.begin("sessions", { conversationBound: false });

  assert.equal(scope.switchAccount(202), true);
  assert.equal(reply.controller.signal.aborted, true);
  assert.equal(sessions.controller.signal.aborted, true);
  assert.equal(scope.isCurrent(reply), false);
  assert.equal(scope.isCurrent(sessions), false);
  assert.equal(scope.accountKey, "user:202");
  assert.equal(scope.switchAccount(202), false);
});

test("대화 전환은 대화 요청만 중단하고 같은 계정의 목록 조회는 유지한다", () => {
  const scope = new ChatbotRequestScope(101);
  const history = scope.begin("history");
  const sessions = scope.begin("sessions", { conversationBound: false });

  scope.invalidateConversation();

  assert.equal(history.controller.signal.aborted, true);
  assert.equal(scope.isCurrent(history), false);
  assert.equal(sessions.controller.signal.aborted, false);
  assert.equal(scope.isCurrent(sessions), true);
});

test("같은 lane의 새 요청은 이전 응답을 무효화한다", () => {
  const scope = new ChatbotRequestScope(101);
  const first = scope.begin("reply");
  const second = scope.begin("reply");

  assert.equal(first.controller.signal.aborted, true);
  assert.equal(scope.isCurrent(first), false);
  assert.equal(scope.isCurrent(second), true);
  scope.finish(second);
  assert.equal(scope.isCurrent(second), false);
});
