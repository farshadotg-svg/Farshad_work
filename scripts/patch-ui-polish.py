from pathlib import Path
import re
p=Path("index.html")
s=p.read_text(encoding="utf-8")
css="""<style id="farshad-ui-polish">
.field label{display:inline-flex;align-items:center;margin:2px 0 4px;padding:6px 12px;border-right:3px solid var(--blue);border-radius:8px 5px 5px 8px;background:#168bff18;color:#e8f2ff;font-size:14px;font-weight:700}
body.light .field label{color:#17436d;background:#168bff12}
.service{display:flex;flex-direction:column;gap:10px;margin:12px 0;padding:13px!important;border:1px solid #29435f!important;border-radius:15px!important;background:linear-gradient(145deg,#101e30,#091321)!important;box-shadow:0 5px 14px #0003}
.service-title{display:flex;align-items:center;justify-content:flex-start;gap:8px;flex-wrap:wrap;padding-bottom:9px;border-bottom:1px solid #2a4058;font-weight:700}
.service-title .service-title-name{order:0;flex:1 1 100%;font-size:14px;line-height:1.8}
.service-title .service-date{order:1;margin-inline-start:0}
.service-title .ignored-tag{order:2;margin-inline-start:auto;padding:5px 9px;font-size:11px}
.service-info{gap:9px}
.service-mainline{padding:8px 10px;border:1px solid #263d56;border-radius:10px;background:#ffffff06}
.service-calc{display:flex;flex-wrap:wrap;gap:7px;white-space:normal}
.service-calc .num-chip{padding:6px 9px;border-radius:8px;background:#d9bd6114;border:1px solid #7e6a3155}
.service-description{padding:9px 11px;border-right:3px solid #537da7;border-radius:8px;background:#ffffff08}
.service-actions{padding-top:9px;margin-top:0;border-top:1px solid #263d56}
.service.ignored-service{border-color:#98702d!important;background:linear-gradient(145deg,#282315,#111827)!important}
body.light .service{background:linear-gradient(145deg,#fff,#f5f8fc)!important;border-color:#d3deea!important}
body.light .service-title{border-color:#dce5ef}
body.light .service-mainline{background:#f7faff;border-color:#dce5ef}
body.light .service.ignored-service{background:linear-gradient(145deg,#fff9e9,#fff)!important;border-color:#d4aa55!important}
@media(max-width:380px){.service{padding:10px!important}.service-calc .num-chip{font-size:11px;padding:5px 7px}}
</style>"""
if 'id="farshad-ui-polish"' in s:
 s=re.sub(r'<style id="farshad-ui-polish">.*?</style>',css,s,count=1,flags=re.S)
else:
 if "</head>" not in s: raise SystemExit("head missing")
 s=s.replace("</head>",css+"</head>",1)
theme="""(function setupTheme(){let mode='dark';try{mode=localStorage.getItem('water-accounting-theme')||'dark'}catch(e){}applyTheme(mode);const b=document.getElementById('themeToggle');if(b){b.onclick=()=>{const next=document.body.classList.contains('light')?'dark':'light';applyTheme(next);try{localStorage.setItem('water-accounting-theme',next)}catch(e){}}}})();"""
s,n=re.subn(r"\(function setupTheme\(\)\{.*?\}\)\(\);",lambda m:theme,s,count=1,flags=re.S)
if n!=1: raise SystemExit("setupTheme block not found uniquely: "+str(n))
for q in ['id="farshad-ui-polish"','.service-title .ignored-tag{order:2','b.onclick=']:
 if q not in s: raise SystemExit("validation failed: "+q)
p.write_text(s,encoding="utf-8")
print("UI polish applied and validated")
