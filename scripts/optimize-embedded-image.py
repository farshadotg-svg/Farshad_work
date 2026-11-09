#!/usr/bin/env python3
"""Losslessly reduce the embedded banner image in the generated Android HTML."""
from pathlib import Path
from io import BytesIO
import base64
import re
import sys

path = Path("index.html")
if not path.is_file():
    raise SystemExit("ERROR: index.html not found")

html = path.read_text(encoding="utf-8")
pattern = re.compile(
    r'(?P<before><img\b[^>]*?\bsrc=["\'])data:image/png;base64,'
    r'(?P<data>[A-Za-z0-9+/=]+)(?P<after>["\'][^>]*>)',
    re.IGNORECASE,
)
match = pattern.search(html)
if not match:
    if "data:image/webp;base64," in html:
        print("Embedded banner is already WebP; no conversion needed.")
        sys.exit(0)
    raise SystemExit("ERROR: embedded PNG banner was not found")

try:
    from PIL import Image
except ImportError as exc:
    raise SystemExit("ERROR: Pillow is required for lossless WebP conversion") from exc

original = base64.b64decode(match.group("data"), validate=True)
with Image.open(BytesIO(original)) as image:
    image.load()
    if image.format != "PNG":
        raise SystemExit(f"ERROR: expected PNG banner, found {image.format}")
    output = BytesIO()
    image.save(output, format="WEBP", lossless=True, method=6)
    optimized = output.getvalue()

if len(optimized) >= len(original):
    print(f"Keeping original PNG ({len(original):,} bytes); WebP was not smaller.")
    sys.exit(0)

encoded = base64.b64encode(optimized).decode("ascii")
replacement = (
    match.group("before") + "data:image/webp;base64," +
    encoded + match.group("after")
)
html = html[:match.start()] + replacement + html[match.end():]
path.write_text(html, encoding="utf-8")
saved = len(original) - len(optimized)
print(
    f"Embedded banner converted losslessly: {len(original):,} -> "
    f"{len(optimized):,} bytes (saved {saved:,} bytes, "
    f"{saved / len(original) * 100:.1f}%)."
)
