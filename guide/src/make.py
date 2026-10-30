# Wraps each page's body (src/pages/<name>.html) in the shared head, navigation and footer: python3 guide/src/make.py
import os, re
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..') + '/'
PAGES = [('index', 'Home', 'den-tv', 'A home screen for Android TV and Google TV made for football, cricket and anime fans: live scores, next episodes, looks you design by describing them, and a phone remote.'),
         ('setup', 'Setup', 'den-tv Setup Guide', 'Put den-tv on an Android TV or Google TV in about ten minutes, with just the remote.'),
         ('looks', 'Looks', 'den-tv Looks', 'Every den-tv look: wallpapers, watchers that follow the app you pick, clocks, icon styles, layouts, wake animations and big moments.'),
         ('fanhub', 'Fan Hub', 'den-tv Fan Hub', 'Your teams\' matches and live scores, next episodes, trail weather and widgets, on the TV.'),
         ('phone', 'Phone app', 'den-tv Phone App', 'Everything the den-tv phone page does: looks, tweaks, widgets, people, the remote and the TV\'s health.'),
         ('keys', 'Keys', 'den-tv Free Keys', 'How to get the free Gemini, football-data.org and CricketData.org keys, and what each one adds.'),
         ('help', 'Help', 'den-tv Help', 'Updates, undo, privacy and fixes for common problems.'),
         ('credits', 'Credits', 'den-tv Credits', 'den-tv is built on cleanyourtv. The projects, data sources and fonts it uses.')]
HEAD = '''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>{title}</title>
<meta name="claude:viewport-fit-sides" content="cover">
<meta name="description" content="{desc}">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;800&family=Atkinson+Hyperlegible:ital,wght@0,400;0,700;1,400&family=JetBrains+Mono:wght@500;700&display=swap">
<link rel="stylesheet" href="site.css">
</head>
<body>
<header class="site"><div class="wrap">
  <a class="brand" href="index.html">den<span>·</span>tv</a>
  <nav aria-label="Pages"><ul>
{nav}
  </ul></nav>
</div></header>
'''
FOOT = '''<div class="wrap">
  <footer>
    <p>den-tv is free and open source, built on <a href="https://github.com/Gathin23/cleanyourtv">cleanyourtv</a> by Gathin (MIT). <a href="credits.html">Credits</a> · <a href="https://github.com/knightfox1510/den-tv">Code and releases on GitHub</a></p>
    <p>Not made by or connected to Google, any TV maker, club, league, broadcaster or studio. Players' and characters' names label fan tributes drawn in code; den-tv ships no photos or official artwork.</p>
  </footer>
</div>
{scripts}</body>
</html>
'''
for name, label, title, desc in PAGES:
    body = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'pages', name) + '.html').read()
    nav = '\n'.join('    <li><a href="{0}.html"{1}>{2}</a></li>'.format(n, ' aria-current="page"' if n == name else '', l) for n, l, _, _ in PAGES)
    scripts = '<script src="setup.js"></script>\n' if name == 'setup' else ''
    open(OUT + name + '.html', 'w').write(HEAD.format(title=title, desc=desc.replace('"', '&quot;'), nav=nav) + body + FOOT.format(scripts=scripts))
print('ok')
