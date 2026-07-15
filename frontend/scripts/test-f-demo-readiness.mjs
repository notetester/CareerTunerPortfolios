import { readFile } from "node:fs/promises";
import test from "node:test";
import assert from "node:assert/strict";

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("고객센터 검색은 입력, Enter, 버튼, 인기 검색어에서 같은 실행 경로를 사용한다", async () => {
  const source = await read("src/features/support/pages/SupportHomePage.tsx");
  assert.match(source, /const searchFaq =/);
  assert.match(source, /event\.key === "Enter"/);
  assert.match(source, /onClick=\{\(\) => searchFaq\(\)\}/);
  assert.match(source, /onClick=\{\(\) => searchFaq\(t\)\}/);
  assert.match(source, /item\.question.*item\.answer/s);
});

test("비로그인 문의는 실패하는 폼 대신 로그인 복귀 경로를 보여준다", async () => {
  const source = await read("src/features/support/pages/ContactPage.tsx");
  assert.match(source, /if \(!isAuthenticated\)/);
  assert.match(source, /returnTo=%2Fsupport%2Fcontact/);
  assert.match(source, /로그인 후 문의를 접수해 주세요/);
});

test("전용 AI 상담 화면은 위젯의 완전한 패널 구현을 공유한다", async () => {
  const fullScreen = await read("src/features/support/components/ChatbotFullScreen.tsx");
  const widget = await read("src/features/support/components/ChatbotWidget.tsx");
  assert.match(fullScreen, /<ChatbotPanel chatbot=\{chatbot\} embedded/);
  assert.match(widget, /export function ChatbotPanel/);
  assert.match(widget, /AutoPrepWorkView/);
  assert.match(widget, /ModelPicker/);
  assert.match(widget, /to="\/support\/contact\?channel=agent"/);
});

test("챗봇 마이크는 가짜 대기 상태가 아니라 실제 브라우저 STT를 시작한다", async () => {
  const hook = await read("src/features/support/hooks/useChatbot.ts");
  const stt = await read("src/features/interview/hooks/speechToText.ts");
  assert.match(hook, /new BrowserSttTracker/);
  assert.match(hook, /tracker\.start\(\)/);
  assert.doesNotMatch(hook, /navigator\.mediaDevices/);
  assert.match(stt, /interimResults = true/);
  assert.match(stt, /onTranscript\?\./);
});

test("390px 커뮤니티에서 필터와 검색이 페이지 폭을 넘지 않는다", async () => {
  const css = await read("src/features/community/styles/community.css");
  assert.match(css, /\.cv-page \{[^}]*width: 100%;[^}]*min-width: 0;/s);
  assert.match(css, /@media \(max-width: 600px\)[\s\S]*\.cv-bar \{[^}]*flex-wrap: wrap;/);
  assert.match(css, /\.cv-bar \.right \{[^}]*width: 100% !important;/s);
});

test("커뮤니티 데모는 운영과 같은 category-counts 전수 집계 계약을 제공한다", async () => {
  const api = await read("src/features/community/api/communityApi.ts");
  const mock = await read("src/app/lib/mock/domains/community.ts");
  assert.match(api, /api<Record<string, number>>\("\/community\/posts\/category-counts"\)/);
  assert.match(mock, /pattern: \/\^\\\/community\\\/posts\\\/category-counts\$\//);
  assert.match(mock, /function publishedCategoryCounts\(\)[\s\S]*post\.status !== "PUBLISHED"[\s\S]*counts\[post\.category\]/);
  assert.match(mock, /handler: \(\) => publishedCategoryCounts\(\)/);
});

test("커뮤니티 글쓰기 본문과 하단 작업 버튼은 같은 폭을 사용한다", async () => {
  const css = await read("src/features/community/styles/community.css");
  assert.match(css, /\.wv-wrap \{[^}]*max-width: 1120px;/s);
  assert.match(css, /\.wv-foot__in \{[^}]*width: 100%;[^}]*max-width: none;/s);
});

test("주간 인기글 mock과 UI는 숫자 게시글 id로만 상세 링크를 만든다", async () => {
  const mock = await read("src/app/lib/mock/domains/community.ts");
  const api = await read("src/features/community/api/communityApi.ts");
  const sidebar = await read("src/features/community/components/HotPostsSidebar.tsx");
  assert.match(mock, /const HOT_POSTS: \{ id: number;/);
  assert.match(mock, /\{ id: 5102,[\s\S]*\{ id: 5101,[\s\S]*\{ id: 5104,/);
  assert.match(api, /posts\.filter\(\(post\) => Number\.isSafeInteger\(post\.id\) && post\.id > 0\)/);
  assert.match(api, /"\/community\/posts\/hot", \{\}, \{ auth: true \}/);
  assert.match(sidebar, /key=\{post\.id\}[\s\S]*to=\{`\/community\/posts\/\$\{post\.id\}`\}/);
  assert.doesNotMatch(sidebar, /post\.id \?\? i/);
});
