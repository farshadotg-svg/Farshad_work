from pathlib import Path

path = Path("index.html")
html = path.read_text(encoding="utf-8")

# Keep the application DOM hidden until the welcome/login flow succeeds.
style = '<style id="welcome-app-layer-fix">body:not(.app-entered) .app{visibility:hidden!important;pointer-events:none!important}body.app-entered .app{visibility:visible!important;pointer-events:auto!important}</style>'
if 'id="welcome-app-layer-fix"' not in html:
    if "</body>" not in html:
        raise SystemExit("Cannot install welcome/app layer fix: </body> not found.")
    html = html.replace("</body>", style + "\\n</body>", 1)

old_enter = "window.enterFarshadApp=function(){const welcome=$s('appWelcomeScreen');if(saved()){mode='unlock';show('الگوی ورود','')}else if(welcome)welcome.classList.add('welcome-hidden')};"
new_enter = "window.enterFarshadApp=function(){const welcome=$s('appWelcomeScreen');if(saved()){mode='unlock';show('الگوی ورود','')}else{document.body.classList.add('app-entered');if(welcome)welcome.classList.add('welcome-hidden')}};"
if html.count(old_enter) != 1 and new_enter not in html:
    raise SystemExit(f"Expected one welcome entry handler, found {html.count(old_enter)}.")
if old_enter in html:
    html = html.replace(old_enter, new_enter, 1)

old_unlock = "if(hashPattern(value)===saved()){hide();const welcome=$s('appWelcomeScreen');if(welcome)welcome.classList.add('welcome-hidden');return}"
new_unlock = "if(hashPattern(value)===saved()){hide();document.body.classList.add('app-entered');const welcome=$s('appWelcomeScreen');if(welcome)welcome.classList.add('welcome-hidden');return}"
if html.count(old_unlock) != 1 and new_unlock not in html:
    raise SystemExit(f"Expected one successful pattern unlock handler, found {html.count(old_unlock)}.")
if old_unlock in html:
    html = html.replace(old_unlock, new_unlock, 1)

old_bio = "window.onBiometricAuthSuccess=function(){hide();const welcome=$s('appWelcomeScreen');if(welcome)welcome.classList.add('welcome-hidden')};"
new_bio = "window.onBiometricAuthSuccess=function(){hide();document.body.classList.add('app-entered');const welcome=$s('appWelcomeScreen');if(welcome)welcome.classList.add('welcome-hidden')};"
if old_bio in html:
    html = html.replace(old_bio, new_bio, 1)

if 'id="welcome-app-layer-fix"' not in html or new_enter not in html or new_unlock not in html:
    raise SystemExit("Welcome/app layer fix validation failed.")

path.write_text(html, encoding="utf-8")
