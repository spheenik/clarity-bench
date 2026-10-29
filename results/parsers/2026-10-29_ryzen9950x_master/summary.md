# Parser comparison 2026-10-29 (AMD Ryzen 9 9950X 16-Core Processor)

- kernel: 7.2.7-arch1-1
- java: openjdk version "21.0.12.1" 2026-08-18
- go: go version go1.27.2-X:nodwarf5 linux/amd64
- python: 3.14.8
- load_start: ['2.23', '1.32', '2.04']
- load_end: ['2.31', '1.54', '2.06']
- rounds: 7
- loop_iterations: 10
- loop_warmup: 3

| Replay | Parser | Version | Mode | Process median wall | Process CPU | Peak RSS | Steady wall | Steady CPU |
|---|---|---|---|---:|---:|---:|---:|---:|
| cs2-anubis | clarity | master@3be39a5d | ST | 1.290s | 2.71s | 1137 MB | 1.035s | 1.120s |
| dota-2016 | clarity | master@3be39a5d | ST | 0.509s | 1.79s | 803 MB | 0.286s | 0.350s |
| dota-2016 | manta | v1.5.2 | ST | 0.259s | 0.29s | 34 MB | 0.253s | 0.280s |
| dota-2025 | clarity | master@3be39a5d | ST | 1.316s | 3.25s | 990 MB | 1.070s | 1.480s |
| dota-2025 | manta | v1.5.2 | ST | 0.897s | 1.05s | 58 MB | 0.885s | 1.042s |
