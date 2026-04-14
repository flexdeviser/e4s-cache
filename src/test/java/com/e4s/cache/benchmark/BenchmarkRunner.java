package com.e4s.cache.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class BenchmarkRunner {
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AttributeDefBenchmark.class.getSimpleName())
                .include(TimeChunkBenchmark.class.getSimpleName())
                .include(ChunkManagerBenchmark.class.getSimpleName())
                .include(CacheSystemBenchmark.class.getSimpleName())
                .build();
        
        new Runner(opt).run();
    }
}