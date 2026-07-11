import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const [apiSource, authSource, registrySource] = await Promise.all([
  readFile(new URL("../src/features/autoprep/api/autoPrepApi.ts", import.meta.url), "utf8"),
  readFile(new URL("../src/app/auth/AuthContext.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/app/lib/pendingAutoPrepFiles.ts", import.meta.url), "utf8"),
]);

assert.match(apiSource, /fd\.append\("refType", "AUTO_PREP_PENDING"\)/);
assert.match(apiSource, /trackPendingAutoPrepUpload\(/);
assert.match(apiSource, /evt\.type === "done"/);
assert.match(apiSource, /discardPendingAutoPrepFiles\(req\.attachmentFileIds\)/);
assert.match(authSource, /discardPendingAutoPrepFiles\(\)/);
assert.match(authSource, /forgetPendingAutoPrepFiles\(\)/);
assert.match(registrySource, /PendingCollaborationFileRegistry/);

console.log("pending AutoPrep attachment lifecycle contract: ok");
