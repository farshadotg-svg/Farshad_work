# Build pipeline patch: keep the main app completely out of the layout until welcome/login succeeds.
from pathlib import Path

path = Path("index.html")
html = path.read_text(encoding="utf-8")

style = '<style id="welcome-app-layer-fix">body:not(.app-entered) .app{display:none!important;visibility:hidden!important;pointer-events:none!important}body.app-entered .app{display:block!important;visibility:visible!important;pointer-events:auto!important}</style>'
if 'id="welcome-app-layer-fix"' in html:
    import re
    html = re.sub(r'<style id="welcome-app-layer-fix">.*?</style>', style, html, count=1, flags=re.S)
else:
    if "</head>" not in html:
        raise SystemExit("Cannot install welcome/app layer fix: </head> not found.")
    html = html.replace("</head>", style + "</head>", 1)

old_enter = "window.enterFarshadApp=function(){const welcome=$s('appWelcomeScreen');if(saved()){mode='unlock';show('الگوی ورود','')}else if(welcome)welcome.classList.add('welcome-hidden')};"
new_enter = "window.enterFarshadApp=function(){const welcome=$s('appWelcomeScreen');if(saved()){mode='unlock';show('الگوی ورود','')}else{document.body.classList.add('app-entered');if(welcome)welcome.classList.add('welcome-hidden')}};"
if old_enter in html:
    html = html.replace(old_enter, new_enter, 1)
elif new_enter not in html:
    raise SystemExit(f"Expected welcome entry handler; old version count={html.count('window.enterFarshadApp=')}.")

old_unlock = "if(hashPattern(value)===saved()){hide();const welcome=$s('appWelcomeScreen');if(welcome)welcome.classList.add('welcome-hidden');return}"
new_unlock = "if(hashPattern(value)===saved()){hide();document.body.classList.add('app-entered');const welcome=$s('appWelcomeScreen');if(welcome)welcome.classList.add('welcome-hidden');return}"
if old_unlock in html:
    html = html.replace(old_unlock, new_unlock, 1)
elif new_unlock not in html:
    raise SystemExit("Expected successful pattern-unlock handler.")

old_bio = "window.onBiometricAuthSuccess=function(){hide();const welcome=$s('appWelcomeScreen');if(welcome)welcome.classList.add('welcome-hidden')};"
new_bio = "window.onBiometricAuthSuccess=function(){hide();document.body.classList.add('app-entered');const welcome=$s('appWelcomeScreen');if(welcome)welcome.classList.add('welcome-hidden')};"
if old_bio in html:
    html = html.replace(old_bio, new_bio, 1)
elif new_bio not in html:
    raise SystemExit("Expected biometric-success handler.")

if 'id="welcome-app-layer-fix"' not in html or new_enter not in html or new_unlock not in html or new_bio not in html:
    raise SystemExit("Welcome/app layer fix validation failed.")


# Prevent even a first-frame flash before the head stylesheet is painted.
app_open = '<div class="app">'
app_hidden = '<div class="app" style="display:none">'
if html.count(app_open) == 1:
    html = html.replace(app_open, app_hidden, 1)
elif html.count(app_hidden) != 1:
    raise SystemExit("Expected one main app wrapper for startup hiding.")
if "body.app-entered .app{display:block!important" not in html:
    raise SystemExit("Welcome/app visibility rule is missing.")
path.write_text(html, encoding="utf-8")
print("Welcome/app layer isolation installed and validated.")
