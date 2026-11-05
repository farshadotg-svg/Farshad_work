from pathlib import Path

path = Path("index.html")
html = path.read_text(encoding="utf-8")

old = "function exportMemoryRange(){const days=selectedMemoryRange();if(!days)return;downloadText('hesab-bazeh-'+$('memoryStart').value.replace(/[\\/\\.]/g,'-')+'-'+$('memoryEnd').value.replace(/[\\/\\.]/g,'-')+'.txt',memoryRangeText(days))}"
new = "function exportMemoryRange(){const days=selectedMemoryRange();if(!days)return;const txt=memoryRangeText(days);if(window.AndroidBridge&&typeof window.AndroidBridge.copyText==='function'){window.AndroidBridge.copyText(txt);alert('متن کل بازه در کلیپ‌بورد کپی شد.');return}downloadText('hesab-bazeh-'+$('memoryStart').value.replace(/[\\/\\.]/g,'-')+'-'+$('memoryEnd').value.replace(/[\\/\\.]/g,'-')+'.txt',txt)}"

if new not in html:
    count = html.count(old)
    if count != 1:
        raise SystemExit(f"Memory-range text export patch stopped: expected one match, found {count}.")
    html = html.replace(old, new)

# Keep the two range fields explicitly tagged as Persian text inputs.
html = html.replace('id="memoryStart" type="text" inputmode="text" lang="fa"',
                    'id="memoryStart" type="text" inputmode="text" lang="fa-IR"')
html = html.replace('id="memoryEnd" type="text" inputmode="text" lang="fa"',
                    'id="memoryEnd" type="text" inputmode="text" lang="fa-IR"')

path.write_text(html, encoding="utf-8")
print("Patched Android range-text export and Persian date-field language hints.")
