# Parser comparison 2026-10-29 (AMD Ryzen 9 9950X 16-Core Processor)

- kernel: 7.2.7-arch1-1
- java: openjdk version "21.0.12.1" 2026-08-18
- go: go version go1.27.2-X:nodwarf5 linux/amd64
- python: 3.14.8
- load_start: ['1.43', '1.24', '2.70']
- load_end: ['2.31', '1.74', '2.57']
- rounds: 7
- loop_iterations: 10
- loop_warmup: 3

| Replay | Parser | Version | Mode | Process median wall | Process CPU | Peak RSS | Steady wall | Steady CPU |
|---|---|---|---|---:|---:|---:|---:|---:|
| cs2-anubis | clarity | 5.0.0 | ST | 1.375s | 2.75s | 1136 MB | 1.114s | 1.180s |
| dota-2016 | clarity | 5.0.0 | ST | 0.553s | 1.88s | 803 MB | 0.326s | 0.400s |
| dota-2016 | manta | v1.5.2 | ST | 0.256s | 0.29s | 35 MB | 0.260s | 0.293s |
| dota-2016 | manta | v1.5.1 | ST | 0.379s | 0.43s | 38 MB | 0.388s | 0.452s |
| dota-2016 | manta | v1.5.0 | ST | 0.497s | 0.63s | 38 MB | 0.491s | 0.612s |
| dota-2016 | manta | v1.4.8-0.20260324190535-91f79795c479 | ST | 1.139s | 1.52s | 35 MB | 1.135s | 1.486s |
| dota-2025 | clarity | 5.0.0 | ST | 1.401s | 3.40s | 988 MB | 1.163s | 1.600s |
| dota-2025 | manta | v1.5.2 | ST | 0.874s | 1.00s | 56 MB | 0.889s | 1.045s |
| dota-2025 | manta | v1.5.1 | ST | 1.349s | 1.55s | 69 MB | 1.369s | 1.613s |
| dota-2025 | manta | v1.5.0 | ST | 1.823s | 2.44s | 76 MB | 1.827s | 2.446s |
| dota-2025 | manta | v1.4.8-0.20260324190535-91f79795c479 | ST | 3.852s | 5.53s | 64 MB | 3.857s | 5.588s |
