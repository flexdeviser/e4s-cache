package com.e4s.cache.model;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadSafeCompressedChunkManager {
    private static final int STRIPE_COUNT = 64;
    
    private final int maxChunks;
    private final int chunkIntervalHours;
    private final long maxMemoryBytes;
    
    private final ConcurrentHashMap<String, ThreadSafeCompressedTimeChunk> sensorChunks;
    private final ConcurrentHashMap<String, ReadWriteLock> sensorLocks;
    private final Lock[] stripes;
    private final AtomicLong currentChunkId;
    
    private long totalBytesInUse;
    private long totalDataPoints;
    private long totalCompressedBytes;
    private long totalUncompressedBytes;
    
    public ThreadSafeCompressedChunkManager(int maxChunks, int chunkIntervalHours, long maxMemoryBytes) {
        this.maxChunks = maxChunks;
        this.chunkIntervalHours = chunkIntervalHours;
        this.maxMemoryBytes = maxMemoryBytes;
        
        this.sensorChunks = new ConcurrentHashMap<>();
        this.sensorLocks = new ConcurrentHashMap<>();
        this.stripes = new Lock[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new ReentrantLock();
        }
        this.currentChunkId = new AtomicLong(0);
        
        this.totalBytesInUse = 0;
        this.totalDataPoints = 0;
        this.totalCompressedBytes = 0;
        this.totalUncompressedBytes = 0;
    }
    
    private Lock getStripe(String sensorId) {
        int stripeIndex = Math.abs(sensorId.hashCode()) % STRIPE_COUNT;
        return stripes[stripeIndex];
    }
    
    private ReadWriteLock getSensorLock(String sensorId) {
        return sensorLocks.computeIfAbsent(sensorId, k -> new ReentrantReadWriteLock());
    }
    
    public ThreadSafeCompressedTimeChunk getOrCreateChunk(String sensorId, long currentTimeEpoch) {
        Lock stripeLock = getStripe(sensorId);
        stripeLock.lock();
        try {
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
        } finally {
            stripeLock.unlock();
        }
    }
    
    public byte[] getDataForAttribute(String sensorId, String attributeName, long currentTimeEpoch) {
        ReadWriteLock sensorLock = getSensorLock(sensorId);
        sensorLock.readLock().lock();
        try {
            ThreadSafeCompressedTimeChunk chunk = sensorChunks.get(sensorId);
            if (chunk == null) {
                return null;
            }
            
            long chunkStartTime = chunk.getStartTime().toEpochMilli();
            if (currentTimeEpoch < chunkStartTime) {
                sensorLock.readLock().unlock();
                sensorLock.writeLock().lock();
                try {
                    chunk = sensorChunks.get(sensorId);
                    if (chunk != null && chunk.getStartTime().toEpochMilli() < currentTimeEpoch) {
                        evictChunk(chunk);
                    }
                    return null;
                } finally {
                    sensorLock.writeLock().unlock();
                    sensorLock.readLock().lock();
                }
            }
            
            return chunk.getDataForAttribute(attributeName);
        } finally {
            sensorLock.readLock().unlock();
        }
    }
    
    public void storeData(String sensorId, byte[] attributeData) {
        long currentTimeEpoch = System.currentTimeMillis();
        
        ReadWriteLock sensorLock = getSensorLock(sensorId);
        sensorLock.writeLock().lock();
        try {
            ThreadSafeCompressedTimeChunk chunk = getOrCreateChunk(sensorId, currentTimeEpoch);
            chunk.storeData(attributeData);
            
            totalBytesInUse += AttributeDef.MAX_ATTRIBUTE_LENGTH;
            totalDataPoints++;
            totalUncompressedBytes += AttributeDef.MAX_ATTRIBUTE_LENGTH;
            
            if (chunk.getDataPointCount() % 100 == 0 || !chunk.canStoreMoreData()) {
                chunk.compress();
                updateTotalCompressedSize();
            }
        } finally {
            sensorLock.writeLock().unlock();
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
    
    private ThreadSafeCompressedTimeChunk createNewChunk(String sensorId, long currentTimeEpoch) {
        long chunkId = currentChunkId.incrementAndGet();
        long chunkStartTime = currentTimeEpoch - (currentTimeEpoch % (chunkIntervalHours * 3600000L));
        
        int maxDataPoints = maxDataPointsPerChunk();
        ThreadSafeCompressedTimeChunk chunk = new ThreadSafeCompressedTimeChunk((int) chunkId, maxDataPoints, chunkStartTime);
        sensorChunks.put(sensorId, chunk);
        
        evictIfNecessary();
        return chunk;
    }
    
    private int maxDataPointsPerChunk() {
        return (int) (maxMemoryBytes / (maxChunks * AttributeDef.MAX_ATTRIBUTE_LENGTH));
    }
    
    private void evictIfNecessary() {
        Lock stripeLock = stripes[0];
        stripeLock.lock();
        try {
            while (sensorChunks.size() > maxChunks || totalBytesInUse > maxMemoryBytes) {
                evictOldestChunk();
            }
        } finally {
            stripeLock.unlock();
        }
    }
    
    private void evictOldestChunk() {
        Lock stripeLock = stripes[0];
        stripeLock.lock();
        try {
            ThreadSafeCompressedTimeChunk oldest = null;
            String oldestSensorId = null;
            
            for (Map.Entry<String, ThreadSafeCompressedTimeChunk> entry : sensorChunks.entrySet()) {
                if (oldest == null || entry.getValue().getStartTime().isBefore(oldest.getStartTime())) {
                    oldest = entry.getValue();
                    oldestSensorId = entry.getKey();
                }
            }
            
            if (oldest != null && oldestSensorId != null) {
                evictChunk(oldest, oldestSensorId);
            }
        } finally {
            stripeLock.unlock();
        }
    }
    
    public void evictOldestChunksBeyondWindow(long currentTimeEpoch) {
        Lock stripeLock = stripes[0];
        stripeLock.lock();
        try {
            Instant cutoff = Instant.ofEpochMilli(currentTimeEpoch).minus(21, ChronoUnit.DAYS);
            
            sensorChunks.entrySet().removeIf(entry -> {
                if (entry.getValue().getEndTime().isBefore(cutoff)) {
                    evictChunk(entry.getValue(), entry.getKey());
                    return true;
                }
                return false;
            });
        } finally {
            stripeLock.unlock();
        }
    }
    
    private void evictChunk(ThreadSafeCompressedTimeChunk chunk) {
        evictChunk(chunk, null);
    }
    
    private void evictChunk(ThreadSafeCompressedTimeChunk chunk, String sensorId) {
        if (sensorId != null) {
            sensorChunks.remove(sensorId);
            sensorLocks.remove(sensorId);
        }
        
        totalBytesInUse -= chunk.getDataPointCount() * AttributeDef.MAX_ATTRIBUTE_LENGTH;
        totalDataPoints -= chunk.getDataPointCount();
        totalUncompressedBytes -= chunk.getDataPointCount() * AttributeDef.MAX_ATTRIBUTE_LENGTH;
        totalCompressedBytes -= chunk.getCompressedSize();
    }
    
    private void updateTotalCompressedSize() {
        long total = 0;
        for (ThreadSafeCompressedTimeChunk chunk : sensorChunks.values()) {
            total += chunk.getCompressedSize();
        }
        totalCompressedBytes = total;
    }
    
    public Map<String, ChunkInfo> getAllChunkInfo() {
        Map<String, ChunkInfo> info = new HashMap<>();
        Instant currentTime = Instant.now();
        Instant cutoff = currentTime.minus(21, ChronoUnit.DAYS);
        
        for (Map.Entry<String, ThreadSafeCompressedTimeChunk> entry : sensorChunks.entrySet()) {
            ThreadSafeCompressedTimeChunk chunk = entry.getValue();
            
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