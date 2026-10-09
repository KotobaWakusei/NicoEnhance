#!/usr/bin/env python3
"""Check translation dictionaries for duplicate keys and empty values."""
from pathlib import Path
import sys

FILES = (
    Path("app/src/main/assets/translations/zh-CN/strings.properties"),
    Path("app/src/main/assets/translations/zh-CN/exact.properties"),
)

def logical_lines(text):
    pending = ""
    for line in text.splitlines():
        pending = (pending + line.lstrip()) if pending else line
        trailing = len(pending) - len(pending.rstrip("\\"))
        if trailing % 2:
            pending = pending[:-1]
            continue
        yield pending
        pending = ""
    if pending:
        yield pending

def unescape(value):
    result = []
    i = 0
    while i < len(value):
        if value[i] != "\\" or i + 1 >= len(value):
            result.append(value[i])
            i += 1
            continue
        i += 1
        ch = value[i]
        if ch == "u" and i + 4 < len(value):
            try:
                result.append(chr(int(value[i + 1:i + 5], 16)))
                i += 5
                continue
            except ValueError:
                pass
        result.append({"t": "\t", "n": "\n", "r": "\r", "f": "\f"}.get(ch, ch))
        i += 1
    return "".join(result)

def split_property(line):
    escaped = False
    separator = len(line)
    for i, ch in enumerate(line):
        if not escaped and (ch in "=:" or ch.isspace()):
            separator = i
            break
        if ch == "\\":
            escaped = not escaped
        else:
            escaped = False
    key = line[:separator]
    i = separator
    while i < len(line) and line[i].isspace():
        i += 1
    if i < len(line) and line[i] in "=:":
        i += 1
    while i < len(line) and line[i].isspace():
        i += 1
    return unescape(key), unescape(line[i:])

def audit(path):
    entries = {}
    duplicates = []
    empty_values = []
    likely_untranslated = []
    for logical_line, logical in enumerate(logical_lines(path.read_text(encoding="utf-8")), 1):
        stripped = logical.lstrip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        key, value = split_property(logical)
        if key in entries:
            duplicates.append((key, entries[key], logical_line))
        entries[key] = logical_line
        if value == "":
            empty_values.append((key, logical_line))
        if any("\\u3040" <= ch <= "\\u30ff" for ch in value) and not any("\\u4e00" <= ch <= "\\u9fff" for ch in value):
            likely_untranslated.append((key, value, logical_line))
    print(f"{path}: {len(entries)} unique keys; {len(duplicates)} duplicate definitions; {len(empty_values)} empty values; {len(likely_untranslated)} Japanese-only value candidates")
    for key, first, duplicate in duplicates:
        print(f"  DUPLICATE {key!r}: first logical line {first}, repeated at {duplicate}")
    for key, line in empty_values:
        print(f"  EMPTY {key!r}: logical line {line}")
    for key, value, line in likely_untranslated:
        print(f"  REVIEW Japanese-only value {key!r}={value!r}: logical line {line}")
    return bool(duplicates or empty_values)

def main():
    failed = False
    for path in FILES:
        if not path.exists():
            print(f"Missing translation file: {path}", file=sys.stderr)
            return 2
        failed |= audit(path)
    return 1 if failed else 0

if __name__ == "__main__":
    raise SystemExit(main())
