import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const NAV_FILES = [
  "src/app/components/layout/Header.tsx",
  "src/app/components/layout/Footer.tsx",
  "src/app/components/layout/MobileBottomNav.tsx",
  "src/app/components/layout/MobileMoreSheet.tsx",
  "src/admin/components/AdminShell.tsx",
];

const routeSources = await Promise.all([
  readFile("src/app/routes.ts", "utf8"),
  readFile("src/admin/routes.ts", "utf8"),
]);

const routePatterns = routeSources
  .flatMap((source) => [
    ...[...source.matchAll(/\bpath:\s*"([^"]+)"/g)].map((match) => `/${match[1]}`),
    ...[...source.matchAll(/\badminRoute\(\s*"([^"]+)"/g)].map((match) => `/${match[1]}`),
  ])
  .map((path) => path.replace(/\/$/, "") || "/");

function routeMatches(href) {
  const path = href.split(/[?#]/)[0].replace(/\/$/, "") || "/";
  return routePatterns.some((pattern) => {
    const pathSegments = path.split("/").filter(Boolean);
    const patternSegments = pattern.split("/").filter(Boolean);
    if (pathSegments.length !== patternSegments.length) return false;
    return patternSegments.every((segment, index) => segment.startsWith(":") || segment === pathSegments[index]);
  });
}

for (const file of NAV_FILES) {
  const source = await readFile(file, "utf8");
  const links = [...source.matchAll(/\blabel:\s*"([^"]+)"(?:(?!\blabel:)[\s\S])*?\bhref:\s*"([^"]+)"/g)]
    .map((match) => ({ label: match[1], href: match[2] }))
    .filter((item) => item.href.startsWith("/"));

  assert.ok(links.length > 0, `${file}: 검사할 내비게이션 링크가 없습니다.`);

  for (const link of links) {
    assert.equal(
      link.href.includes("?"),
      false,
      `${file}: '${link.label}'은 query tab이 아니라 독립 pathname을 사용해야 합니다 (${link.href}).`,
    );
    assert.ok(routeMatches(link.href), `${file}: '${link.label}'의 라우트가 등록되지 않았습니다 (${link.href}).`);
  }

  const labelsByHref = new Map();
  for (const link of links) {
    const previous = labelsByHref.get(link.href);
    assert.ok(
      !previous || previous === link.label,
      `${file}: 서로 다른 메뉴 '${previous}'와 '${link.label}'이 같은 페이지 ${link.href}를 가리킵니다.`,
    );
    labelsByHref.set(link.href, link.label);
  }
}

console.log(`navigation integrity: ${NAV_FILES.length}개 내비게이션 소스와 ${routePatterns.length}개 라우트 확인 완료`);
