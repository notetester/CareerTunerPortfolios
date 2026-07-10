import { access, cp, mkdir, rm } from "node:fs/promises";
import { resolve } from "node:path";
import type { Plugin } from "vite";

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return false;
    }
    throw error;
  }
}

/** Sites 배포 메타데이터를 Vite 산출물 안에 포함한다. */
export function sites(): Plugin {
  let root = process.cwd();

  return {
    name: "sites",
    apply: "build",
    configResolved(config) {
      root = config.root;
    },
    async closeBundle() {
      const outputDirectory = resolve(root, "dist", ".openai");
      const hostingConfig = resolve(root, ".openai", "hosting.json");
      const serverOutput = resolve(root, "dist", "server");

      // 다중 환경 빌드에서 PWA 플러그인이 서버 폴더에도 클라이언트 파일을 만든다.
      // 호스팅 런타임이 이를 Worker 모듈로 읽지 않도록 서버 산출물에서는 제거한다.
      await rm(resolve(serverOutput, "registerSW.js"), { force: true });
      await rm(resolve(serverOutput, "manifest.webmanifest"), { force: true });

      await rm(outputDirectory, { recursive: true, force: true });
      await mkdir(outputDirectory, { recursive: true });

      if (await exists(hostingConfig)) {
        await cp(hostingConfig, resolve(outputDirectory, "hosting.json"));
      }
    },
  };
}
