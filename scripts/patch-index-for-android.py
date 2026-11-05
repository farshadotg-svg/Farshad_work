from pathlib import Path

path = Path("index.html")
html = path.read_text(encoding="utf-8")
if "window.AndroidBridge&&typeof window.AndroidBridge.copyText" in html and "window.AndroidBridge&&typeof window.AndroidBridge.printHtml" in html:
    print("Android bridge patches are already present.")
    raise SystemExit(0)
bt = chr(96)
day_template = bt + "hesab-" + "$" + "{day}.txt" + bt
factor_template = bt + "factor-" + "$" + "{toJalali(day).replaceAll('/','-')}.png" + bt

replacements = [
(
"function exportDay(day){downloadText("+day_template+",dayText(day))}",
"function exportDay(day){const text=dayText(day);if(window.AndroidBridge&&typeof window.AndroidBridge.copyText==='function'){window.AndroidBridge.copyText(text);alert('گزارش کامل روز در کلیپ‌بورد کپی شد.');return}if(navigator.clipboard&&window.isSecureContext){navigator.clipboard.writeText(text).then(()=>alert('گزارش کامل روز کپی شد.')).catch(()=>downloadText("+day_template+",text));return}downloadText("+day_template+",text)}"
),
(
"async function shareDay(day){let text=dayText(day);if(navigator.share){try{await navigator.share({title:'حساب سرویس‌های آب',text})}catch(e){}}else{try{await navigator.clipboard.writeText(text);alert('متن روز کپی شد.')}catch{downloadText("+day_template+",text)}}}",
"async function shareDay(day){const text=dayText(day);if(window.AndroidBridge&&typeof window.AndroidBridge.shareText==='function'){window.AndroidBridge.shareText('حساب سرویس‌های آب',text);return}if(navigator.share){try{await navigator.share({title:'حساب سرویس‌های آب',text})}catch(e){if(e.name!=='AbortError')alert('اشتراک‌گذاری متن انجام نشد.')}}else{try{await navigator.clipboard.writeText(text);alert('اشتراک‌گذاری در این مرورگر پشتیبانی نمی‌شود؛ متن کپی شد.')}catch{downloadText("+day_template+",text)}}}"
),
(
"if(!blob){alert('ساخت تصویر ممکن نشد. لطفاً صفحه را در مرورگر Chrome باز کنید و دوباره تلاش کنید.');return}\n const file=",
"if(!blob){alert('ساخت تصویر ممکن نشد. لطفاً صفحه را در مرورگر Chrome باز کنید و دوباره تلاش کنید.');return}\n if(window.AndroidBridge&&typeof window.AndroidBridge.shareImageBase64==='function'){try{const bytes=new Uint8Array(await blob.arrayBuffer());let binary='';for(let i=0;i<bytes.length;i+=0x8000)binary+=String.fromCharCode(...bytes.subarray(i,i+0x8000));window.AndroidBridge.shareImageBase64(btoa(binary),"+factor_template+");return}catch(e){alert('آماده‌سازی تصویر برای اشتراک‌گذاری ناموفق بود: '+e.message);return}}\n const file="
),
(
";let w=window.open('','_blank');if(!w){alert('مرورگر پنجره جدید را مسدود کرده؛ اجازه بازشدن پنجره را بدهید.');return}w.document.open();w.document.write(html2);w.document.close()}",
";if(window.AndroidBridge&&typeof window.AndroidBridge.printHtml==='function'){window.AndroidBridge.printHtml(html2);return}let w=window.open('','_blank');if(!w){alert('مرورگر پنجره جدید را مسدود کرده؛ اجازه بازشدن پنجره را بدهید.');return}w.document.open();w.document.write(html2);w.document.close()}"
)
]
for old, new in replacements:
    count = html.count(old)
    if count != 1:
        raise SystemExit(f"Patch stopped: expected one match, found {count}: {old[:90]}")
    html = html.replace(old, new)

path.write_text(html, encoding="utf-8")
print("Patched 4 handlers in the original app HTML.")
