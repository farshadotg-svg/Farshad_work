from pathlib import Path

path = Path("index.html")
html = path.read_text(encoding="utf-8")

# Replace the entire range-export function using stable neighboring function names.
start = html.find("function exportMemoryRange(){")
end = html.find("async function shareMemoryRange(){", start)
if start < 0 or end < 0:
    raise SystemExit("Could not safely locate memory range export function.")
replacement = """function exportMemoryRange(){const days=selectedMemoryRange();if(!days)return;const txt=memoryRangeText(days);if(window.AndroidBridge&&typeof window.AndroidBridge.copyText==='function'){window.AndroidBridge.copyText(txt);alert('متن کل بازه در کلیپ‌بورد کپی شد.');return}downloadText('hesab-bazeh-'+$('memoryStart').value.replace(/[\\/\\.]/g,'-')+'-'+$('memoryEnd').value.replace(/[\\/\\.]/g,'-')+'.txt',txt)}
"""
html = html[:start] + replacement + html[end:]

# Persian Android keyboard hint with numeric keypad for Jalali digits.
import re

for field_id in ("memoryStart", "memoryEnd"):
    pattern = r'(<input\b(?=[^>]*\bid="' + field_id + r'")[^>]*)(>)'
    match = re.search(pattern, html)
    if not match:
        raise SystemExit("Could not locate Persian memory date input: " + field_id)
    tag = match.group(1)
    tag = re.sub(r'\s+inputmode="[^"]*"', '', tag)
    tag = re.sub(r'\s+lang="[^"]*"', '', tag)
    tag += ' inputmode="numeric" lang="fa-IR"'
    html = html[:match.start()] + tag + match.group(2) + html[match.end():]

# Remove the extra whole-day details action and its now-unused handler.
extra_button = """<button onclick="showDayDetails('${day}')">جزئیات کل روز</button>"""
html = html.replace(extra_button, "")
details_start = html.find("function showDayDetails(day){")
details_end = html.find("function selectedMemoryRange(){", details_start)
if details_start >= 0:
    if details_end < 0:
        raise SystemExit("Could not safely locate end of whole-day details handler.")
    html = html[:details_start] + html[details_end:]

# Enforce confirmation on both transitions without touching other UI.
pstart = html.find("function togglePaid(id){")
if pstart < 0:
    raise SystemExit("Could not locate payment toggle handler.")
pend = html.find("\nfunction ", pstart + 1)
if pend < 0:
    pend = html.find("\nasync function ", pstart + 1)
if pend < 0:
    raise SystemExit("Could not locate end of payment toggle handler.")
payment = """function togglePaid(id){const item=records.find(r=>String(r.id)===String(id));if(!item)return;if(item.paid){if(!confirm('آیا به اشتباه «پرداخت شد» را زده‌اید؟'))return;records=records.map(r=>String(r.id)===String(id)?{...r,paid:false,updatedAt:Date.now()}:r)}else{if(!confirm('آیا این سرویس پرداخت شده است؟'))return;records=records.map(r=>String(r.id)===String(id)?{...r,paid:true,updatedAt:Date.now()}:r)}persist()}"""
html = html[:pstart] + payment + html[pend:]

path.write_text(html, encoding="utf-8")
print("Patched Persian memory date input, Android memory text export, and payment confirmations.")
