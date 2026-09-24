import resource
import sys
import time

from demoparser2 import DemoParser

PROPS = ["X", "Y", "Z", "health"]


def cpu_seconds():
    ru = resource.getrusage(resource.RUSAGE_SELF)
    return ru.ru_utime + ru.ru_stime


for i in range(1, int(sys.argv[2]) + 1):
    c0 = cpu_seconds()
    t0 = time.perf_counter()
    DemoParser(sys.argv[1]).parse_ticks(PROPS)
    print(f"ITER {i} wall={time.perf_counter() - t0:.4f} cpu={cpu_seconds() - c0:.4f}", flush=True)
