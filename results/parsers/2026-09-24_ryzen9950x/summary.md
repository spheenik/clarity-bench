# Parser comparison 2026-09-24 (AMD Ryzen 9 9950X 16-Core Processor)

- kernel: 7.2.4-arch1-2
- java: openjdk version "21.0.12.1" 2026-08-18
- go: go version go1.27.0-X:nodwarf5 linux/amd64
- python: 3.14.7
- load_start: ['1.08', '1.01', '1.99']
- load_end: ['1.52', '1.53', '1.86']
- rounds: 7
- loop_iterations: 10
- loop_warmup: 3

| Replay | Parser | Version | Mode | Process median wall | Process CPU | Peak RSS | Steady wall | Steady CPU |
|---|---|---|---|---:|---:|---:|---:|---:|
| cs2-anubis | clarity | next@c9d00c17 | ST | 1.437s | 2.87s | 1137 MB | 1.170s | 1.290s |
| cs2-anubis | demoparser2 | 0.42.0 | MT | 0.449s | 4.61s | 1156 MB | 0.249s | 2.411s |
| cs2-anubis | demoparser2 | 0.42.0 | ST | 1.670s | 3.65s | 1089 MB | 1.442s | 1.446s |
| cs2-anubis | demoparser2 | 0.41.1 | MT | 0.519s | 4.84s | 1216 MB | 0.304s | 2.631s |
| cs2-anubis | demoparser2 | 0.41.1 | ST | 1.814s | 3.79s | 1184 MB | 1.569s | 1.573s |
| cs2-anubis | demoinfocs | v5.2.0 | default | 2.220s | 6.00s | 57 MB | 2.220s | 6.116s |
| cs2-anubis | demoinfocs | v5.2.0 | ST | 4.319s | 6.44s | 54 MB | 4.305s | 6.419s |
| cs2-anubis | demoinfocs | v5.1.4 | default | 2.547s | 7.24s | 111 MB | 2.512s | 7.364s |
| cs2-anubis | demoinfocs | v5.1.4 | ST | 5.332s | 7.80s | 43 MB | 5.384s | 7.933s |
| dota-2016 | clarity | next@c9d00c17 | ST | 0.577s | 1.98s | 806 MB | 0.340s | 0.440s |
| dota-2016 | manta | v1.5.0 | ST | 0.509s | 0.65s | 43 MB | 0.531s | 0.698s |
| dota-2016 | manta | v1.4.8-0.20260324190535-91f79795c479 | ST | 1.171s | 1.57s | 40 MB | 1.164s | 1.558s |
| dota-2025 | clarity | next@c9d00c17 | ST | 1.474s | 3.60s | 995 MB | 1.214s | 1.780s |
| dota-2025 | manta | v1.5.0 | ST | 1.844s | 2.48s | 80 MB | 1.860s | 2.522s |
| dota-2025 | manta | v1.4.8-0.20260324190535-91f79795c479 | ST | 3.903s | 5.65s | 69 MB | 3.958s | 5.797s |
