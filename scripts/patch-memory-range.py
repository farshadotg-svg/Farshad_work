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
    # Use an in-app Persian keypad: Android WebView cannot force Gboard's language.
    keypad = '''<div class="memory-keypad" aria-label="صفحه‌کلید تاریخ شمسی">
<button type="button" onclick="memoryDateKey(\'%FIELD%\',\'۱\')">۱</button><button type="button" onclick="memoryDateKey(\'%FIELD%\',\'۲\')">۲</button><button type="button" onclick="memoryDateKey(\'%FIELD%\',\'۳\')">۳</button><button type="button" onclick="memoryDateKey(\'%FIELD%\',\'/\')">/</button>
<button type="button" onclick="memoryDateKey(\'%FIELD%\',\'۴\')">۴</button><button type="button" onclick="memoryDateKey(\'%FIELD%\',\'۵\')">۵</button><button type="button" onclick="memoryDateKey(\'%FIELD%\',\'۶\')">۶</button><button type="button" onclick="memoryDateKey(\'%FIELD%\',\'⌫\')">⌫</button>
<button type="button" onclick="memoryDateKey(\'%FIELD%\',\'۷\')">۷</button><button type="button" onclick="memoryDateKey(\'%FIELD%\',\'۸\')">۸</button><button type="button" onclick="memoryDateKey(\'%FIELD%\',\'۹\')">۹</button><button type="button" onclick="memoryDateKey(\'%FIELD%\',\'۰\')">۰</button>
<button type="button" class="memory-key-clear" onclick="memoryDateKey(\'%FIELD%\',\'clear\')">پاک کردن</button></div>'''.replace('%FIELD%', field_id)
    if 'class="memory-keypad"' not in html[match.end():match.end()+500]:
        html = html[:match.end()] + keypad + html[match.end():]
    html = html.replace('id="' + field_id + '" type="text"', 'id="' + field_id + '" type="text" readonly')
    html = html.replace('id="' + field_id + '" type="text" readonly inputmode="numeric"',
                        'id="' + field_id + '" type="text" readonly inputmode="none"')



# Remove the Firestore rules disclosure from the backup section as requested.
html, removed_rules = re.subn(r'<details>\\s*<summary>قوانین امنیتی Firestore.*?</details>', '', html, count=1, flags=re.S)
if removed_rules != 1:
    raise SystemExit("Could not locate Firestore security rules disclosure in backup section.")

# Add compact Persian keypad styling.
key_css = """<style id="memory-persian-keypad-style">.memory-keypad{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:5px;margin-top:7px}.memory-keypad button{padding:7px 4px;min-height:36px;font-size:15px}.memory-keypad .memory-key-clear{grid-column:1/-1;font-size:12px}</style>"""
if 'memory-persian-keypad-style' not in html:
    html = html.replace('</head>', key_css + '</head>', 1)

# Add keypad handler before app initialization.
handler = """function memoryDateKey(id,key){const el=$(id);if(!el)return;let v=el.value||'';if(key==='clear')v='';else if(key==='⌫')v=v.slice(0,-1);else if(v.length<10)v+=key;el.value=v;el.dispatchEvent(new Event('input',{bubbles:true}));}
"""
if 'function memoryDateKey(id,key)' not in html:
    pos=html.find('function init(){')
    if pos<0: raise SystemExit("Could not find app initialization.")
    html=html[:pos]+handler+html[pos:]

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
