# Batch in one process: Ryzen 9 9950X, 2026-10-29

- Host: Ryzen 9 9950X (16c/32t), OpenJDK 21.0.12, go1.27.2
- clarity: master@3be39a5d (5.0.2-SNAPSHOT, local build). Run via clarity-examples
  `dev:entityrun` (`Main().run`), not the `ClarityBatch` driver in this repo; the
  workload is the same (SimpleRunner, `@UsesEntities`, no listeners).
- manta: v1.5.2
- Replays: dota/s2/normal/{1560289528,1673659333,1716444111,1734886116}.dem and
  dota/s2/340/8168882574_1198277651.dem
- 12 demos per thread, warmup = one pass over all replays per thread, n=3 rounds
  (interleaved), load gate 4.0 (observed 1.3-4.0)
- Raw lines: `mt_runs.txt` (G1/Parallel/Serial at -Xmx8g; configs `g1`, `par`, `serial`),
  `mt2_runs.txt` (manta vs clarity heap sizes; `par_8g`, `par_4g`, `par_2g`, `g1_4g`)
- Values are medians over the rounds. cpu/demo is process CPU (incl. JIT and GC threads).
  The manta line reports gc 0 (no GC counters).

| config | N | runs | demos/s | cpu/demo (s) | gc (ms) | gc count | RSS (MB) |
|---|---:|---:|---:|---:|---:|---:|---:|
| par | 1 | 3 | 2.59 | 0.457 | 35 | 8 | 2562 |
| serial | 1 | 3 | 2.50 | 0.412 | 279 | 51 | 804 |
| g1 | 1 | 3 | 2.58 | 0.487 | 54 | 18 | 1512 |
| par_8g | 1 | 3 | 2.60 | 0.448 | 34 | 8 | 2571 |
| par_4g | 1 | 3 | 2.63 | 0.452 | 39 | 13 | 1690 |
| par_2g | 1 | 3 | 2.77 | 0.474 | 65 | 22 | 1212 |
| g1_4g | 1 | 3 | 2.55 | 0.556 | 93 | 25 | 1180 |
| manta | 1 | 3 | 3.02 | 0.371 | 0 | 0 | 64 |
| par | 4 | 3 | 8.40 | 0.506 | 148 | 23 | 4224 |
| serial | 4 | 3 | 6.60 | 0.468 | 1770 | 219 | 1478 |
| g1 | 4 | 3 | 8.10 | 0.567 | 272 | 35 | 3559 |
| par_8g | 4 | 3 | 8.25 | 0.513 | 148 | 23 | 4211 |
| par_4g | 4 | 3 | 8.39 | 0.520 | 209 | 45 | 3416 |
| par_2g | 4 | 3 | 8.59 | 0.516 | 285 | 91 | 2499 |
| g1_4g | 4 | 3 | 8.05 | 0.574 | 296 | 68 | 2245 |
| manta | 4 | 3 | 9.95 | 0.424 | 0 | 0 | 147 |
| par | 8 | 3 | 14.13 | 0.568 | 442 | 47 | 6693 |
| serial | 8 | 3 | 9.09 | 0.524 | 4134 | 448 | 1989 |
| g1 | 8 | 3 | 13.54 | 0.632 | 582 | 49 | 6255 |
| par_8g | 8 | 3 | 14.30 | 0.565 | 442 | 47 | 6739 |
| par_4g | 8 | 3 | 13.94 | 0.572 | 520 | 92 | 4811 |
| par_2g | 8 | 3 | 14.51 | 0.565 | 570 | 185 | 2843 |
| g1_4g | 8 | 3 | 13.45 | 0.633 | 603 | 80 | 3974 |
| manta | 8 | 3 | 17.81 | 0.462 | 0 | 0 | 247 |
