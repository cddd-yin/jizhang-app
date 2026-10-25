# -*- coding: utf-8 -*-
"""Extract Tailwind palette from ZCode CSS and convert oklch -> hex sRGB."""
import io
import math
import re

CSS = r"C:\Users\yang\AppData\Local\Temp\opencode\zcode-extract\out\renderer\assets\styles-C8Nayk5k.css"

def oklch_to_srgb(L, C, h):
    """Convert oklch to sRGB. L,C in [0,1], h in degrees."""
    h_rad = math.radians(h)
    a = C * math.cos(h_rad)
    b = C * math.sin(h_rad)
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l = l_ ** 3
    m = m_ ** 3
    s = s_ ** 3
    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    def clamp(x):
        x = max(0.0, min(1.0, x))
        return x
    # apply linear->gamma (sRGB, approx)
    def to_srgb(c):
        c = clamp(c)
        if c <= 0.0031308:
            return 12.92 * c
        return 1.055 * (c ** (1 / 2.4)) - 0.055
    r8 = round(to_srgb(r) * 255)
    g8 = round(to_srgb(g) * 255)
    b8 = round(to_srgb(bb) * 255)
    return '#{:02X}{:02X}{:02X}'.format(r8, g8, b8)

def main():
    s = io.open(CSS, encoding='utf-8', errors='replace').read()
    # find oklch definitions like --color-sky-400:oklch(74.6% .16 232.661);
    pat = re.compile(r'--color-([a-z0-9-]+):oklch\(([\d.]+)%\s+([\d.]+)\s+([\d.]+)\);')
    colors = {}
    for m in pat.finditer(s):
        name, L, C, h = m.group(1), float(m.group(2)) / 100.0, float(m.group(3)), float(m.group(4))
        colors[name] = oklch_to_srgb(L, C, h)
    wanted = [
        'neutral-50', 'neutral-100', 'neutral-200', 'neutral-300', 'neutral-500',
        'neutral-700', 'neutral-800', 'neutral-900', 'neutral-950',
        'sky-200', 'sky-300', 'sky-400', 'sky-500', 'sky-600', 'sky-700', 'sky-950',
        'green-300', 'green-400', 'green-500', 'green-600', 'green-700',
        'red-400', 'red-500', 'red-600',
        'yellow-300', 'yellow-400', 'yellow-500', 'yellow-600',
        'teal-300', 'teal-400', 'teal-600',
        'violet-300', 'violet-400', 'violet-500', 'violet-600',
        'amber-300', 'amber-400', 'amber-600',
        'rose-400', 'rose-500', 'rose-600',
        'indigo-400', 'indigo-500',
        'cyan-400', 'cyan-500', 'cyan-600',
        'fuchsia-500', 'fuchsia-600',
        'slate-300', 'slate-600',
        'zinc-400', 'gray-400',
    ]
    for w in wanted:
        print('{} = {}'.format(w, colors.get(w, '?')))
    # dump full map for reference
    with io.open(r'C:\Users\yang\AppData\Local\Temp\opencode\zcode-extract\palette.json', 'w', encoding='utf-8') as f:
        import json
        json.dump(colors, f, ensure_ascii=False, indent=1)

if __name__ == '__main__':
    main()
