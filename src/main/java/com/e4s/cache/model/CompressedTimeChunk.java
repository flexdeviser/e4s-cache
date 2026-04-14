package com.e4s.cache.model;

import net.jpountz.lz4.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class CompressedTimeChunk {
    private static final Logger logger = LoggerFactory.getLogger(CompressedTimeChunk.class);
    
    private final int chunkId;
    private final Instant startTime;
    private final Instant endTime;
    private final int maxDataPoints;
    private final int maxCompressedSize;
    
    private ByteBuffer compressedBuffer;
    private ByteBuffer uncompressedBuffer;
    private int dataPointCount;
    private long timestamp;
    
    private final LZ4Compressor compressor;
    private final LZ4Decompressor decompressor;
    
    public CompressedTimeChunk(int chunkId, int maxDataPoints, long startTimeEpoch) {
        this.chunkId = chunkId;
        this.maxDataPoints = maxDataPoints;
        this.startTime = Instant.ofEpochMilli(startTimeEpoch);
        this.endTime = this.startTime.plus(24, ChronoUnit.HOURS);
        
        LZ4Factory factory = LZ4Factory.fastestInstance();
        this.compressor = factory.fastCompressor();
        this.decompressor = factory.fastDecompressor();
        
        int uncompressedSize = maxDataPoints * AttributeDef.MAX_ATTRIBUTE_LENGTH;
        this.maxCompressedSize = compressor.maxCompressedLength(uncompressedSize);
        
        this.compressedBuffer = ByteBuffer.allocateDirect(maxCompressedSize);
        this.uncompressedBuffer = ByteBuffer.allocateDirect(uncompressedSize);
        
        this.dataPointCount = 0;
        this.timestamp = System.currentTimeMillis();
    }
    
    public void storeData(byte[] attributeData) {
        if (!canStoreMoreData()) {
            return;
        }
        
        uncompressedBuffer.put(attributeData);
        dataPointCount++;
        timestamp = System.currentTimeMillis();
    }
    
    public void compress() {
        if (dataPointCount == 0) {
            return;
        }
        
        try {
            int dataSize = dataPointCount * AttributeDef.MAX_ATTRIBUTE_LENGTH;
            
            byte[] uncompressedArray = new byte[dataSize];
            byte[] compressedArray = new byte[maxCompressedSize];
            
            uncompressedBuffer.position(0);
            uncompressedBuffer.limit(dataSize);
            uncompressedBuffer.get(uncompressedArray);
            
            int compressedSize = compressor.compress(uncompressedArray, 0, dataSize, compressedArray, 0, maxCompressedSize);
            
            compressedBuffer.position(0);
            compressedBuffer.put(compressedArray, 0, compressedSize);
            compressedBuffer.position(0);
            compressedBuffer.limit(compressedSize);
            
            logger.debug("Compressed {} data points to {} bytes (ratio: {:.2f}x)",
                dataPointCount, compressedSize, (double)dataSize / compressedSize);
                
        } catch (Exception e) {
            logger.error("Compression failed for chunk {}", chunkId, e);
        }
    }
    
    public ByteBuffer getDataBuffer() {
        try {
            int compressedSize = compressedBuffer.limit();
            if (compressedSize == 0) {
                ByteBuffer snapshot = uncompressedBuffer.duplicate();
                snapshot.position(0);
                snapshot.limit(dataPointCount * AttributeDef.MAX_ATTRIBUTE_LENGTH);
                return snapshot;
            }
            
            int uncompressedSize = dataPointCount * AttributeDef.MAX_ATTRIBUTE_LENGTH;
            
            byte[] compressedArray = new byte[compressedSize];
            byte[] uncompressedArray = new byte[uncompressedSize];
            
            compressedBuffer.position(0);
            compressedBuffer.get(compressedArray);
            
            decompressor.decompress(compressedArray, 0, uncompressedArray, 0, uncompressedSize);
            
            uncompressedBuffer.position(0);
            uncompressedBuffer.put(uncompressedArray);
            uncompressedBuffer.position(0);
            uncompressedBuffer.limit(uncompressedSize);
            
            ByteBuffer snapshot = uncompressedBuffer.duplicate();
            snapshot.position(0);
            snapshot.limit(uncompressedSize);
            return snapshot;
            
        } catch (Exception e) {
            logger.error("Decompression failed for chunk {}", chunkId, e);
            return ByteBuffer.allocate(0);
        }
    }
    
    public byte[] getDataForAttribute(String attributeName) {
        ByteBuffer buffer = getDataBuffer();
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
                byte[] valueBytes = new byte[buffer.remaining()];
                buffer.get(valueBytes);
                return valueBytes;
            }
        }
        
        return null;
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
    
    public int getDataPointCount() {
        return dataPointCount;
    }
    
    public long getLastUpdateTimestamp() {
        return timestamp;
    }
    
    public boolean isExpired(long currentTimeEpoch) {
        return currentTimeEpoch > endTime.plus(21, ChronoUnit.DAYS).toEpochMilli();
    }
    
    public int getCompressedSize() {
        return compressedBuffer.limit();
    }
    
    public int getUncompressedSize() {
        return dataPointCount * AttributeDef.MAX_ATTRIBUTE_LENGTH;
    }
    
    public double getCompressionRatio() {
        int compressed = getCompressedSize();
        int uncompressed = getUncompressedSize();
        if (compressed == 0) return 0.0;
        return (double) uncompressed / compressed;
    }
}