package com.e4s.cache.benchmark;

import com.e4s.cache.model.ChunkManager;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class ChunkManagerBenchmark {
    
    private ChunkManager chunkManager;
    private byte[] testData;
    private static final int MAX_CHUNKS = 100;
    private static final int CHUNK_INTERVAL_HOURS = 24;
    private static final long MAX_MEMORY_BYTES = 1024L * 1024 * 1024; // 1GB
    
    @Setup
    public void setup() {
        chunkManager = new ChunkManager(MAX_CHUNKS, CHUNK_INTERVAL_HOURS, MAX_MEMORY_BYTES);
        testData = new byte[72]; // AttributeDef.MAX_ATTRIBUTE_LENGTH
        ByteBuffer.wrap(testData).putDouble(220.5);
    }
    
    @Benchmark
    public void benchmarkStoreData() {
        chunkManager.storeData("sensor-001", testData);
    }
    
    @Benchmark
    public void benchmarkStoreMultipleSensors() {
        for (int i = 0; i < 10; i++) {
            chunkManager.storeData("sensor-" + i, testData);
        }
    }
    
    @Benchmark
    public byte[] benchmarkGetDataForAttribute() {
        return chunkManager.getDataForAttribute("sensor-001", "voltage", System.currentTimeMillis());
    }
    
    @Benchmark
    public int benchmarkGetChunkCount() {
        return chunkManager.getChunkCount();
    }
    
    @Benchmark
    public long benchmarkGetTotalBytesInUse() {
        return chunkManager.getTotalBytesInUse();
    }
    
    @Benchmark
    public long benchmarkGetTotalDataPoints() {
        return chunkManager.getTotalDataPoints();
    }
    
    @Benchmark
    public void benchmarkGetOrCreateChunk() {
        chunkManager.getOrCreateChunk("sensor-001", System.currentTimeMillis());
    }
    
    @Benchmark
    public void benchmarkEvictOldestChunksBeyondWindow() {
        chunkManager.evictOldestChunksBeyondWindow(System.currentTimeMillis());
    }
    
    @Benchmark
    public java.util.Map<String, ChunkManager.ChunkInfo> benchmarkGetAllChunkInfo() {
        return chunkManager.getAllChunkInfo();
    }
    
    @Benchmark
    public void benchmarkSequentialStoreAndRetrieve() {
        chunkManager.storeData("sensor-001", testData);
        chunkManager.getDataForAttribute("sensor-001", "voltage", System.currentTimeMillis());
    }
    
    @Benchmark
    public void benchmarkBulkStore() {
        for (int i = 0; i < 100; i++) {
            chunkManager.storeData("sensor-" + (i % 10), testData);
        }
    }
    
    @Benchmark
    public void benchmarkBulkRetrieve() {
        for (int i = 0; i < 100; i++) {
            chunkManager.getDataForAttribute("sensor-" + (i % 10), "voltage", System.currentTimeMillis());
        }
    }
    
    @Benchmark
    public void benchmarkConcurrentAccess() {
        String sensorId = "sensor-" + (int)(Math.random() * 10);
        chunkManager.storeData(sensorId, testData);
        chunkManager.getDataForAttribute(sensorId, "voltage", System.currentTimeMillis());
    }
    
    @Benchmark
    public void benchmarkMemoryPressure() {
        for (int i = 0; i < 1000; i++) {
            chunkManager.storeData("sensor-" + i, testData);
        }
    }
}