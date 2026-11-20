from pathlib import Path
import re
p=Path("index.html")
s=p.read_text(encoding="utf-8")
css="""<style id="farshad-ui-polish">
.field>label{display:inline-flex;align-items:center;align-self:flex-start;margin:2px 0 3px;padding:6px 11px 6px 13px;border-right:3px solid var(--blue);border-radius:8px 5px 5px 8px;background:linear-gradient(90deg,#168bff10,#168bff24);color:#e8f2ff;font-size:14px;font-weight:700;line-height:1.5}
body.light .field>label{color:#17436d;background:linear-gradient(90deg,#168bff08,#168bff16)}
.service{display:flex;flex-direction:column;gap:9px;margin:11px 0;padding:13px!important;border:1px solid #29435f!important;border-radius:15px!important;background:linear-gradient(145deg,#101e30,#091321)!important;box-shadow:0 5px 14px #0003}
.service-topline{display:flex;direction:rtl;align-items:center;justify-content:space-between;gap:10px;padding-bottom:8px;border-bottom:1px solid #2a4058}
.service-topline .service-date{order:0;font-size:12px;border-color:#45617e;background:#ffffff08}
.service-topline .ignored-tag{order:1;margin-right:auto;font-size:11px;padding:4px 9px}
.service-title{display:block;margin:0!important;font-size:14px!important;font-weight:700!important;line-height:1.8!important}
.service-title-name{display:block}
.service-info{gap:9px}
.service-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;direction:rtl}
.service-metric{display:flex;align-items:center;justify-content:space-between;gap:6px;min-width:0;padding:8px 9px;border:1px solid #263d56;border-radius:10px;background:#ffffff05}
.service-metric small{color:var(--muted);font-size:11px;white-space:nowrap}
.service-metric b{color:#e8c66b;font-size:12px;overflow-wrap:anywhere;text-align:left}
.service-metric.source{grid-column:1/-1}
.service-description{padding:8px 10px;border-right:2px solid #537da7;border-radius:7px;background:#ffffff06}
.service-actions{padding-top:8px;margin-top:0;border-top:1px solid #263d56}
.service.ignored-service{border-color:#98702d!important;background:linear-gradient(145deg,#282315,#111827)!important}
body.light .service{background:linear-gradient(145deg,#fff,#f5f8fc)!important;border-color:#d3deea!important;box-shadow:0 4px 13px #253c5710}
body.light .service-topline,body.light .service-actions{border-color:#dce5ef}
body.light .service-metric{background:#f7faff;border-color:#dce5ef}
body.light .service-metric b{color:#916300}
body.light .service.ignored-service{background:linear-gradient(145deg,#fff9e9,#fff)!important;border-color:#d4aa55!important}
@media(max-width:380px){.service{padding:10px!important}.service-metric{padding:7px}.service-metric small{font-size:10px}.service-metric b{font-size:11px}}
html,body{background-color:#050a12}
body:not(.app-entered) .app{display:none!important}
</style>"""
if 'id="farshad-ui-polish"' in s:
 s=re.sub(r'<style id="farshad-ui-polish">.*?</style>',css,s,count=1,flags=re.S)
else:
 s=s.replace("</head>",css+"</head>",1)
pat=re.compile(r"function serviceHTML\\(r\\)\\{return `.*?`\\}\\s*function toggleDay",re.S)
fn=r"""function serviceHTML(r){return `<div class="service ${r.ignored?'ignored-service':''}"><div class="service-topline"><span class="service-date">${esc(toJalali(r.date))}</span>${r.ignored?'<span class="ignored-tag">نادیده گرفته شده</span>':''}</div><div class="service-title"><span class="service-title-name">${faNum(r.count)} سرویس ${esc(r.customer)} از ${esc(r.source)}</span></div><div class="service-info"><div class="service-metrics"><div class="service-metric"><small>تعداد سرویس</small><b>${enNum(r.count)}</b></div><div class="service-metric"><small>مبلغ کل</small><b>${enNum(r.count*r.price)} تومان</b></div><div class="service-metric"><small>قیمت هر سرویس</small><b>${enNum(r.price)} تومان</b></div><div class="service-metric source"><small>محل آبگیری</small><b>${esc(r.source||'—')}</b></div></div>${r.description?`<div class="service-description">یادداشت: ${esc(r.description)}</div>`:''}</div><div class="service-actions"><button onclick="editService('${r.id}')">✏️ ویرایش</button><button class="danger" onclick="deleteService('${r.id}')">🗑 حذف</button><button type="button" class="${r.paid?'paid':'unpaid'}" onclick="togglePaid('${r.id}')" aria-label="تغییر وضعیت پرداخت">${r.paid?'✓ پرداخت شده':'✕ پرداخت نشده'}</button><button type="button" onclick="showServiceDetails('${r.id}')">جزئیات</button></div></div>`}
function toggleDay"""
s,n=pat.subn(lambda m: fn,s,count=1)
if n!=1: raise SystemExit("Could not safely replace service renderer: "+str(n))
js="""<script id="farshad-theme-toggle-fix">(function(){const b=document.getElementById('themeToggle'),m=document.getElementById('themeColorMeta');if(!b)return;function apply(light){document.body.classList.toggle('light',!!light);b.textContent=light?'🌙':'☀️';b.title=light?'فعال‌کردن حالت شب':'فعال‌کردن حالت روز';b.setAttribute('aria-label',b.title);if(m)m.content=light?'#f3f6fa':'#07101e'}let light=false;try{light=localStorage.getItem('farshadTheme')==='light'}catch(e){}apply(light);b.addEventListener('click',()=>{light=!document.body.classList.contains('light');apply(light);try{localStorage.setItem('farshadTheme',light?'light':'dark')}catch(e){}})})();</script>"""
if 'id="farshad-theme-toggle-fix"' in s:
 s=re.sub(r'<script id="farshad-theme-toggle-fix">.*?</script>',js,s,count=1,flags=re.S)
else:s=s.replace("</body>",js+"</body>",1)
if 'service-topline' not in s or 'farshad-theme-toggle-fix' not in s: raise SystemExit("UI patch validation failed")
p.write_text(s,encoding="utf-8")
print("UI polish applied")
