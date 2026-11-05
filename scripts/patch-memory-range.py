from pathlib import Path
import re

path = Path("index.html")
html = path.read_text(encoding="utf-8")

# Keep the Android text export confined to the memory-range action.
start = html.find("function exportMemoryRange(){")
end = html.find("async function shareMemoryRange(){", start)
if start < 0 or end < 0:
    raise SystemExit("Could not safely locate memory range export function.")
replacement = """function exportMemoryRange(){const days=selectedMemoryRange();if(!days)return;const txt=memoryRangeText(days);if(window.AndroidBridge&&typeof window.AndroidBridge.copyText==='function'){window.AndroidBridge.copyText(txt);alert('متن کل بازه در کلیپ‌بورد کپی شد.');return}downloadText('hesab-bazeh-'+$('memoryStart').value.replace(/[\\/\\.]/g,'-')+'-'+$('memoryEnd').value.replace(/[\\/\\.]/g,'-')+'.txt',txt)}
"""
html = html[:start] + replacement + html[end:]

# Remove the Firestore rules disclosure from the backup section.
html, removed_rules = re.subn(r'<details>\s*<summary>قوانین امنیتی Firestore.*?</details>', '', html, count=1, flags=re.S)
if removed_rules == 0:
    print("Firestore rules disclosure already absent; continuing.")

# Remove the old extra whole-day details action and handler only.
extra_button = """<button onclick="showDayDetails('${day}')">جزئیات کل روز</button>"""
html = html.replace(extra_button, "")
details_start = html.find("function showDayDetails(day){")
details_end = html.find("function selectedMemoryRange(){", details_start)
if details_start >= 0:
    if details_end < 0:
        raise SystemExit("Could not safely locate end of whole-day details handler.")
    html = html[:details_start] + html[details_end:]

# Rebuild only the memory page markup. This repairs malformed markup left by
# the previous keypad insertion without changing any other page or stored data.
memory_start = html.find('<section id="page-memory"')
memory_end = html.find("</section>", memory_start)
if memory_start < 0 or memory_end < 0:
    raise SystemExit("Could not safely locate the memory page section.")
memory_end += len("</section>")
memory_section = """<section id="page-memory" class="page"><div class="sectionhead"><h2>حافظه روزهای گذشته</h2></div><p class="muted">برای دیدن جزئیات، روی هر روز بزنید.</p><div class="memory-range card"><h3>خروجی بازه تاریخی</h3><p class="muted">تاریخ شروع و پایان را به شمسی وارد کن؛ نمونه: ۱۴۰۵/۰۸/۱۴</p><div class="fieldgrid"><div class="field"><label for="memoryStart">از تاریخ</label><input id="memoryStart" type="text" autocomplete="off" placeholder="۱۴۰۵/۰۸/۱۴" aria-label="تاریخ شروع شمسی" lang="fa-IR" dir="rtl"></div><div class="field"><label for="memoryEnd">تا تاریخ</label><input id="memoryEnd" type="text" autocomplete="off" placeholder="۱۴۰۵/۰۸/۱۴" aria-label="تاریخ پایان شمسی" lang="fa-IR" dir="rtl"></div></div><div class="toolbar"><button type="button" onclick="exportMemoryRange()">خروجی متن کل بازه</button><button type="button" onclick="shareMemoryRange()">اشتراک‌گذاری متن کل بازه</button><button type="button" onclick="printMemoryRange()">فاکتور / PDF کل بازه</button><button type="button" onclick="shareMemoryRangeImage()">تصویر کل بازه</button></div></div><div id="memoryList"></div></section>"""
html = html[:memory_start] + memory_section + html[memory_end:]

# Remove the obsolete keypad CSS and handler if an earlier build inserted them.
html = re.sub(r'<style id="memory-persian-keypad-style">.*?</style>', '', html, flags=re.S)
html = re.sub(r'function memoryDateKey\(id,key\)\{.*?\}\s*', '', html)

# Confirm the rebuilt section is unique and its controls are not scattered elsewhere.
if html.count('id="page-memory"') != 1:
    raise SystemExit("Memory page uniqueness check failed.")
if html.count('id="memoryStart"') != 1 or html.count('id="memoryEnd"') != 1:
    raise SystemExit("Memory date input uniqueness check failed.")

# Confirm payment state changes before modifying records.
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

# The range inputs already contain Jalali dates. Do not pass them to
# toJalali(), which expects an ISO Gregorian day key and throws on Jalali text.
old_label = "function memoryRangeLabel(start,end){return toJalali(start)+' تا '+toJalali(end)}"
new_label = "function memoryRangeLabel(start,end){return String(start||'').trim()+' تا '+String(end||'').trim()}"
if html.count(old_label) != 1:
    raise SystemExit("Could not safely locate the memory range label function.")
html = html.replace(old_label, new_label)

path.write_text(html, encoding="utf-8")
print("Rebuilt only the memory page; preserved stored records and other pages.")
