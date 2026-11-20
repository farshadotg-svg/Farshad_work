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

# V16 fixes: native Android contacts/gallery actions, image rotation/sharing,
# ignored-service layout, Firebase bridge scope, and scrollable select dialogs.
def replace_once(old, new, label):
    global html
    count = html.count(old)
    if count != 1:
        raise SystemExit(f"Expected one {label} anchor, found {count}; refusing a partial patch.")
    html = html.replace(old, new, 1)

replace_once(
    "fbAuth=fbApp.auth();fbDb=fbApp.firestore();window.cloudReady=true;",
    "fbAuth=fbApp.auth();fbDb=fbApp.firestore();window.fbAuth=fbAuth;window.fbDb=fbDb;window.cloudReady=true;",
    "Firebase window bridge"
)

old_contact = "async function pickCustomerContact(){if(!('contacts' in navigator)||typeof navigator.contacts.select!=='function'){alert('انتخاب مستقیم مخاطب در این مرورگر پشتیبانی نمی‌شود. نام و شماره را دستی وارد کن؛ برنامه برای دفعات بعد شماره را به خاطر می‌سپارد.');return}try{const contacts=await navigator.contacts.select(['name','tel'],{multiple:false});if(!contacts||!contacts.length)return;const c=contacts[0],name=(c.name&&c.name[0]||'').trim(),phone=(c.tel&&c.tel[0]||'').trim();if(name)$('customer').value=name;if(phone)$('customerPhone').value=phone;$('customerPhone').dataset.edited='1';if(name&&phone)rememberCustomerPhone(name,phone);updatePreview()}catch(e){if(e&&e.name!=='AbortError')alert('مخاطب انتخاب نشد. می‌توانی نام و شماره را دستی وارد کنی.')}}"
new_contact = "async function pickCustomerContact(){if(window.AndroidBridge&&typeof window.AndroidBridge.pickContact==='function'){window.onNativeContactPicked=(name,phone)=>{if(name)$('customer').value=name;if(phone)$('customerPhone').value=phone;$('customerPhone').dataset.edited='1';if(name&&phone)rememberCustomerPhone(name,phone);updatePreview()};window.AndroidBridge.pickContact();return}if(!('contacts' in navigator)||typeof navigator.contacts.select!=='function'){alert('انتخاب مخاطب در این مرورگر پشتیبانی نمی‌شود.');return}try{const contacts=await navigator.contacts.select(['name','tel'],{multiple:false});if(!contacts||!contacts.length)return;const c=contacts[0],name=(c.name&&c.name[0]||'').trim(),phone=(c.tel&&c.tel[0]||'').trim();if(name)$('customer').value=name;if(phone)$('customerPhone').value=phone;$('customerPhone').dataset.edited='1';if(name&&phone)rememberCustomerPhone(name,phone);updatePreview()}catch(e){if(e&&e.name!=='AbortError')alert('مخاطب انتخاب نشد.')}}"
replace_once(old_contact,new_contact,"native contact picker")

old_gallery_picker = "function invoicePhotoOpenGallery(){$('invoicePhotoGallery').click()}"
new_gallery_picker = "function invoicePhotoOpenGallery(){if(window.AndroidBridge&&typeof window.AndroidBridge.setInvoicePickerMode==='function')window.AndroidBridge.setInvoicePickerMode('gallery');$('invoicePhotoGallery').click()}"
if html.count(old_gallery_picker) != 2:
    raise SystemExit(f"Expected two direct gallery picker anchors, found {html.count(old_gallery_picker)}.")
html = html.replace(old_gallery_picker, new_gallery_picker)

replace_once(
    '<div class="invoice-crop-actions"><button type="button" id="invoiceCropConfirm" class="primary">تأیید برش</button>',
    '<div class="invoice-crop-actions"><button type="button" id="invoiceCropRotate">چرخش ۹۰° ↻</button><button type="button" id="invoiceCropConfirm" class="primary">تأیید برش</button>',
    "crop rotate button"
)
replace_once(
    "img.onload=()=>{modal.classList.add('show');document.body.style.overflow='hidden';requestAnimationFrame(layout)};img.onerror=()=>close(null,new Error('Image could not be opened'));img.src=url;",
    "img.onload=()=>{modal.classList.add('show');document.body.style.overflow='hidden';requestAnimationFrame(layout)};img.onerror=()=>close(null,new Error('Image could not be opened'));$('invoiceCropRotate').onclick=()=>{try{const c=document.createElement('canvas');c.width=img.naturalHeight;c.height=img.naturalWidth;const cx=c.getContext('2d');cx.translate(c.width/2,c.height/2);cx.rotate(Math.PI/2);cx.drawImage(img,-img.naturalWidth/2,-img.naturalHeight/2);img.src=c.toDataURL('image/jpeg',.94)}catch(err){alert('چرخاندن تصویر ممکن نشد.')}};img.src=url;",
    "crop rotation handler"
)

# Keep date and ignored badge in a predictable order even when the service is ignored.
html += '\n<style id="ignored-service-date-order-v16">.service-title .service-title-name{order:1;flex:1 1 100%}.service-title .service-date{order:2;margin-inline-start:auto}.service-title .ignored-tag{order:3;flex:0 0 auto}#appSelectOptions{overflow-y:auto;-webkit-overflow-scrolling:touch;touch-action:pan-y;overscroll-behavior:contain}#appSelectDialog{overscroll-behavior:contain}#appLockBiometric{display:none!important}</style>\n'

# The classification image action must share the image, not write it to Gallery.
old_class_share = "if(saveOnly){await saveImageToGallery(blob,'gozaresh-daste.png');return}const file=new File([blob],'gozaresh-daste.png',{type:'image/png'});"
new_class_share = "if(saveOnly){await saveImageToGallery(blob,'gozaresh-daste.png');return}if(window.AndroidBridge&&typeof window.AndroidBridge.shareImageBase64==='function'){const bytes=new Uint8Array(await blob.arrayBuffer());let binary='';for(let i=0;i<bytes.length;i+=0x8000)binary+=String.fromCharCode(...bytes.subarray(i,i+0x8000));window.AndroidBridge.shareImageBase64(btoa(binary),'gozaresh-daste.png');return}const file=new File([blob],'gozaresh-daste.png',{type:'image/png'});"
replace_once(old_class_share,new_class_share,"classification image sharing")

# Ensure the generated APK never prompts for biometrics at app launch.
# The Android activity is initialized directly; hide the optional fingerprint action in the HTML lock UI.
replace_once(
    '<button type="button" id="appLockBiometric" onclick="securityTryFingerprint()">اثر انگشت</button>',
    '',
    "fingerprint login control"
)

required = [
    "window.AndroidBridge.copyText(txt)",
    "window.AndroidBridge.saveImageToGalleryBase64(btoa(binary),name)",
    "window.AndroidBridge.saveTextFile('backup-hesab-ab.json'",
]
for marker in required:
    if marker not in html:
        raise SystemExit(f"Required Android integration is missing: {marker}")


# V17: remove the legacy custom dropdown layer. V16 installs the single
# app-select dialog; keeping the older v12 MutationObserver creates two triggers.
import re
html, legacy_style_count = re.subn(r'<style id="farshad-custom-dropdowns-v12">.*?</style>', '', html, count=1, flags=re.S)
html, legacy_script_count = re.subn(r'<script id="farshad-custom-dropdowns-v12-script">.*?</script>', '', html, count=1, flags=re.S)
if legacy_style_count != 1 or legacy_script_count != 1:
    raise SystemExit(f"Expected one legacy dropdown style/script, found {legacy_style_count}/{legacy_script_count}.")
if 'id="app-select-popup-script"' not in html:
    raise SystemExit("Single custom select dialog is missing after removing legacy dropdowns.")

# Fix temporal-dead-zone startup: initialize the main UI only after the Firebase
# default config binding has been initialized.
init_marker = chr(10) + "init();" + chr(10) + chr(10) + "function applyTheme"
if html.count(init_marker) != 1:
    raise SystemExit("Could not locate the early init() call for safe Firebase initialization.")
html = html.replace(init_marker, chr(10) + "function applyTheme", 1)
firebase_boot = "try { const saved=JSON.parse(localStorage.getItem('waterFirebaseConfig_v1')||'null'); initFirebase(saved || DEFAULT_FIREBASE_CONFIG); } catch(e) { initFirebase(DEFAULT_FIREBASE_CONFIG); }"
if html.count(firebase_boot) != 1:
    raise SystemExit("Could not locate the Firebase startup block.")
html = html.replace(firebase_boot, firebase_boot + chr(10) + "init();", 1)

# Classification report image: include table heading and spacing in canvas height,
# and reserve enough footer space so both totals are visible.
old_canvas = "async function classShareCategoryImage(saveOnly=false){const rows=classCategoryRecords(),W=1200,rowH=76,headerH=240,footerH=155,canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');"
new_canvas = "async function classShareCategoryImage(saveOnly=false){const rows=classCategoryRecords(),W=1200,rowH=76,headerH=250,tableHeadH=52,footerH=180,canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');"
if html.count(old_canvas) != 1:
    raise SystemExit("Classification image function anchor not found exactly once.")
html = html.replace(old_canvas,new_canvas,1)
height_pattern = r"canvas\.width=W;canvas\.height=headerH\+Math\.max\(1,rows\.length\)\*rowH\+footerH;"
html, height_count = re.subn(height_pattern, "canvas.width=W;canvas.height=headerH+tableHeadH+Math.max(1,rows.length)*rowH+10+footerH;", html, count=1)
if height_count != 1:
    raise SystemExit(f"Classification image canvas height anchor not found (matches={height_count}).")
html = html.replace("ctx.fillText('تعداد سرویس: '+faNum(countTrips(rows))+' ',W-35,175);let y=headerH;ctx.fillStyle='#dce8f5';ctx.fillRect(20,y,W-40,52);","ctx.fillText('تعداد سرویس: '+faNum(countTrips(rows))+' ',W-35,205);let y=headerH;ctx.fillStyle='#dce8f5';ctx.fillRect(20,y,W-40,tableHeadH);",1)
if "tableHeadH" not in html or "footerH=180" not in html:
    raise SystemExit("Classification image layout validation failed.")

path.write_text(html, encoding="utf-8")
