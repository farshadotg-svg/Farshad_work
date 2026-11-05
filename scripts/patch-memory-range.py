from pathlib import Path
import re

path = Path("index.html")
html = path.read_text(encoding="utf-8")

new_function = """function exportMemoryRange(){const days=selectedMemoryRange();if(!days)return;const txt=memoryRangeText(days);if(window.AndroidBridge&&typeof window.AndroidBridge.copyText==='function'){window.AndroidBridge.copyText(txt);alert('متن کل بازه در کلیپ‌بورد کپی شد.');return}downloadText('hesab-bazeh-'+$('memoryStart').value.replace(/[\\/\\.]/g,'-')+'-'+$('memoryEnd').value.replace(/[\\/\\.]/g,'-')+'.txt',txt)}"""

pattern = r"function exportMemoryRange\(\)\{.*?\}(?=\s*async function shareMemoryRange)"
html, count = re.subn(pattern, new_function, html, count=1, flags=re.S)
if count != 1 and new_function not in html:
    raise SystemExit("Could not locate exportMemoryRange() to patch safely.")

html = html.replace('id="memoryStart" type="text" inputmode="text" lang="fa"',
                    'id="memoryStart" type="text" inputmode="text" lang="fa-IR"')
html = html.replace('id="memoryEnd" type="text" inputmode="text" lang="fa"',
                    'id="memoryEnd" type="text" inputmode="text" lang="fa-IR"')

path.write_text(html, encoding="utf-8")
print("Patched Android range-text export and Persian date-field language hints.")
