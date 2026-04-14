package com.e4s.cache.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class TimeChunk {
    private final int chunkId;
    private final Instant startTime;
    private final Instant endTime;
    private final int maxDataPoints;
    private ByteBuffer dataBuffer;
    private int dataPointCount;
    private long timestamp;
    
    public TimeChunk(int chunkId, int maxDataPoints, long startTimeEpoch) {
        this.chunkId = chunkId;
        this.maxDataPoints = maxDataPoints;
        this.startTime = Instant.ofEpochMilli(startTimeEpoch);
        this.endTime = this.startTime.plus(24, ChronoUnit.HOURS);
        this.dataBuffer = ByteBuffer.allocate(maxDataPoints * AttributeDef.MAX_ATTRIBUTE_LENGTH);
        this.dataPointCount = 0;
        this.timestamp = System.currentTimeMillis();
    }
    
    public int getChunkId() {
        return chunkId;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public int getMaxDataPoints() {
        return maxDataPoints;
    }
    
    public boolean canStoreMoreData() {
        return dataPointCount < maxDataPoints;
    }
    
    public void storeData(byte[] attributeData) {
        if (!canStoreMoreData()) {
            return;
        }
        dataBuffer.put(attributeData);
        dataPointCount++;
        timestamp = System.currentTimeMillis();
    }
    
    public ByteBuffer getDataBuffer() {
        ByteBuffer snapshot = dataBuffer.duplicate();
        snapshot.position(0);
        snapshot.limit(dataPointCount * AttributeDef.MAX_ATTRIBUTE_LENGTH);
        return snapshot;
    }
    
    public int getDataPointCount() {
        return dataPointCount;
    }
    
    public long getLastUpdateTimestamp() {
        return timestamp;
    }
    
    public boolean isExpired(long currentTimeEpoch) {
        return currentTimeEpoch > endTime.plus(21, ChronoUnit.DAYS).toEpochMilli();
    }
    
    public byte[] getDataForAttribute(String attributeName) {
        ByteBuffer buffer = dataBuffer.duplicate();
        buffer.position(0);
        
        int nameBytes = attributeName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        byte[] attributeBytes = new byte[AttributeDef.MAX_ATTRIBUTE_LENGTH];
        
        for (int i = 0; i < dataPointCount; i++) {
            int pos = i * AttributeDef.MAX_ATTRIBUTE_LENGTH;
            buffer.position(pos);
            buffer.get(attributeBytes, 0, AttributeDef.MAX_ATTRIBUTE_LENGTH);
            
            String currentName = new String(attributeBytes, 0, AttributeDef.MAX_ATTRIBUTE_LENGTH, 
                java.nio.charset.StandardCharsets.UTF_8).trim();
            
            if (currentName.equals(attributeName)) {
                int valueOffset = pos + nameBytes;
                buffer.position(valueOffset);
                byte[] valueBytes = new byte[dataBuffer.remaining()];
                buffer.get(valueBytes);
                return valueBytes;
            }
        }
        
        return null;
    }
}