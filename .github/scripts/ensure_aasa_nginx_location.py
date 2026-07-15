#!/usr/bin/env python3
"""Install the exact AASA JSON location in the active CareerTuner TLS server block."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile


LOCATION_PATH = "/.well-known/apple-app-site-association"
LOCATION_BLOCK = (
    "\n    location = /.well-known/apple-app-site-association {\n"
    "        default_type application/json;\n"
    "        try_files $uri =404;\n"
    "    }\n"
)


@dataclass(frozen=True)
class ServerBlock:
    start: int
    opening_brace: int
    closing_brace: int
    text: str

    @property
    def is_tls(self) -> bool:
        return bool(re.search(r"\blisten\s+[^;]*(?:443|ssl)\b", self.text))


def _matching_brace(text: str, opening_brace: int) -> int:
    depth = 0
    quote: str | None = None
    escaped = False
    in_comment = False

    for index in range(opening_brace, len(text)):
        character = text[index]
        if in_comment:
            if character == "\n":
                in_comment = False
            continue
        if quote:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = None
            continue
        if character == "#":
            in_comment = True
            continue
        if character in {'"', "'"}:
            quote = character
            continue
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return index
    raise ValueError("닫히지 않은 nginx server block입니다.")


def server_blocks(text: str) -> list[ServerBlock]:
    blocks: list[ServerBlock] = []
    for match in re.finditer(r"\bserver\s*\{", text):
        opening = text.find("{", match.start(), match.end())
        closing = _matching_brace(text, opening)
        blocks.append(ServerBlock(match.start(), opening, closing, text[match.start() : closing + 1]))
    return blocks


def _serves_hostname(block: ServerBlock, hostname: str) -> bool:
    escaped_hostname = re.escape(hostname)
    return bool(re.search(rf"\bserver_name\s+[^;]*\b{escaped_hostname}\b[^;]*;", block.text))


def choose_tls_server(configs: dict[Path, str], hostname: str) -> tuple[Path, ServerBlock]:
    candidates = [
        (path, block)
        for path, text in configs.items()
        for block in server_blocks(text)
        if _serves_hostname(block, hostname)
    ]
    tls_candidates = [(path, block) for path, block in candidates if block.is_tls]
    selected = tls_candidates or candidates
    if len(selected) != 1:
        labels = ", ".join(str(path) for path, _ in selected) or "없음"
        raise ValueError(f"{hostname} 활성 server block을 하나로 결정할 수 없습니다: {labels}")
    return selected[0]


def _server_level_text(text: str) -> str:
    """Return only directives declared directly inside a server block."""
    depth = 0
    quote: str | None = None
    escaped = False
    in_comment = False
    visible: list[str] = []

    for character in text:
        if in_comment:
            visible.append("\n" if character == "\n" else " ")
            if character == "\n":
                in_comment = False
            continue
        if quote:
            visible.append(character if depth <= 1 else " ")
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = None
            continue
        if character == "#":
            in_comment = True
            visible.append(" ")
            continue
        if character in {'"', "'"}:
            quote = character
            visible.append(character if depth <= 1 else " ")
            continue
        if character == "{":
            visible.append(character if depth <= 1 else " ")
            depth += 1
            continue
        if character == "}":
            depth -= 1
            visible.append(character if depth <= 1 else " ")
            continue
        visible.append(character if depth <= 1 else " ")
    return "".join(visible)


def server_root(block: ServerBlock) -> PurePosixPath:
    roots = re.findall(r"\broot\s+([^;]+);", _server_level_text(block.text))
    if len(roots) != 1:
        raise ValueError(f"선택한 TLS server block의 root를 하나로 결정할 수 없습니다: {roots or '없음'}")
    raw_root = roots[0].strip().strip('"\'')
    if "$" in raw_root:
        raise ValueError(f"변수가 포함된 nginx root는 자동 배포할 수 없습니다: {raw_root}")
    root = PurePosixPath(raw_root)
    if not root.is_absolute():
        raise ValueError(f"nginx root는 절대 경로여야 합니다: {raw_root}")
    return root


def ensure_aasa_location(text: str, block: ServerBlock) -> tuple[str, bool]:
    exact_location = re.compile(rf"\blocation\s*=\s*{re.escape(LOCATION_PATH)}\s*\{{")
    existing = exact_location.search(block.text)
    if existing:
        existing_start = block.start + existing.start()
        existing_opening = text.find("{", existing_start, block.closing_brace)
        existing_closing = _matching_brace(text, existing_opening)
        existing_text = text[existing_start : existing_closing + 1]
        has_json_type = bool(re.search(r"\bdefault_type\s+application/json\s*;", existing_text))
        has_exact_file = bool(re.search(r"\btry_files\s+\$uri\s+=404\s*;", existing_text))
        if has_json_type and has_exact_file:
            return text, False
        replacement = LOCATION_BLOCK.strip("\n")
        return text[:existing_start] + replacement + text[existing_closing + 1 :], True
    return text[: block.closing_brace] + LOCATION_BLOCK + text[block.closing_brace :], True


def install(hostname: str, config_paths: list[Path], *, validate: bool = True) -> tuple[Path, bool]:
    resolved_paths = list(dict.fromkeys(path.resolve() for path in config_paths))
    configs = {path: path.read_text(encoding="utf-8") for path in resolved_paths}
    target, block = choose_tls_server(configs, hostname)
    updated, changed = ensure_aasa_location(configs[target], block)
    if not changed:
        if validate:
            subprocess.run(["nginx", "-t"], check=True)
        return target, False

    with tempfile.NamedTemporaryFile(prefix="careertuner-nginx-", suffix=".conf", delete=False) as handle:
        backup = Path(handle.name)
    shutil.copy2(target, backup)
    try:
        target.write_text(updated, encoding="utf-8")
        shutil.copymode(backup, target)
        if validate:
            subprocess.run(["nginx", "-t"], check=True)
            subprocess.run(["nginx", "-s", "reload"], check=True)
    except BaseException:
        shutil.copy2(backup, target)
        if validate:
            subprocess.run(["nginx", "-t"], check=False)
            subprocess.run(["nginx", "-s", "reload"], check=False)
        raise
    finally:
        backup.unlink(missing_ok=True)
    return target, True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--hostname", default="careertuner.example.com")
    parser.add_argument("configs", nargs="+", type=Path)
    args = parser.parse_args()
    configs = {path.resolve(): path.resolve().read_text(encoding="utf-8") for path in args.configs}
    _, block = choose_tls_server(configs, args.hostname)
    root = server_root(block)
    target, changed = install(args.hostname, args.configs)
    print(f"AASA nginx location {'updated' if changed else 'already configured'}: {target}")
    print(f"CAREERTUNER_ROOT={root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
