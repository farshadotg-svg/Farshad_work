#!/usr/bin/env python3
"""Read-only diagnostics for the uploaded water-ledger HTML."""
from html.parser import HTMLParser
from pathlib import Path
import json, re, subprocess, tempfile, shutil, sys

TARGET = Path("index-farshad-v13-corrected.html")
if not TARGET.is_file() or TARGET.stat().st_size == 0:
    print(f"ERROR: {TARGET} is missing or empty")
    sys.exit(2)

raw = TARGET.read_text(encoding="utf-8", errors="replace")

class Inspect(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tags = {}
        self.ids = {}
        self.duplicates = []
        self.inline_scripts = []
        self.external_scripts = []
        self.in_script = False
        self.script_attrs = {}
        self.script_data = []
        self.has_title = False
        self.has_body = False
    def handle_starttag(self, tag, attrs):
        self.tags[tag] = self.tags.get(tag, 0) + 1
        a = dict(attrs)
        if tag == "body": self.has_body = True
        if tag == "title": self.has_title = True
        if "id" in a:
            ident = a["id"]
            self.ids[ident] = self.ids.get(ident, 0) + 1
            if self.ids[ident] == 2: self.duplicates.append(ident)
        if tag == "script":
            self.in_script = True
            self.script_attrs = a
            self.script_data = []
            if a.get("src"): self.external_scripts.append(a["src"])
    def handle_data(self, data):
        if self.in_script: self.script_data.append(data)
    def handle_endtag(self, tag):
        if tag == "script" and self.in_script:
            if not self.script_attrs.get("src"):
                typ = self.script_attrs.get("type", "").lower()
                if typ not in ("application/json", "application/ld+json", "application/manifest+json"):
                    self.inline_scripts.append({"type": typ, "code": "".join(self.script_data)})
            self.in_script = False

p = Inspect()
try:
    p.feed(raw)
    p.close()
    html_parse = "passed"
except Exception as e:
    html_parse = f"parser exception: {e}"

checks = []
node = shutil.which("node")
if node:
    for i, item in enumerate(p.inline_scripts, 1):
        code = item["code"].strip()
        if not code: continue
        suffix = ".mjs" if item["type"] == "module" else ".js"
        with tempfile.NamedTemporaryFile("w", suffix=suffix, encoding="utf-8", delete=False) as f:
            f.write(code)
            name = f.name
        result = subprocess.run([node, "--check", name], capture_output=True, text=True)
        Path(name).unlink(missing_ok=True)
        checks.append({"script": i, "ok": result.returncode == 0,
                       "error": (result.stderr or result.stdout).strip()[:3000]})
else:
    checks.append({"ok": False, "error": "Node.js is not installed; inline JS syntax checks skipped."})

report = {
    "file": str(TARGET),
    "bytes": TARGET.stat().st_size,
    "html_parser": html_parse,
    "html_tag_count": p.tags.get("html", 0),
    "head_tag_count": p.tags.get("head", 0),
    "body_present": p.has_body,
    "title_present": p.has_title,
    "script_tags": p.tags.get("script", 0),
    "inline_scripts_checked": len(checks),
    "external_scripts": p.external_scripts,
    "duplicate_ids": p.duplicates[:100],
    "javascript_checks": checks,
}
Path("html-diagnostic-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(report, ensure_ascii=False, indent=2))
if not p.has_body or not p.has_title or p.duplicates or any(not x["ok"] for x in checks):
    sys.exit(1)
