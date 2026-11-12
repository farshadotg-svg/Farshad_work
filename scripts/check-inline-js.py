#!/usr/bin/env python3
"""Validate every inline JavaScript block in the actual Android app HTML."""
from html.parser import HTMLParser
from pathlib import Path
import subprocess
import tempfile
import sys

TARGET = Path("index.html")

class ScriptParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=False)
        self.scripts = []
        self.active = False
        self.attrs = {}
        self.parts = []

    def handle_starttag(self, tag, attrs):
        if tag.lower() == "script":
            self.active = True
            self.attrs = dict(attrs)
            self.parts = []

    def handle_data(self, data):
        if self.active:
            self.parts.append(data)

    def handle_endtag(self, tag):
        if tag.lower() == "script" and self.active:
            kind = (self.attrs.get("type") or "").lower()
            if not self.attrs.get("src") and kind not in (
                "application/json", "application/ld+json", "application/manifest+json"
            ):
                self.scripts.append((kind, "".join(self.parts)))
            self.active = False
            self.attrs = {}
            self.parts = []

if not TARGET.is_file() or TARGET.stat().st_size == 0:
    print("ERROR: index.html is missing or empty")
    sys.exit(2)

parser = ScriptParser()
parser.feed(TARGET.read_text(encoding="utf-8", errors="replace"))
failures = []
checked = 0
with tempfile.TemporaryDirectory() as tmp:
    for number, (kind, source) in enumerate(parser.scripts, 1):
        if not source.strip():
            continue
        suffix = ".mjs" if kind == "module" else ".js"
        path = Path(tmp) / f"inline-{number}{suffix}"
        path.write_text(source, encoding="utf-8")
        result = subprocess.run(["node", "--check", str(path)], capture_output=True, text=True)
        checked += 1
        if result.returncode:
            failures.append((number, (result.stderr or result.stdout).strip()))

print(f"Checked {checked} inline JavaScript blocks in {TARGET}.")
if failures:
    for number, message in failures:
        print(f"ERROR in inline script #{number}:\n{message}")
    sys.exit(1)
print("All inline JavaScript blocks passed Node.js syntax validation.")
