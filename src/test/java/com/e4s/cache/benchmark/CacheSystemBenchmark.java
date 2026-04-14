package com.e4s.cache.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class CacheSystemBenchmark {
    
    private com.e4s.cache.model.ChunkManager chunkManager;
    private com.e4s.cache.model.TimeChunk timeChunk;
    private com.e4s.cache.model.AttributeDef attributeDef;
    private byte[] testData;
    private static final int MAX_CHUNKS = 100;
    private static final int CHUNK_INTERVAL_HOURS = 24;
    private static final long MAX_MEMORY_BYTES = 1024L * 1024 * 1024; // 1GB
    
    @Setup
    public void setup() {
        chunkManager = new com.e4s.cache.model.ChunkManager(MAX_CHUNKS, CHUNK_INTERVAL_HOURS, MAX_MEMORY_BYTES);
        timeChunk = new com.e4s.cache.model.TimeChunk(1, 1000, System.currentTimeMillis());
        attributeDef = new com.e4s.cache.model.AttributeDef("voltage", 100, 8);
        testData = new byte[72]; // AttributeDef.MAX_ATTRIBUTE_LENGTH
        java.nio.ByteBuffer.wrap(testData).putDouble(220.5);
    }
    
    @Benchmark
    public void benchmarkFullCacheOperation() {
        chunkManager.storeData("sensor-001", testData);
        chunkManager.getDataForAttribute("sensor-001", "voltage", System.currentTimeMillis());
    }
    
    @Benchmark
    public void benchmarkAttributeEncoding() {
        java.nio.ByteBuffer encoded = attributeDef.encodeAttribute(testData);
        byte[] encodedBytes = new byte[encoded.capacity()];
        encoded.get(encodedBytes);
    }
    
    @Benchmark
    public void benchmarkAttributeDecoding() {
        java.nio.ByteBuffer encoded = attributeDef.encodeAttribute(testData);
        byte[] encodedBytes = new byte[encoded.capacity()];
        encoded.get(encodedBytes);
        attributeDef.decodeAttribute(encodedBytes);
    }
    
    @Benchmark
    public void benchmarkChunkStorage() {
        timeChunk.storeData(testData);
    }
    
    @Benchmark
    public void benchmarkChunkRetrieval() {
        timeChunk.getDataBuffer();
    }
    
    @Benchmark
    public void benchmarkChunkManagerStorage() {
        chunkManager.storeData("sensor-001", testData);
    }
    
    @Benchmark
    public void benchmarkChunkManagerRetrieval() {
        chunkManager.getDataForAttribute("sensor-001", "voltage", System.currentTimeMillis());
    }
    
    @Benchmark
    public void benchmarkMultiSensorAccess() {
        for (int i = 0; i < 10; i++) {
            chunkManager.storeData("sensor-" + i, testData);
            chunkManager.getDataForAttribute("sensor-" + i, "voltage", System.currentTimeMillis());
        }
    }
    
    @Benchmark
    public void benchmarkHighThroughput() {
        for (int i = 0; i < 100; i++) {
            chunkManager.storeData("sensor-" + (i % 10), testData);
        }
    }
    
    @Benchmark
    public void benchmarkMemoryEfficiency() {
        for (int i = 0; i < 1000; i++) {
            chunkManager.storeData("sensor-" + (i % 100), testData);
        }
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CacheSystemBenchmark.class.getSimpleName())
                .build();
        
        new Runner(opt).run();
    }
}