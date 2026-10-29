package main

import (
	"fmt"
	"os"
	"strconv"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/dotabuff/manta"
)

func cpuSeconds() float64 {
	var ru syscall.Rusage
	if err := syscall.Getrusage(syscall.RUSAGE_SELF, &ru); err != nil {
		panic(err)
	}
	return float64(ru.Utime.Nano()+ru.Stime.Nano()) / 1e9
}

func parse(path string) {
	f, err := os.Open(path)
	if err != nil {
		panic(err)
	}
	defer f.Close()
	p, err := manta.NewStreamParser(f)
	if err != nil {
		panic(err)
	}
	if err := p.Start(); err != nil {
		panic(err)
	}
}

// args: <threads> <demosPerThread> <replay>...
func main() {
	threads, _ := strconv.Atoi(os.Args[1])
	perThread, _ := strconv.Atoi(os.Args[2])
	replays := os.Args[3:]

	var wg sync.WaitGroup
	for t := 0; t < threads; t++ {
		wg.Add(1)
		go func(off int) {
			defer wg.Done()
			for i := range replays {
				parse(replays[(i+off)%len(replays)])
			}
		}(t)
	}
	wg.Wait()

	total := int64(threads * perThread)
	var next int64 = -1
	c0 := cpuSeconds()
	t0 := time.Now()
	for t := 0; t < threads; t++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for {
				i := atomic.AddInt64(&next, 1)
				if i >= total {
					return
				}
				parse(replays[int(i)%len(replays)])
			}
		}()
	}
	wg.Wait()
	wall := time.Since(t0).Seconds()
	proc := cpuSeconds() - c0
	fmt.Printf("batch: threads %d demos %d wall %.2f s proc %.2f s demos/s %.2f cpu/demo %.3f s gc 0 ms in 0\n",
		threads, total, wall, proc, float64(total)/wall, proc/float64(total))
}
