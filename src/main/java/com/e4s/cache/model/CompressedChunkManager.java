package com.e4s.cache.model;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CompressedChunkManager {
    private final int maxChunks;
    private final int chunkIntervalHours;
    private final long maxMemoryBytes;
    
    private final ConcurrentHashMap<String, CompressedTimeChunk> sensorChunks;
    private final AtomicLong currentChunkId;
    
    private long totalBytesInUse;
    private long totalDataPoints;
    private long totalCompressedBytes;
    private long totalUncompressedBytes;
    
    public CompressedChunkManager(int maxChunks, int chunkIntervalHours, long maxMemoryBytes) {
        this.maxChunks = maxChunks;
        this.chunkIntervalHours = chunkIntervalHours;
        this.maxMemoryBytes = maxMemoryBytes;
        
        this.sensorChunks = new ConcurrentHashMap<>();
        this.currentChunkId = new AtomicLong(0);
        
        this.totalBytesInUse = 0;
        this.totalDataPoints = 0;
        this.totalCompressedBytes = 0;
        this.totalUncompressedBytes = 0;
    }
    
    public CompressedTimeChunk getOrCreateChunk(String sensorId, long currentTimeEpoch) {
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
        CompressedTimeChunk chunk = sensorChunks.get(sensorId);
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
        CompressedTimeChunk chunk = getOrCreateChunk(sensorId, currentTimeEpoch);
        chunk.storeData(attributeData);
        
        totalBytesInUse += AttributeDef.MAX_ATTRIBUTE_LENGTH;
        totalDataPoints++;
        totalUncompressedBytes += AttributeDef.MAX_ATTRIBUTE_LENGTH;
        
        if (chunk.getDataPointCount() % 100 == 0 || !chunk.canStoreMoreData()) {
            chunk.compress();
            totalCompressedBytes = calculateTotalCompressedSize();
        }
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
    
    public long getTotalCompressedBytes() {
        return totalCompressedBytes;
    }
    
    public long getTotalUncompressedBytes() {
        return totalUncompressedBytes;
    }
    
    public double getCompressionRatio() {
        if (totalCompressedBytes == 0) return 0.0;
        return (double) totalUncompressedBytes / totalCompressedBytes;
    }
    
    public double getMemorySavings() {
        if (totalUncompressedBytes == 0) return 0.0;
        return (1.0 - (double) totalCompressedBytes / totalUncompressedBytes) * 100;
    }
    
    private CompressedTimeChunk createNewChunk(String sensorId, long currentTimeEpoch) {
        long chunkId = currentChunkId.incrementAndGet();
        long chunkStartTime = currentTimeEpoch - (currentTimeEpoch % (chunkIntervalHours * 3600000L));
        
        int maxDataPoints = maxDataPointsPerChunk();
        CompressedTimeChunk chunk = new CompressedTimeChunk((int) chunkId, maxDataPoints, chunkStartTime);
        sensorChunks.put(sensorId, chunk);
        
        evictIfNecessary();
        return chunk;
    }
    
    private int maxDataPointsPerChunk() {
        return (int) (maxMemoryBytes / (maxChunks * AttributeDef.MAX_ATTRIBUTE_LENGTH));
    }
    
    private void evictIfNecessary() {
        while (sensorChunks.size() > maxChunks || totalBytesInUse > maxMemoryBytes) {
            evictOldestChunk();
        }
    }
    
    private void evictOldestChunk() {
        CompressedTimeChunk oldest = null;
        for (CompressedTimeChunk chunk : sensorChunks.values()) {
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
    
    private void evictChunk(CompressedTimeChunk chunk) {
        sensorChunks.remove(chunk.getChunkId());
        totalBytesInUse -= chunk.getDataPointCount() * AttributeDef.MAX_ATTRIBUTE_LENGTH;
        totalDataPoints -= chunk.getDataPointCount();
        totalUncompressedBytes -= chunk.getDataPointCount() * AttributeDef.MAX_ATTRIBUTE_LENGTH;
        totalCompressedBytes -= chunk.getCompressedSize();
    }
    
    private long calculateTotalCompressedSize() {
        long total = 0;
        for (CompressedTimeChunk chunk : sensorChunks.values()) {
            total += chunk.getCompressedSize();
        }
        return total;
    }
    
    public Map<String, ChunkInfo> getAllChunkInfo() {
        Map<String, ChunkInfo> info = new HashMap<>();
        Instant currentTime = Instant.now();
        Instant cutoff = currentTime.minus(21, ChronoUnit.DAYS);
        
        for (Map.Entry<String, CompressedTimeChunk> entry : sensorChunks.entrySet()) {
            CompressedTimeChunk chunk = entry.getValue();
            
            boolean isExpired = chunk.getEndTime().isBefore(cutoff);
            
            info.put(entry.getKey(), new ChunkInfo(
                chunk.getChunkId(),
                chunk.getStartTime(),
                chunk.getEndTime(),
                chunk.getDataPointCount(),
                chunk.getCompressedSize(),
                chunk.getUncompressedSize(),
                chunk.getCompressionRatio(),
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
        private final int compressedSize;
        private final int uncompressedSize;
        private final double compressionRatio;
        private final boolean expired;
        
        public ChunkInfo(int chunkId, Instant startTime, Instant endTime, 
                        int dataPointCount, int compressedSize, int uncompressedSize,
                        double compressionRatio, boolean expired) {
            this.chunkId = chunkId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.dataPointCount = dataPointCount;
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
            this.compressionRatio = compressionRatio;
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
        
        public int getCompressedSize() {
            return compressedSize;
        }
        
        public int getUncompressedSize() {
            return uncompressedSize;
        }
        
        public double getCompressionRatio() {
            return compressionRatio;
        }
        
        public boolean isExpired() {
            return expired;
        }
    }
}