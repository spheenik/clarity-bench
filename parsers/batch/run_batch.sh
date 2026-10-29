#!/bin/bash
# Batch-in-one-process harness: N threads pull demos from a shared counter.
# usage: run_batch.sh [replays-root] [output-file]
#   env: THREADS="1 4 8"  ROUNDS=3  PER_THREAD=12  LOAD_GATE=4.0
#        REPLAYS="a.dem b.dem" (overrides the default five)
#        CONFIGS="manta par_8g par_4g par_2g g1_4g g1_8g serial_8g"
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PARSERS="$(dirname "$HERE")"
R="${1:-${REPLAYS_ROOT:-$HOME/projects/clarity/replays}}/dota/s2"
OUT="${2:-$PARSERS/build/batch_runs.txt}"
THREADS="${THREADS:-1 4 8}"
ROUNDS="${ROUNDS:-3}"
PER_THREAD="${PER_THREAD:-12}"
LOAD_GATE="${LOAD_GATE:-4.0}"
CONFIGS="${CONFIGS:-manta par_8g par_4g par_2g g1_4g g1_8g serial_8g}"

REPLAYS="${REPLAYS:-$R/normal/1560289528.dem $R/normal/1673659333.dem $R/normal/1716444111.dem $R/normal/1734886116.dem $R/340/8168882574_1198277651.dem}"
CP="$PARSERS/clarity/build/install/parsers-clarity/lib/*"
MANTA="$PARSERS/build/manta-batch"
BATCH=spheenik.claritybench.parsers.ClarityBatch

mkdir -p "$PARSERS/build" "$(dirname "$OUT")"
(cd "$HERE/manta" && go build -o "$MANTA" .)

declare -A C
C[manta]="$MANTA"
C[par_8g]="java -Xmx8g -XX:+UseParallelGC -cp $CP $BATCH"
C[par_4g]="java -Xmx4g -XX:+UseParallelGC -cp $CP $BATCH"
C[par_2g]="java -Xmx2g -XX:+UseParallelGC -cp $CP $BATCH"
C[g1_8g]="java -Xmx8g -XX:+UseG1GC -cp $CP $BATCH"
C[g1_4g]="java -Xmx4g -XX:+UseG1GC -cp $CP $BATCH"
C[serial_8g]="java -Xmx8g -XX:+UseSerialGC -cp $CP $BATCH"
read -ra NAMES <<< "$CONFIGS"
K=${#NAMES[@]}
set -f  # keep the classpath wildcard from being globbed when ${C[...]} is expanded

for round in $(seq 1 "$ROUNDS"); do
  for n in $THREADS; do
    for k in $(seq 0 $((K - 1))); do
      c=${NAMES[$(( (k + round) % K ))]}
      while awk -v g="$LOAD_GATE" '{exit !($1 >= g)}' /proc/loadavg; do sleep 5; done
      echo "round $round $c n=$n load=$(cut -d' ' -f1 /proc/loadavg)" >> "$OUT"
      # shellcheck disable=SC2086
      /usr/bin/time -f "rss_kb %M" ${C[$c]} "$n" "$PER_THREAD" $REPLAYS 2>&1 \
        | grep -E '^(batch|rss_kb|Exception|java.lang.OutOfMemory)' >> "$OUT" || true
    done
  done
done
echo ALLDONE >> "$OUT"
