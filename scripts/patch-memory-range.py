from pathlib import Path

path = Path("index.html")
html = path.read_text(encoding="utf-8")

start = html.find("function exportMemoryRange(){")
end = html.find("async function shareMemoryRange(){", start)
if start < 0 or end < 0:
    raise SystemExit("Could not safely locate memory range export function.")

replacement = """function exportMemoryRange(){const days=selectedMemoryRange();if(!days)return;const txt=memoryRangeText(days);if(window.AndroidBridge&&typeof window.AndroidBridge.copyText==='function'){window.AndroidBridge.copyText(txt);alert('متن کل بازه در کلیپ‌بورد کپی شد.');return}downloadText('hesab-bazeh-'+$('memoryStart').value.replace(/[\\/\\.]/g,'-')+'-'+$('memoryEnd').value.replace(/[\\/\\.]/g,'-')+'.txt',txt)}
"""
html = html[:start] + replacement + html[end:]

html = html.replace('id="memoryStart" type="text" inputmode="text" lang="fa"',
                    'id="memoryStart" type="text" inputmode="numeric" lang="fa-IR"')
html = html.replace('id="memoryEnd" type="text" inputmode="text" lang="fa"',
                    'id="memoryEnd" type="text" inputmode="numeric" lang="fa-IR"')

# Keep the payment confirmation behavior explicit in the app.
if "آیا این سرویس پرداخت شده است؟" not in html or "آیا به اشتباه «پرداخت شد» را زده‌اید؟" not in html:
    raise SystemExit("Payment confirmation handlers are not both present; refusing to alter unrelated payment logic.")

path.write_text(html, encoding="utf-8")
print("Memory-range export now copies text through AndroidBridge; Persian numeric date input enabled.")
