package main

import (
	"fmt"
	"os"
	"strconv"
	"syscall"
	"time"

	demoinfocs "github.com/markus-wa/demoinfocs-golang/v5/pkg/demoinfocs"
)

func cpuSeconds() float64 {
	var ru syscall.Rusage
	if err := syscall.Getrusage(syscall.RUSAGE_SELF, &ru); err != nil {
		panic(err)
	}
	return float64(ru.Utime.Nano()+ru.Stime.Nano()) / 1e9
}

func main() {
	iterations, err := strconv.Atoi(os.Args[2])
	if err != nil {
		panic(err)
	}
	cfg := demoinfocs.DefaultParserConfig
	if len(os.Args) > 3 && os.Args[3] == "st" {
		cfg.MsgQueueBufferSize = 0
	}
	for i := 1; i <= iterations; i++ {
		c0 := cpuSeconds()
		t0 := time.Now()
		f, err := os.Open(os.Args[1])
		if err != nil {
			panic(err)
		}
		p := demoinfocs.NewParserWithConfig(f, cfg)
		if err := p.ParseToEnd(); err != nil {
			panic(err)
		}
		p.Close()
		f.Close()
		fmt.Printf("ITER %d wall=%.4f cpu=%.4f\n", i, time.Since(t0).Seconds(), cpuSeconds()-c0)
	}
}
