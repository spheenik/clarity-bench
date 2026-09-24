#!/usr/bin/env python3
"""Cross-parser comparison: clarity vs demoparser2, demoinfocs-golang and manta.

Two measurements per target and replay:

  process  one parse per process, --rounds rounds interleaved across targets.
           Wall and CPU are taken around the parse inside the process; peak RSS
           and whole-process CPU (including JVM startup/JIT) come from /usr/bin/time.
  loop     --loop-iterations parses in a single process. The median of the
           iterations after --loop-warmup is the steady-state cost, i.e. what a
           long-lived batch worker pays per demo.
"""

import argparse
import datetime
import hashlib
import json
import os
import platform
import re
import shutil
import statistics
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
BUILD = os.path.join(HERE, "build")

TARGETS = [
    # id, parser, version, mode, engines
    ("clarity", "clarity", "5.0.0-SNAPSHOT", "ST", {"CS2", "DOTA_S2"}),
    ("dp2-0.42.0-mt", "demoparser2", "0.42.0", "MT", {"CS2"}),
    ("dp2-0.42.0-st", "demoparser2", "0.42.0", "ST", {"CS2"}),
    ("dp2-0.41.1-mt", "demoparser2", "0.41.1", "MT", {"CS2"}),
    ("dp2-0.41.1-st", "demoparser2", "0.41.1", "ST", {"CS2"}),
    ("dic-v5.2.0-default", "demoinfocs", "v5.2.0", "default", {"CS2"}),
    ("dic-v5.2.0-st", "demoinfocs", "v5.2.0", "ST", {"CS2"}),
    ("dic-v5.1.4-default", "demoinfocs", "v5.1.4", "default", {"CS2"}),
    ("dic-v5.1.4-st", "demoinfocs", "v5.1.4", "ST", {"CS2"}),
    ("manta-v1.5.0", "manta", "v1.5.0", "ST", {"DOTA_S2"}),
    ("manta-91f7979", "manta", "91f7979", "ST", {"DOTA_S2"}),
]

GO_MODULES = {
    "manta": "github.com/dotabuff/manta",
    "demoinfocs": "github.com/markus-wa/demoinfocs-golang/v5",
}

ITER_RE = re.compile(r"^ITER (\d+) wall=([0-9.]+) cpu=([0-9.]+)$", re.M)
TIME_RE = re.compile(r"^RES ([0-9.]+) ([0-9.]+) ([0-9.]+) ([0-9]+)$", re.M)


def sh(cmd, cwd=None, env=None):
    subprocess.run(cmd, cwd=cwd, env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def load_replays(root):
    replays = []
    for line in open(os.path.join(HERE, "replays.txt")):
        if not line.strip() or line.startswith("#"):
            continue
        rid, engine, sha, size, rel = line.split()
        path = os.path.join(root, rel)
        if os.path.getsize(path) != int(size):
            sys.exit(f"size mismatch for {rel}")
        h = hashlib.sha256()
        with open(path, "rb") as f:
            for block in iter(lambda: f.read(1 << 24), b""):
                h.update(block)
        if h.hexdigest() != sha:
            sys.exit(f"sha256 mismatch for {rel}")
        replays.append({"id": rid, "engine": engine, "path": path, "rel": rel})
    return replays


def build_clarity():
    sh(["./gradlew", "-q", ":parsers-clarity:installDist"], cwd=ROOT)
    return os.path.join(HERE, "clarity", "build", "install", "parsers-clarity", "lib", "*")


def build_go(parser, version):
    out = os.path.join(BUILD, f"{parser}-{version}")
    shutil.rmtree(out, ignore_errors=True)
    shutil.copytree(os.path.join(HERE, parser), out)
    sh(["go", "get", f"{GO_MODULES[parser]}@{version}"], cwd=out)
    sh(["go", "mod", "tidy"], cwd=out)
    sh(["go", "build", "-o", "bin", "."], cwd=out)
    resolved = subprocess.run(["go", "list", "-m", GO_MODULES[parser]], cwd=out, capture_output=True, text=True, check=True)
    return os.path.join(out, "bin"), resolved.stdout.split()[-1]


def build_demoparser2(version):
    venv = os.path.join(BUILD, f"demoparser2-{version}")
    if not os.path.exists(os.path.join(venv, "bin", "python")):
        sh([sys.executable, "-m", "venv", venv])
        sh([os.path.join(venv, "bin", "pip"), "install", "-q", f"demoparser2=={version}"])
    return os.path.join(venv, "bin", "python")


def build(targets):
    built, versions = {}, {}
    for tid, parser, version, mode, _ in targets:
        key = (parser, version)
        if key in built:
            continue
        print(f"building {parser} {version}", flush=True)
        if parser == "clarity":
            built[key] = build_clarity()
            versions[key] = version
        elif parser == "demoparser2":
            built[key] = build_demoparser2(version)
            versions[key] = version
        else:
            built[key], versions[key] = build_go(parser, version)
    return built, versions


def command(target, artifact, replay, iterations):
    tid, parser, version, mode, _ = target
    env = {}
    if parser == "clarity":
        cmd = ["java", "-Xmx4g", "-cp", artifact, "spheenik.claritybench.parsers.ClarityParse", replay, str(iterations)]
    elif parser == "demoparser2":
        cmd = [artifact, os.path.join(HERE, "demoparser2", "parse.py"), replay, str(iterations)]
        if mode == "ST":
            env["RAYON_NUM_THREADS"] = "1"
    elif parser == "demoinfocs":
        cmd = [artifact, replay, str(iterations)] + (["st"] if mode == "ST" else [])
    else:
        cmd = [artifact, replay, str(iterations)]
    return cmd, env


def execute(cmd, env):
    p = subprocess.run(["/usr/bin/time", "-f", "RES %e %U %S %M"] + cmd,
                       capture_output=True, text=True, env={**os.environ, **env})
    out = p.stdout + p.stderr
    iters = [{"wall": float(w), "cpu": float(c)} for _, w, c in ITER_RE.findall(out)]
    res = TIME_RE.search(out)
    if p.returncode != 0 or not iters or not res:
        raise RuntimeError(f"{' '.join(cmd)} failed:\n{out[-3000:]}")
    return {
        "iterations": iters,
        "process_wall": float(res.group(1)),
        "process_cpu": float(res.group(2)) + float(res.group(3)),
        "rss_mb": int(res.group(4)) / 1024,
    }


def summarize(results, replays, targets, versions, warmup):
    lines = ["| Replay | Parser | Version | Mode | Process median wall | Process CPU | Peak RSS | Steady wall | Steady CPU |",
             "|---|---|---|---|---:|---:|---:|---:|---:|"]
    for r in replays:
        for t in targets:
            key = f"{r['id']}|{t[0]}"
            if key not in results["process"]:
                continue
            proc = results["process"][key]
            loop = results["loop"].get(key)
            walls = [x["iterations"][0]["wall"] for x in proc]
            pcpu = [x["process_cpu"] for x in proc]
            rss = [x["rss_mb"] for x in proc]
            steady = loop["iterations"][warmup:] if loop else []
            sw = f"{statistics.median(i['wall'] for i in steady):.3f}s" if steady else "-"
            sc = f"{statistics.median(i['cpu'] for i in steady):.3f}s" if steady else "-"
            lines.append(f"| {r['id']} | {t[1]} | {versions[(t[1], t[2])]} | {t[3]} | {statistics.median(walls):.3f}s"
                         f" | {statistics.median(pcpu):.2f}s | {statistics.median(rss):.0f} MB | {sw} | {sc} |")
    return "\n".join(lines) + "\n"


def tool_version(cmd):
    try:
        p = subprocess.run(cmd, capture_output=True, text=True)
        return (p.stdout + p.stderr).strip().splitlines()[0]
    except OSError:
        return "n/a"


def host_slug():
    model = ""
    for line in open("/proc/cpuinfo"):
        if line.startswith("model name"):
            model = line.split(":", 1)[1]
            break
    m = re.search(r"Ryzen \d+ (\w+)|(i\d-\w+)|(\w+)\s+Processor", model)
    token = next((g for g in (m.groups() if m else ()) if g), platform.node())
    return re.sub(r"[^a-z0-9]", "", ("ryzen" if "Ryzen" in model else "") + token.lower())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--replays-root", required=True)
    ap.add_argument("--rounds", type=int, default=7)
    ap.add_argument("--loop-iterations", type=int, default=10)
    ap.add_argument("--loop-warmup", type=int, default=3)
    ap.add_argument("--only", action="append", default=[], help="restrict to target ids containing this; repeatable")
    ap.add_argument("--clarity-label", default="5.0.0-SNAPSHOT (mavenLocal)")
    ap.add_argument("--record", action="store_true", help="write into results/parsers/ instead of parsers/build/")
    args = ap.parse_args()

    targets = [t for t in TARGETS if not args.only or any(o in t[0] for o in args.only)]
    replays = load_replays(args.replays_root)
    built, versions = build(targets)
    versions[("clarity", "5.0.0-SNAPSHOT")] = args.clarity_label

    cells = [(r, t) for r in replays for t in targets if r["engine"] in t[4]]
    results = {"process": {}, "loop": {}}
    load_start = open("/proc/loadavg").read().split()[:3]

    for rnd in range(args.rounds):
        for r, t in cells:
            cmd, env = command(t, built[(t[1], t[2])], r["path"], 1)
            res = execute(cmd, env)
            results["process"].setdefault(f"{r['id']}|{t[0]}", []).append(res)
            print(f"process {rnd + 1}/{args.rounds} {r['id']:10} {t[0]:20} {res['iterations'][0]['wall']:.3f}s", flush=True)

    for r, t in cells:
        cmd, env = command(t, built[(t[1], t[2])], r["path"], args.loop_iterations)
        res = execute(cmd, env)
        results["loop"][f"{r['id']}|{t[0]}"] = res
        steady = res["iterations"][args.loop_warmup:]
        print(f"loop    {r['id']:10} {t[0]:20} steady wall={statistics.median(i['wall'] for i in steady):.3f}s"
              f" cpu={statistics.median(i['cpu'] for i in steady):.3f}s", flush=True)

    load_end = open("/proc/loadavg").read().split()[:3]
    date = datetime.date.today().isoformat()
    out_dir = os.path.join(ROOT, "results", "parsers", f"{date}_{host_slug()}") if args.record \
        else os.path.join(BUILD, "results", f"{date}_{host_slug()}")
    os.makedirs(out_dir, exist_ok=True)

    context = {
        "date": date,
        "host": host_slug(),
        "cpu": next(l.split(":", 1)[1].strip() for l in open("/proc/cpuinfo") if l.startswith("model name")),
        "kernel": platform.release(),
        "java": tool_version(["java", "-version"]),
        "go": tool_version(["go", "version"]),
        "python": platform.python_version(),
        "load_start": load_start,
        "load_end": load_end,
        "rounds": args.rounds,
        "loop_iterations": args.loop_iterations,
        "loop_warmup": args.loop_warmup,
        "versions": {f"{p} {v}": resolved for (p, v), resolved in versions.items()},
        "replays": {r["id"]: r["rel"] for r in replays},
    }
    json.dump({"context": context, "results": results}, open(os.path.join(out_dir, "results.json"), "w"), indent=1)
    summary = summarize(results, replays, targets, versions, args.loop_warmup)
    with open(os.path.join(out_dir, "summary.md"), "w") as f:
        f.write(f"# Parser comparison {date} ({context['cpu']})\n\n")
        for k in ("kernel", "java", "go", "python", "load_start", "load_end", "rounds", "loop_iterations", "loop_warmup"):
            f.write(f"- {k}: {context[k]}\n")
        f.write("\n" + summary)
    print("\n" + summary)
    print(f"written to {out_dir}")


if __name__ == "__main__":
    main()
