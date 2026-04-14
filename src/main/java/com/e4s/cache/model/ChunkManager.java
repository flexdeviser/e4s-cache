package com.e4s.cache.model;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkManager {
    private final int maxChunks;
    private final int chunkIntervalHours;
    private final long maxMemoryBytes;
    
    private final ConcurrentHashMap<String, TimeChunk> sensorChunks = new ConcurrentHashMap<>();
    private final AtomicLong currentChunkId = new AtomicLong(0);
    
    private long totalBytesInUse;
    private long totalDataPoints;
    
    public ChunkManager(int maxChunks, int chunkIntervalHours, long maxMemoryBytes) {
        this.maxChunks = maxChunks;
        this.chunkIntervalHours = chunkIntervalHours;
        this.maxMemoryBytes = maxMemoryBytes;
        this.totalBytesInUse = 0;
        this.totalDataPoints = 0;
    }
    
    public TimeChunk getOrCreateChunk(String sensorId, long currentTimeEpoch) {
        return sensorChunks.compute(sensorId, (key, chunk) -> {
            if (chunk == null) {
                return createNewChunk(key, currentTimeEpoch);
            }
            
            long chunkStartTime = chunk.getStartTime().toEpochMilli();
            if (currentTimeEpoch >= chunkStartTime + chunkIntervalHours * 3600000L) {
                evictChunk(chunk);
                return createNewChunk(key, currentTimeEpoch);
            }
            
            return chunk;
        });
    }
    
    public byte[] getDataForAttribute(String sensorId, String attributeName, long currentTimeEpoch) {
        TimeChunk chunk = sensorChunks.get(sensorId);
        if (chunk == null) {
            return null;
        }
        
        long chunkStartTime = chunk.getStartTime().toEpochMilli();
        if (currentTimeEpoch < chunkStartTime) {
            evictChunk(chunk);
            return null;
        }
        
        return chunk.getDataForAttribute(attributeName);
    }
    
    public void storeData(String sensorId, byte[] attributeData) {
        long currentTimeEpoch = System.currentTimeMillis();
        TimeChunk chunk = getOrCreateChunk(sensorId, currentTimeEpoch);
        chunk.storeData(attributeData);
        totalBytesInUse += AttributeDef.MAX_ATTRIBUTE_LENGTH;
        totalDataPoints++;
    }
    
    public int getChunkCount() {
        return sensorChunks.size();
    }
    
    public long getTotalBytesInUse() {
        return totalBytesInUse;
    }
    
    public long getTotalDataPoints() {
        return totalDataPoints;
    }
    
    private TimeChunk createNewChunk(String sensorId, long currentTimeEpoch) {
        long chunkId = currentChunkId.incrementAndGet();
        long chunkStartTime = currentTimeEpoch - (currentTimeEpoch % (chunkIntervalHours * 3600000L));
        
        TimeChunk chunk = new TimeChunk((int) chunkId, maxDataPointsPerChunk(), chunkStartTime);
        sensorChunks.put(sensorId, chunk);
        
        evictIfNecessary();
        return chunk;
    }
    
    private int maxDataPointsPerChunk() {
        return (int) (maxMemoryBytes / (AttributeDef.MAX_ATTRIBUTE_LENGTH * 10));
    }
    
    private void evictIfNecessary() {
        while (sensorChunks.size() > maxChunks || totalBytesInUse > maxMemoryBytes) {
            evictOldestChunk();
        }
    }
    
    private void evictOldestChunk() {
        TimeChunk oldest = null;
        for (TimeChunk chunk : sensorChunks.values()) {
            if (oldest == null || chunk.getStartTime().isBefore(oldest.getStartTime())) {
                oldest = chunk;
            }
        }
        
        if (oldest != null) {
            evictChunk(oldest);
        }
    }
    
    public void evictOldestChunksBeyondWindow(long currentTimeEpoch) {
        Instant cutoff = Instant.ofEpochMilli(currentTimeEpoch).minus(21, ChronoUnit.DAYS);
        
        sensorChunks.entrySet().removeIf(entry -> {
            if (entry.getValue().getEndTime().isBefore(cutoff)) {
                evictChunk(entry.getValue());
                return true;
            }
            return false;
        });
    }
    
    private void evictChunk(TimeChunk chunk) {
        sensorChunks.remove(chunk.getChunkId());
        totalBytesInUse -= chunk.getDataPointCount() * AttributeDef.MAX_ATTRIBUTE_LENGTH;
        totalDataPoints -= chunk.getDataPointCount();
    }
    
    public Map<String, ChunkInfo> getAllChunkInfo() {
        Map<String, ChunkInfo> info = new HashMap<>();
        Instant currentTime = Instant.now();
        Instant cutoff = currentTime.minus(21, ChronoUnit.DAYS);
        
        for (Map.Entry<String, TimeChunk> entry : sensorChunks.entrySet()) {
            TimeChunk chunk = entry.getValue();
            
            boolean isExpired = chunk.getEndTime().isBefore(cutoff);
            
            info.put(entry.getKey(), new ChunkInfo(
                chunk.getChunkId(),
                chunk.getStartTime(),
                chunk.getEndTime(),
                chunk.getDataPointCount(),
                isExpired
            ));
        }
        
        return info;
    }
    
    public static class ChunkInfo {
        private final int chunkId;
        private final Instant startTime;
        private final Instant endTime;
        private final int dataPointCount;
        private final boolean expired;
        
        public ChunkInfo(int chunkId, Instant startTime, Instant endTime, 
                        int dataPointCount, boolean expired) {
            this.chunkId = chunkId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.dataPointCount = dataPointCount;
            this.expired = expired;
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
        
        public int getDataPointCount() {
            return dataPointCount;
        }
        
        public boolean isExpired() {
            return expired;
        }
    }
}