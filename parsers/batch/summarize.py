#!/usr/bin/env python3
"""Summarize run_batch.sh output: median per config and thread count.

usage: summarize.py runs.txt [runs2.txt ...]
"""
import collections
import re
import statistics as st
import sys

d = collections.defaultdict(lambda: collections.defaultdict(list))
order = []
cur = None
for path in sys.argv[1:]:
    for line in open(path):
        m = re.match(r'round \d+ (\S+) n=(\d+)', line)
        if m:
            cur = (m.group(1), int(m.group(2)))
            if m.group(1) not in order:
                order.append(m.group(1))
            continue
        m = re.search(r'wall ([\d.]+) s proc ([\d.]+) s demos/s ([\d.]+) cpu/demo ([\d.]+) s gc (\d+) ms in (\d+)', line)
        if m and cur:
            for k, v in zip(['wall', 'proc', 'dps', 'cpd', 'gcms', 'gcn'], m.groups()):
                d[cur][k].append(float(v))
            continue
        m = re.match(r'rss_kb (\d+)', line)
        if m and cur:
            d[cur]['rss'].append(int(m.group(1)) / 1024)

print("| config | N | runs | demos/s | cpu/demo (s) | gc (ms) | gc count | RSS (MB) |")
print("|---|---:|---:|---:|---:|---:|---:|---:|")
for n in sorted({k[1] for k in d}):
    for c in order:
        x = d[(c, n)]
        if not x['dps']:
            continue
        rss = f"{st.median(x['rss']):.0f}" if x['rss'] else "-"
        print(f"| {c} | {n} | {len(x['dps'])} | {st.median(x['dps']):.2f} | {st.median(x['cpd']):.3f} "
              f"| {st.median(x['gcms']):.0f} | {st.median(x['gcn']):.0f} | {rss} |")
