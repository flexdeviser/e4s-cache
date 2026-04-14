package com.e4s.cache.benchmark;

import com.e4s.cache.model.TimeChunk;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class TimeChunkBenchmark {
    
    private TimeChunk timeChunk;
    private byte[] testData;
    private static final int CHUNK_ID = 1;
    private static final int MAX_DATA_POINTS = 1000;
    private static final long START_TIME_EPOCH = System.currentTimeMillis();
    
    @Setup
    public void setup() {
        timeChunk = new TimeChunk(CHUNK_ID, MAX_DATA_POINTS, START_TIME_EPOCH);
        testData = new byte[72]; // AttributeDef.MAX_ATTRIBUTE_LENGTH
        ByteBuffer.wrap(testData).putDouble(220.5);
    }
    
    @Benchmark
    public void benchmarkStoreData() {
        timeChunk.storeData(testData);
    }
    
    @Benchmark
    public void benchmarkStoreMultipleDataPoints() {
        for (int i = 0; i < 10; i++) {
            timeChunk.storeData(testData);
        }
    }
    
    @Benchmark
    public java.nio.ByteBuffer benchmarkGetDataBuffer() {
        return timeChunk.getDataBuffer();
    }
    
    @Benchmark
    public int benchmarkGetDataPointCount() {
        return timeChunk.getDataPointCount();
    }
    
    @Benchmark
    public boolean benchmarkCanStoreMoreData() {
        return timeChunk.canStoreMoreData();
    }
    
    @Benchmark
    public long benchmarkGetLastUpdateTimestamp() {
        return timeChunk.getLastUpdateTimestamp();
    }
    
    @Benchmark
    public int benchmarkGetChunkId() {
        return timeChunk.getChunkId();
    }
    
    @Benchmark
    public java.time.Instant benchmarkGetStartTime() {
        return timeChunk.getStartTime();
    }
    
    @Benchmark
    public java.time.Instant benchmarkGetEndTime() {
        return timeChunk.getEndTime();
    }
    
    @Benchmark
    public int benchmarkGetMaxDataPoints() {
        return timeChunk.getMaxDataPoints();
    }
    
    @Benchmark
    public byte[] benchmarkGetDataForAttribute() {
        return timeChunk.getDataForAttribute("voltage");
    }
    
    @Benchmark
    public boolean benchmarkIsExpired() {
        return timeChunk.isExpired(System.currentTimeMillis());
    }
    
    @Benchmark
    public void benchmarkSequentialStoreAndRetrieve() {
        timeChunk.storeData(testData);
        timeChunk.getDataBuffer();
    }
    
    @Benchmark
    public void benchmarkBulkStore() {
        for (int i = 0; i < 100; i++) {
            timeChunk.storeData(testData);
        }
    }
    
    @Benchmark
    public void benchmarkBulkRetrieve() {
        for (int i = 0; i < 100; i++) {
            timeChunk.getDataBuffer();
        }
    }
}