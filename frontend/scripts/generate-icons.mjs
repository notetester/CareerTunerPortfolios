// PWA/앱 아이콘 생성기. 폰트 의존성이 없는 기하학적 마크(다크 배경 + "맞춤/fit" 타겟 링)를
// SVG로 정의하고 sharp 로 각 크기 PNG 를 만든다. 소스 로고가 따로 없어 코드로 생성한다.
//   사용: node scripts/generate-icons.mjs
import sharp from "sharp";
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const outDir = resolve(root, "public/icons");

const BG = "#030213";     // theme_color
const FG = "#ffffff";
const ACCENT = "#6366f1"; // indigo (앱 전반 강조색)

// 512 기준. content 가 중앙 안전영역(80%) 안에 들어가 maskable 로도 사용 가능.
const svg = (size, { bg = true } = {}) => `
<svg width="${size}" height="${size}" viewBox="0 0 512 512" xmlns="http://www.w3.org/2000/svg">
  ${bg ? `<rect width="512" height="512" rx="112" fill="${BG}"/>` : ""}
  <circle cx="256" cy="256" r="150" fill="none" stroke="${FG}" stroke-width="26"/>
  <circle cx="256" cy="256" r="95"  fill="none" stroke="${ACCENT}" stroke-width="26"/>
  <circle cx="256" cy="256" r="26"  fill="${FG}"/>
</svg>`;

const targets = [
  { name: "icon-192.png", size: 192 },
  { name: "icon-512.png", size: 512 },
  { name: "icon-maskable-512.png", size: 512 }, // 안전영역 내 컨텐츠 → maskable 겸용
  { name: "apple-touch-icon.png", size: 180 },
  { name: "favicon-32.png", size: 32 },
];

await mkdir(outDir, { recursive: true });
await writeFile(resolve(outDir, "icon.svg"), svg(512).trim());
for (const t of targets) {
  await sharp(Buffer.from(svg(t.size))).png().toFile(resolve(outDir, t.name));
  console.log("generated", t.name);
}
console.log("done →", outDir);
