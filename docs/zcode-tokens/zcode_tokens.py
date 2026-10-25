# -*- coding: utf-8 -*-
"""Extract ZCode light/dark design tokens from the compiled Tailwind CSS."""
import io
import json
import re

CSS = r"C:\Users\yang\AppData\Local\Temp\opencode\zcode-extract\out\renderer\assets\styles-C8Nayk5k.css"

def grab(prefix, s):
    res = {}
    for m in re.finditer(prefix + r'\s*\{(.*?)\}', s, re.S):
        body = m.group(1)
        for v in re.finditer(r'--([a-zA-Z0-9_-]+)\s*:\s*([^;]+);', body):
            res[v.group(1)] = v.group(2).strip()
    return res

def main():
    s = io.open(CSS, encoding='utf-8', errors='replace').read()
    light = grab(r':root,:host', s)
    dark = grab(r'\.dark', s)
    print('LIGHT_KEYS', len(light), 'DARK_KEYS', len(dark))
    changed = {}
    for k in sorted(dark):
        lv = light.get(k, '-')
        dv = dark.get(k, '-')
        if dv != lv:
            changed[k] = (lv, dv)
    print('CHANGED', len(changed))
    for k, (lv, dv) in changed.items():
        print('{} :: light={} | dark={}'.format(k, lv, dv))

if __name__ == '__main__':
    main()
