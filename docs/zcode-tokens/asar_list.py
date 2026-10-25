# -*- coding: utf-8 -*-
"""Parse Electron app.asar robustly: locate JSON header, list files, extract matching ones."""
import json
import os
import sys

ASAR = r"C:\Users\yang\AppData\Local\Programs\ZCode\resources\app.asar"
OUT = r"C:\Users\yang\AppData\Local\Temp\opencode\zcode-extract"

def find_json_end(data, start):
    """Find the end index of the JSON object starting at `start` in bytes,
    scanning braces while respecting string literals and escapes."""
    i = start
    depth = 0
    in_str = False
    n = len(data)
    while i < n:
        c = data[i]
        if in_str:
            if c == 0x5C:  # backslash
                i += 2
                continue
            if c == 0x22:  # quote
                in_str = False
            i += 1
            continue
        if c == 0x22:  # quote
            in_str = True
        elif c == 0x7B:  # {
            depth += 1
        elif c == 0x7D:  # }
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise RuntimeError('JSON end not found')

def parse_asar(path):
    with open(path, 'rb') as f:
        data = f.read()
    marker = b'{"files":{'
    idx = data.find(marker)
    if idx < 0:
        raise RuntimeError('header marker not found')
    end = find_json_end(data, idx)
    header = json.loads(data[idx:end].decode('utf-8'))
    return header, idx, end

def walk(node, prefix=''):
    out = []
    for name, info in node.get('files', {}).items():
        p = prefix + '/' + name
        if 'files' in info:
            out.extend(walk(info, p))
        else:
            out.append((p, int(info.get('offset', 0)), int(info.get('size', 0))))
    return out

def main():
    header, idx, end = parse_asar(ASAR)
    with open(ASAR, 'rb') as f:
        raw = f.read()
    # header JSON spans bytes [idx, idx+end). file data starts after that.
    # offsets in header are relative to base = end of header pickle.
    # Try base = idx + end + 4 (uint32 pad) and idx + end; decide by extracting first file.
    files = walk(header)
    print(f"TOTAL_FILES {len(files)}")
    first = sorted(files, key=lambda x: -x[2])[:3]
    for base_cand in (idx + end, idx + end + 4, idx + end + 8):
        ok = True
        for p, off, size in first:
            s = off + base_cand
            if s + size > len(raw):
                ok = False
                break
        print(f"base_candidate {base_cand} in_range={ok}")
    # pick base by text-ratio check on the largest file
    big = sorted(files, key=lambda x: -x[2])[0]
    best = None
    best_ratio = 0
    for base_cand in (idx + end, idx + end + 4, idx + end + 8, idx + end + 12):
        data = raw[base_cand + big[1]: base_cand + big[1] + min(big[2], 4096)]
        if not data:
            continue
        printable = sum(1 for b in data if 9 <= b <= 13 or 32 <= b <= 126)
        ratio = printable / len(data)
        if ratio > best_ratio:
            best_ratio = ratio
            best = base_cand
    print(f"USING_BASE {best} ratio={best_ratio:.3f}")
    os.makedirs(OUT, exist_ok=True)
    with open(ASAR, 'rb') as f:
        raw = f.read()
    interesting = []
    for p, off, size in files:
        low = p.lower()
        if any(k in low for k in ('dist/assets/', '.css', 'renderer', 'index.', 'theme')):
            interesting.append((p, off, size))
    for p, off, size in interesting:
        data = raw[best + off: best + off + size]
        rel = p.lstrip('/')
        dest = os.path.join(OUT, rel)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, 'wb') as f:
            f.write(data)
        print(f"EXTRACTED {p} size={size}")

if __name__ == '__main__':
    main()
