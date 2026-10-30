#!/usr/bin/env python3
"""Turns cleanyourtv's tested per-TV debloat lists into the Fan Hub's speed-up data.

cleanyourtv (https://github.com/Gathin23/cleanyourtv, MIT) keeps one folder per TV model in examples/, each with a
debloat.txt of packages its owner switched off and checked (a reboot, the inputs, the remote). The Fan Hub matches the
TV it runs on to one of them (Speedup.java) and offers that list. This writes, for each folder:

  folder, tv (its name), brand, models (ro.product.model strings named in the list), devices (codenames: the words
  in brackets in its name, and "device X" in the list), and entries [package, note].

Usage: python3 launcher/tools/cleanyourtv/import.py <cleanyourtv checkout>
Writes launcher/fanhub/src/main/assets/cleanyourtv.json; commit it with the checkout's commit in the message.
"""
import json
import pathlib
import re
import subprocess
import sys

PKG = re.compile(r'^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$')
# Words in a TV's name that say nothing about which TV it is.
NOISE = {'mediatek', 'realtek', 'amlogic', 'smart', 'pro', 'board', 'vision', 'android', 'device', 'google', 'tv',
         'the', 'and', 'with', 'max', 'mini', 'plus', 'lite', 'ultra', 'series', 'projector', 'stick', 'box', 'gen',
         'india', 'china', 'brazil', 'germany', 'spain', 'turkey', 'colombia', 'argentina', 'mexico', 'indonesia',
         'russia', 'italy', 'france', 'poland', 'vietnam', 'portugal', 'netherlands', 'ukraine', 'chile', 'peru',
         'qled', 'uhd', 'oled', 'led', 'lcd', 'built', 'skyworthdigital', 'changhong', 'cvte', 'sdmc', 'mtc', 'has',
         'for', 'from', 'one', 'two', 'new', 'old', 'first', 'second', 'unit', 'set', 'sets'}


def entries(path):
    out = []
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        line = line.rstrip('\r')
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        pkg, _, note = line.partition('#')
        pkg = pkg.split()[0] if pkg.split() else ''
        if PKG.match(pkg):
            out.append([pkg, note.strip()])
    return out


def main():
    root = pathlib.Path(sys.argv[1])
    lists = []
    for folder in sorted((root / 'examples').iterdir()):
        meta, deb = folder / 'tv.json', folder / 'debloat.txt'
        if not meta.is_file() or not deb.is_file():
            continue
        d = json.loads(meta.read_text(encoding='utf-8'))
        text = deb.read_text(encoding='utf-8-sig')
        header = '\n'.join(l for l in text.splitlines() if l.startswith('#'))
        models = sorted(set(m.strip() for m in re.findall(r'ro\.product\.model "([^"]+)"', header)))
        devices = set(re.findall(r'ro\.product\.device[ :"=]+([a-z0-9_]{3,})', header.lower()))
        devices |= set(re.findall(r'\bdevice ([a-z0-9_]{3,})[,)]', header.lower()))
        for paren in re.findall(r'\(([^)]*)\)', d['tv']):
            for word in re.split(r'[,;/]\s*|\s+', paren):
                w = word.strip().lower()
                if re.fullmatch(r'[a-z][a-z0-9_]{2,}', w) and w not in NOISE:
                    devices.add(w)
        e = entries(deb)
        if e:
            lists.append({'folder': folder.name, 'tv': d['tv'], 'brand': d.get('brand', ''), 'models': models,
                          'devices': sorted(devices), 'entries': e})
    commit = subprocess.run(['git', '-C', str(root), 'rev-parse', '--short', 'HEAD'], capture_output=True, text=True).stdout.strip()
    out = pathlib.Path(__file__).resolve().parents[2] / 'fanhub/src/main/assets/cleanyourtv.json'
    out.write_text(json.dumps({'source': 'https://github.com/Gathin23/cleanyourtv', 'commit': commit, 'license': 'MIT',
                               'lists': lists}, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    print(f'{len(lists)} TVs from cleanyourtv {commit} -> {out} ({out.stat().st_size // 1024} KB)')


if __name__ == '__main__':
    main()
