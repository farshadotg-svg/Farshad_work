from pathlib import Path

path = Path("index.html")
html = path.read_text(encoding="utf-8")

# Keep the Android connection tab visually consistent with the rest of the app.
green_tab = '<button class="tab" data-page="cloud" style="background:linear-gradient(135deg,#145d43,#073d2b);border-color:#28dc79;font-weight:bold">☁️ اتصال گوگل</button>'
plain_tab = '<button class="tab" data-page="cloud">☁️ اتصال گوگل</button>'
if green_tab in html:
    html = html.replace(green_tab, plain_tab, 1)

# Remove a literal escaped newline that WebView displays as stray text.
escaped_style_prefix = r'\n<style id="persian-dialog-style">'
if escaped_style_prefix in html:
    html = html.replace(escaped_style_prefix, '<style id="persian-dialog-style">', 1)
if escaped_style_prefix in html:
    raise SystemExit("Visible escaped newline remains before Persian dialog styles.")

# The report text action copies the complete report directly to Android clipboard.
# The backup action still uses Android's document picker to create a JSON backup.
old_backup = "function backupData(){downloadText('backup-hesab-ab.json',JSON.stringify({version:2,records,customers:JSON.parse(localStorage.getItem('waterServiceCustomers_v1')||'[]'),customerPhones:customerPhones},null,2))}"
new_backup = "function backupData(){const data=JSON.stringify({version:2,records,customers:JSON.parse(localStorage.getItem('waterServiceCustomers_v1')||'[]'),customerPhones:customerPhones},null,2);if(window.AndroidBridge&&typeof window.AndroidBridge.saveTextFile==='function'){window.AndroidBridge.saveTextFile('backup-hesab-ab.json',data,'application/json');return}downloadText('backup-hesab-ab.json',data)}"
if old_backup in html:
    html = html.replace(old_backup, new_backup, 1)


# Replace Android WebView's native (white) select popup with the app's themed dialog.
# The original select elements remain in the DOM as the source of truth so all
# existing form logic, change listeners, saved values, and calculations stay intact.
select_style = r"""
<style id="app-select-popup-style">
.app-select-wrap{position:relative;width:100%;min-width:0}
.app-select-wrap select{position:absolute!important;inset:0!important;width:1px!important;height:1px!important;opacity:0!important;pointer-events:none!important}
.app-select-trigger{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%;min-height:48px;padding:12px 14px;border:1px solid #344a66;border-radius:11px;background:#07101d;color:var(--text);font:inherit;text-align:right}
.app-select-trigger:after{content:"";width:9px;height:9px;flex:0 0 9px;border-left:2px solid #a8b5c7;border-bottom:2px solid #a8b5c7;transform:rotate(-45deg) translateY(-2px)}
.app-select-trigger:focus-visible{outline:2px solid var(--blue);outline-offset:2px}
#appSelectOverlay{position:fixed;inset:0;z-index:2147483001;display:none;align-items:center;justify-content:center;padding:18px;background:rgba(2,8,18,.76);backdrop-filter:blur(5px);-webkit-backdrop-filter:blur(5px)}
#appSelectOverlay.open{display:flex}
#appSelectDialog{width:min(100%,420px);max-height:min(82vh,620px);display:flex;flex-direction:column;overflow:hidden;direction:rtl;text-align:right;background:linear-gradient(155deg,#12243a,#091321 75%);color:#f4f7fb;border:1px solid #2c4664;border-radius:22px;box-shadow:0 24px 80px #0009;animation:detailsIn .18s ease-out;font-family:inherit}
#appSelectHeader{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px 18px;border-bottom:1px solid #ffffff12;background:linear-gradient(110deg,#ffffff08,#168bff0b)}
#appSelectTitle{margin:0;color:#f4f7fb;font-size:17px;font-weight:700}
#appSelectClose{width:36px;height:36px;padding:0;border:1px solid #ffffff20;border-radius:11px;background:#ffffff0b;color:#edf2f8;font:inherit;font-size:22px}
#appSelectOptions{display:grid;gap:8px;padding:14px;overflow:auto}
.app-select-option{width:100%;min-height:46px;padding:11px 14px;border:1px solid #ffffff16;border-radius:12px;background:#ffffff08;color:#eaf1fa;text-align:right;font:inherit}
.app-select-option[aria-selected="true"]{border-color:#d6ad68;background:linear-gradient(110deg,#bd91482b,#168bff16);color:#f5d99f;font-weight:700}
.app-select-option:focus-visible{outline:2px solid #f2d18d;outline-offset:2px}
body.light .app-select-trigger{background:#fff;color:#17283d;border-color:#d5deea}
body.light #appSelectDialog{background:linear-gradient(155deg,#fff,#f3f7fc);color:#17283d;border-color:#d5deea}
body.light #appSelectHeader{border-color:#e3eaf3;background:linear-gradient(110deg,#f8fbff,#edf5ff)}
body.light #appSelectTitle{color:#17283d}
body.light #appSelectClose{color:#263c55;background:#edf2f8;border-color:#d5deea}
body.light .app-select-option{color:#263c55;background:#f7f9fc;border-color:#e3eaf3}
body.light .app-select-option[aria-selected="true"]{color:#8a6428;background:#fff8e9;border-color:#bd9148}
</style>
"""
select_markup = r"""
<div id="appSelectOverlay" role="presentation" aria-hidden="true">
 <section id="appSelectDialog" role="dialog" aria-modal="true" aria-labelledby="appSelectTitle">
  <div id="appSelectHeader"><h3 id="appSelectTitle">انتخاب گزینه</h3><button id="appSelectClose" type="button" aria-label="بستن">×</button></div>
  <div id="appSelectOptions"></div>
 </section>
</div>
<script id="app-select-popup-script">
(()=>{
 const overlay=document.getElementById('appSelectOverlay');
 const title=document.getElementById('appSelectTitle');
 const optionsBox=document.getElementById('appSelectOptions');
 const closeButton=document.getElementById('appSelectClose');
 if(!overlay||!title||!optionsBox)return;
 let activeSelect=null,lastTrigger=null;
 function sync(select){
  const trigger=select.parentElement.querySelector('.app-select-trigger');
  const selected=select.options[select.selectedIndex];
  if(trigger)trigger.textContent=selected?selected.textContent:'انتخاب گزینه';
  if(trigger)trigger.setAttribute('aria-label',(select.labels&&select.labels[0]?select.labels[0].textContent+'، ':'')+(selected?selected.textContent:'انتخاب گزینه'));
 }
 function close(){
  if(!activeSelect)return;
  overlay.classList.remove('open');
  overlay.setAttribute('aria-hidden','true');
  const trigger=lastTrigger;
  activeSelect=null;lastTrigger=null;
  if(trigger)trigger.focus();
 }
 function open(select,trigger){
  activeSelect=select;lastTrigger=trigger;
  title.textContent=select.labels&&select.labels[0]?select.labels[0].textContent.trim():'انتخاب گزینه';
  optionsBox.replaceChildren();
  Array.from(select.options).forEach(option=>{
   const item=document.createElement('button');
   item.type='button';item.className='app-select-option';
   item.textContent=option.textContent;
   item.setAttribute('aria-selected',String(option.value===select.value));
   item.addEventListener('click',()=>{
    select.value=option.value;
    select.dispatchEvent(new Event('change',{bubbles:true}));
    sync(select);close();
   });
   optionsBox.appendChild(item);
  });
  overlay.classList.add('open');overlay.setAttribute('aria-hidden','false');
  const selected=optionsBox.querySelector('[aria-selected="true"]');
  (selected||optionsBox.querySelector('button'))?.focus();
 }
 document.querySelectorAll('select').forEach(select=>{
  if(select.dataset.appSelectReady==='1')return;
  select.dataset.appSelectReady='1';
  const wrap=document.createElement('div');wrap.className='app-select-wrap';
  select.parentNode.insertBefore(wrap,select);wrap.appendChild(select);
  const trigger=document.createElement('button');trigger.type='button';trigger.className='app-select-trigger';
  trigger.addEventListener('click',()=>open(select,trigger));
  wrap.appendChild(trigger);sync(select);
  select.addEventListener('change',()=>sync(select));
  if(select.form)select.form.addEventListener('reset',()=>setTimeout(()=>sync(select),0));
 });
 closeButton.addEventListener('click',close);
 overlay.addEventListener('click',event=>{if(event.target===overlay)close()});
 document.addEventListener('keydown',event=>{
  if(!activeSelect)return;
  if(event.key==='Escape'){event.preventDefault();close()}
 });
})();
</script>
"""
if "id=\"app-select-popup-script\"" not in html:
    if "</body>" not in html:
        raise SystemExit("Cannot install themed select popup: closing body tag not found.")
    html = html.replace("</body>", select_style + select_markup + "\n</body>", 1)
if "id=\"app-select-popup-script\"" not in html or "id=\"appSelectOverlay\"" not in html:
    raise SystemExit("Themed select popup was not installed correctly.")

    # Remove the redundant second Firebase initialization. The earlier startup
    # block already initializes Firebase from the saved configuration (or default).
    duplicate_firebase_init = "try{const c=DEFAULT_FIREBASE_CONFIG;localStorage.setItem('waterFirebaseConfig_v1',JSON.stringify(c));initFirebase(c)}catch(e){setCloudStatus('راه‌اندازی Firebase ناموفق بود: '+e.message)}"
    if html.count(duplicate_firebase_init) != 1:
        raise SystemExit("Expected exactly one redundant Firebase initialization block; refusing an unsafe patch.")
    html = html.replace(duplicate_firebase_init, "", 1)

required = [
    "window.AndroidBridge.copyText(txt)",
    "window.AndroidBridge.saveImageToGalleryBase64(btoa(binary),name)",
    "window.AndroidBridge.saveTextFile('backup-hesab-ab.json'",
]
for marker in required:
    if marker not in html:
        raise SystemExit(f"Required Android integration is missing: {marker}")

path.write_text(html, encoding="utf-8")
