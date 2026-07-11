export const IOS_APP_BUNDLE_ID = "com.careertuner.app";
export const IOS_APP_LINK_HOST = "careertuner.kro.kr";
export const IOS_ASSOCIATED_DOMAINS_ENTITLEMENT = `applinks:${IOS_APP_LINK_HOST}`;
export const IOS_ENTITLEMENTS_PROJECT_PATH = "App/App.entitlements";
export const IOS_CUSTOM_URL_SCHEME = "careertuner";

const APPLE_TEAM_ID_PATTERN = /^[A-Z0-9]{10}$/;
const UNIVERSAL_LINK_COMPONENTS = Object.freeze([
  Object.freeze({
    "/": "/auth/callback",
    comment: "CareerTuner native OAuth handoff result",
  }),
  Object.freeze({
    "/": "/profile/detail",
    comment: "CareerTuner native social account link result",
  }),
]);

export function parseAppleTeamIds(raw, { allowEmpty = false } = {}) {
  if (typeof raw !== "string" || !raw.trim()) {
    if (allowEmpty) return [];
    throw new Error("IOS_APP_LINK_TEAM_IDS가 비어 있습니다.");
  }

  const teamIds = raw
    .split(/[;,\n]/)
    .map((value) => value.trim().toUpperCase())
    .filter(Boolean);

  if (!teamIds.length || teamIds.some((value) => !APPLE_TEAM_ID_PATTERN.test(value))) {
    throw new Error("Apple Team ID는 영문 대문자와 숫자로 이루어진 10자리여야 합니다.");
  }
  return [...new Set(teamIds)];
}

export function createAppleAppSiteAssociation(rawTeamIds, { allowEmpty = false } = {}) {
  const teamIds = parseAppleTeamIds(rawTeamIds, { allowEmpty });
  return {
    applinks: {
      details: teamIds.length === 0
        ? []
        : [
          {
            appIDs: teamIds.map((teamId) => `${teamId}.${IOS_APP_BUNDLE_ID}`),
            components: UNIVERSAL_LINK_COMPONENTS.map((component) => ({ ...component })),
          },
        ],
    },
  };
}

export function createAssociatedDomainsEntitlements() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
\t<key>com.apple.developer.associated-domains</key>
\t<array>
\t\t<string>${IOS_ASSOCIATED_DOMAINS_ENTITLEMENT}</string>
\t</array>
</dict>
</plist>
`;
}

function findMatchingArrayClose(source, arrayOpenIndex) {
  const tagPattern = /<\/?array\s*>/g;
  tagPattern.lastIndex = arrayOpenIndex;
  let depth = 0;
  for (let match = tagPattern.exec(source); match; match = tagPattern.exec(source)) {
    if (match[0].startsWith("</")) {
      depth -= 1;
      if (depth === 0) return match.index;
    } else {
      depth += 1;
    }
  }
  return -1;
}

function urlTypesContainScheme(urlTypesBlock, scheme) {
  const schemesKeyPattern = /<key>CFBundleURLSchemes<\/key>/g;
  for (let match = schemesKeyPattern.exec(urlTypesBlock); match; match = schemesKeyPattern.exec(urlTypesBlock)) {
    const keyEnd = match.index + match[0].length;
    const arrayOpenIndex = urlTypesBlock.indexOf("<array>", keyEnd);
    if (arrayOpenIndex < 0) throw new Error("CFBundleURLSchemes의 array를 찾지 못했습니다.");
    const arrayCloseIndex = findMatchingArrayClose(urlTypesBlock, arrayOpenIndex);
    if (arrayCloseIndex < 0) throw new Error("CFBundleURLSchemes의 닫는 array를 찾지 못했습니다.");
    if (urlTypesBlock.slice(arrayOpenIndex, arrayCloseIndex).includes(`<string>${scheme}</string>`)) {
      return true;
    }
    schemesKeyPattern.lastIndex = arrayCloseIndex;
  }
  return false;
}

export function patchInfoPlistCustomScheme(infoPlistSource) {
  if (typeof infoPlistSource !== "string" || !infoPlistSource.includes("<plist") || !infoPlistSource.includes("<dict>")) {
    throw new Error("iOS Info.plist 형식이 올바르지 않습니다.");
  }

  const newline = infoPlistSource.includes("\r\n") ? "\r\n" : "\n";
  const urlTypesKeyPattern = /<key>CFBundleURLTypes<\/key>/g;
  const keyMatches = [...infoPlistSource.matchAll(urlTypesKeyPattern)];
  if (keyMatches.length > 1) throw new Error("Info.plist에 CFBundleURLTypes가 중복되어 있습니다.");

  const urlTypeEntry = [
    "\t\t<dict>",
    "\t\t\t<key>CFBundleURLName</key>",
    `\t\t\t<string>${IOS_APP_BUNDLE_ID}</string>`,
    "\t\t\t<key>CFBundleURLSchemes</key>",
    "\t\t\t<array>",
    `\t\t\t\t<string>${IOS_CUSTOM_URL_SCHEME}</string>`,
    "\t\t\t</array>",
    "\t\t</dict>",
  ].join(newline);

  if (keyMatches.length === 1) {
    const keyEnd = keyMatches[0].index + keyMatches[0][0].length;
    const arrayOpenIndex = infoPlistSource.indexOf("<array>", keyEnd);
    if (arrayOpenIndex < 0) throw new Error("CFBundleURLTypes의 array를 찾지 못했습니다.");
    const arrayCloseIndex = findMatchingArrayClose(infoPlistSource, arrayOpenIndex);
    if (arrayCloseIndex < 0) throw new Error("CFBundleURLTypes의 닫는 array를 찾지 못했습니다.");

    const urlTypesBlock = infoPlistSource.slice(arrayOpenIndex, arrayCloseIndex);
    if (urlTypesContainScheme(urlTypesBlock, IOS_CUSTOM_URL_SCHEME)) return infoPlistSource;
    return `${infoPlistSource.slice(0, arrayCloseIndex)}${urlTypeEntry}${newline}\t${infoPlistSource.slice(arrayCloseIndex)}`;
  }

  const rootDictClose = infoPlistSource.lastIndexOf("</dict>");
  if (rootDictClose < 0) throw new Error("Info.plist의 최상위 dict를 찾지 못했습니다.");
  const urlTypesBlock = [
    "\t<key>CFBundleURLTypes</key>",
    "\t<array>",
    urlTypeEntry,
    "\t</array>",
  ].join(newline);
  return `${infoPlistSource.slice(0, rootDictClose)}${urlTypesBlock}${newline}${infoPlistSource.slice(rootDictClose)}`;
}

export function patchXcodeProjectAssociatedDomains(projectSource) {
  if (typeof projectSource !== "string" || !projectSource.includes("/* Begin XCBuildConfiguration section */")) {
    throw new Error("Xcode project.pbxproj 형식이 올바르지 않습니다.");
  }

  let targetConfigurationCount = 0;
  let patchedConfigurationCount = 0;
  const configurationPattern = /(\t\t[0-9A-F]+ \/\* (?:Debug|Release) \*\/ = \{[\s\S]*?\n\t\t\};)/g;
  const patched = projectSource.replace(configurationPattern, (block) => {
    if (!block.includes(`PRODUCT_BUNDLE_IDENTIFIER = ${IOS_APP_BUNDLE_ID};`)) return block;
    targetConfigurationCount += 1;

    const existing = block.match(/^[ \t]*CODE_SIGN_ENTITLEMENTS = ([^;]+);/m);
    if (existing) {
      if (existing[1] !== IOS_ENTITLEMENTS_PROJECT_PATH) {
        throw new Error(`기존 CODE_SIGN_ENTITLEMENTS(${existing[1]})를 자동으로 덮어쓸 수 없습니다.`);
      }
      patchedConfigurationCount += 1;
      return block;
    }

    const marker = /^(\t+)(CODE_SIGN_STYLE = Automatic;)$/m;
    if (!marker.test(block)) {
      throw new Error("App target의 CODE_SIGN_STYLE 설정을 찾지 못했습니다.");
    }
    patchedConfigurationCount += 1;
    return block.replace(marker, `$1CODE_SIGN_ENTITLEMENTS = ${IOS_ENTITLEMENTS_PROJECT_PATH};\n$1$2`);
  });

  if (targetConfigurationCount !== 2 || patchedConfigurationCount !== 2) {
    throw new Error(`App target Debug/Release 설정 2개를 기대했지만 ${targetConfigurationCount}개를 찾았습니다.`);
  }
  return patched;
}
