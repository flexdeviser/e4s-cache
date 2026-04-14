package com.e4s.cache.benchmark;

import com.e4s.cache.model.AttributeDef;
import com.e4s.cache.model.AttributeInfo;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class AttributeDefBenchmark {
    
    private AttributeDef attributeDef;
    private byte[] testData;
    private byte[] encodedData;
    private static final String ATTRIBUTE_NAME = "voltage";
    private static final int MAX_DATA_POINTS = 100;
    private static final int DATA_TYPE_SIZE = 8;
    
    @Setup
    public void setup() {
        attributeDef = new AttributeDef(ATTRIBUTE_NAME, MAX_DATA_POINTS, DATA_TYPE_SIZE);
        testData = new byte[DATA_TYPE_SIZE];
        ByteBuffer.wrap(testData).putDouble(220.5);
        
        ByteBuffer encoded = attributeDef.encodeAttribute(testData);
        encodedData = new byte[encoded.capacity()];
        encoded.get(encodedData);
    }
    
    @Benchmark
    public byte[] benchmarkEncodeAttribute() {
        ByteBuffer encoded = attributeDef.encodeAttribute(testData);
        byte[] result = new byte[encoded.capacity()];
        encoded.get(result);
        return result;
    }
    
    @Benchmark
    public AttributeInfo benchmarkDecodeAttribute() {
        return attributeDef.decodeAttribute(encodedData);
    }
    
    @Benchmark
    public AttributeInfo benchmarkEncodeDecodeRoundTrip() {
        ByteBuffer encoded = attributeDef.encodeAttribute(testData);
        byte[] encodedBytes = new byte[encoded.capacity()];
        encoded.get(encodedBytes);
        return attributeDef.decodeAttribute(encodedBytes);
    }
    
    @Benchmark
    public int benchmarkGetAttributeSize() {
        return attributeDef.getAttributeSize();
    }
    
    @Benchmark
    public String benchmarkGetName() {
        return attributeDef.getName();
    }
    
    @Benchmark
    public int benchmarkGetMaxDataPoints() {
        return attributeDef.getMaxDataPoints();
    }
    
    @Benchmark
    public int benchmarkGetDataTypeSize() {
        return attributeDef.getDataTypeSize();
    }
    
    @Benchmark
    public byte[] benchmarkEncodeMultipleValues() {
        double[] values = {100.0, 200.0, 300.0, 400.0, 500.0};
        byte[] result = new byte[values.length * DATA_TYPE_SIZE];
        
        for (int i = 0; i < values.length; i++) {
            byte[] data = new byte[DATA_TYPE_SIZE];
            ByteBuffer.wrap(data).putDouble(values[i]);
            ByteBuffer encoded = attributeDef.encodeAttribute(data);
            System.arraycopy(encoded.array(), 0, result, i * DATA_TYPE_SIZE, DATA_TYPE_SIZE);
        }
        
        return result;
    }
    
    @Benchmark
    public AttributeInfo[] benchmarkDecodeMultipleValues() {
        AttributeInfo[] results = new AttributeInfo[5];
        for (int i = 0; i < 5; i++) {
            results[i] = attributeDef.decodeAttribute(encodedData);
        }
        return results;
    }
}