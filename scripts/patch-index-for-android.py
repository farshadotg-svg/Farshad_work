from pathlib import Path

path = Path("index.html")
html = path.read_text(encoding="utf-8")

# Keep the Android connection tab visually consistent with the rest of the app.
green_tab = '<button class="tab" data-page="cloud" style="background:linear-gradient(135deg,#145d43,#073d2b);border-color:#28dc79;font-weight:bold">☁️ اتصال گوگل</button>'
plain_tab = '<button class="tab" data-page="cloud">☁️ اتصال گوگل</button>'
if green_tab in html:
    html = html.replace(green_tab, plain_tab, 1)

# The report text action copies the complete report directly to Android clipboard.
# The backup action still uses Android's document picker to create a JSON backup.
old_backup = "function backupData(){downloadText('backup-hesab-ab.json',JSON.stringify({version:2,records,customers:JSON.parse(localStorage.getItem('waterServiceCustomers_v1')||'[]'),customerPhones:customerPhones},null,2))}"
new_backup = "function backupData(){const data=JSON.stringify({version:2,records,customers:JSON.parse(localStorage.getItem('waterServiceCustomers_v1')||'[]'),customerPhones:customerPhones},null,2);if(window.AndroidBridge&&typeof window.AndroidBridge.saveTextFile==='function'){window.AndroidBridge.saveTextFile('backup-hesab-ab.json',data,'application/json');return}downloadText('backup-hesab-ab.json',data)}"
if old_backup in html:
    html = html.replace(old_backup, new_backup, 1)

required = [
    "window.AndroidBridge.copyText(txt)",
    "window.AndroidBridge.saveImageToGalleryBase64(btoa(binary),name)",
    "window.AndroidBridge.saveTextFile('backup-hesab-ab.json'",
]
for marker in required:
    if marker not in html:
        raise SystemExit(f"Required Android integration is missing: {marker}")

path.write_text(html, encoding="utf-8")
