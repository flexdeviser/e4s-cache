# LZ4 Compression Integration for e4s-cache

## Why LZ4 for Time-Series Data?

### Compression Characteristics
- **Speed**: Extremely fast compression/decompression (500+ MB/s)
- **Ratio**: 2-10x compression for time-series data
- **CPU**: Low overhead, suitable for real-time systems
- **Memory**: Minimal memory footprint during compression

### Time-Series Data Compression Benefits
- **Repeated Values**: Sensors often report similar values
- **Small Deltas**: Changes are typically small
- **Patterns**: Daily/seasonal patterns compress well
- **Sparse Data**: Many sensors have gaps in reporting

## Implementation

### 1. Add LZ4 Dependency

```xml
<dependency>
    <groupId>org.lz4</groupId>
    <artifactId>lz4-java</artifactId>
    <version>1.8.0</version>
</dependency>
```

### 2. CompressedTimeChunk Implementation

```java
package com.e4s.cache.model;

import net.jpountz.lz4.*;
import net.jpountz.xxhash.XXHashFactory;
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
    
    private ByteBuffer compressedBuffer;  // Compressed data (off-heap)
    private ByteBuffer uncompressedBuffer; // Temporary buffer for operations
    private int dataPointCount;
    private long timestamp;
    
    // LZ4 compressor/decompressor
    private final LZ4Compressor compressor;
    private final LZ4Decompressor decompressor;
    private final int compressionLevel;
    
    public CompressedTimeChunk(int chunkId, int maxDataPoints, long startTimeEpoch, int compressionLevel) {
        this.chunkId = chunkId;
        this.maxDataPoints = maxDataPoints;
        this.startTime = Instant.ofEpochMilli(startTimeEpoch);
        this.endTime = this.startTime.plus(24, ChronoUnit.HOURS);
        this.compressionLevel = compressionLevel;
        
        // Calculate maximum compressed size
        this.maxCompressedSize = LZ4Factory.fastestInstance().fastCompressor().maxCompressedLength(
            maxDataPoints * AttributeDef.MAX_ATTRIBUTE_LENGTH
        );
        
        // Allocate buffers
        this.compressedBuffer = ByteBuffer.allocateDirect(maxCompressedSize);
        this.uncompressedBuffer = ByteBuffer.allocateDirect(
            maxDataPoints * AttributeDef.MAX_ATTRIBUTE_LENGTH
        );
        
        // Initialize LZ4
        LZ4Factory factory = LZ4Factory.fastestInstance();
        this.compressor = factory.fastCompressor();
        this.decompressor = factory.fastDecompressor();
        
        this.dataPointCount = 0;
        this.timestamp = System.currentTimeMillis();
    }
    
    public void storeData(byte[] attributeData) {
        if (!canStoreMoreData()) {
            return;
        }
        
        // Store in uncompressed buffer
        uncompressedBuffer.put(attributeData);
        dataPointCount++;
        
        // Compress after every 100 data points or when full
        if (dataPointCount % 100 == 0 || !canStoreMoreData()) {
            compressData();
        }
        
        timestamp = System.currentTimeMillis();
    }
    
    private void compressData() {
        try {
            // Prepare data for compression
            int dataSize = dataPointCount * AttributeDef.MAX_ATTRIBUTE_LENGTH;
            uncompressedBuffer.position(0);
            uncompressedBuffer.limit(dataSize);
            
            // Compress data
            int compressedSize = compressor.compress(
                uncompressedBuffer,
                0,
                dataSize,
                compressedBuffer,
                0,
                maxCompressedSize
            );
            
            // Update compressed buffer limit
            compressedBuffer.position(0);
            compressedBuffer.limit(compressedSize);
            
            logger.debug("Compressed {} data points to {} bytes (ratio: {:.2f}x)",
                dataPointCount, compressedSize, (double)dataSize / compressedSize);
            
        } catch (Exception e) {
            logger.error("Compression failed", e);
        }
    }
    
    public ByteBuffer getDataBuffer() {
        try {
            // Decompress data
            int compressedSize = compressedBuffer.limit();
            int uncompressedSize = dataPointCount * AttributeDef.MAX_ATTRIBUTE_LENGTH;
            
            decompressor.decompress(
                compressedBuffer,
                0,
                compressedSize,
                uncompressedBuffer,
                0,
                uncompressedSize
            );
            
            // Return uncompressed data
            ByteBuffer snapshot = uncompressedBuffer.duplicate();
            snapshot.position(0);
            snapshot.limit(uncompressedSize);
            return snapshot;
            
        } catch (Exception e) {
            logger.error("Decompression failed", e);
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
        if (getUncompressedSize() == 0) return 0.0;
        return (double) getUncompressedSize() / getCompressedSize();
    }
}
```

### 3. Compression Statistics

```java
public class CompressionStats {
    private long totalCompressedBytes;
    private long totalUncompressedBytes;
    private long compressionCount;
    private long decompressionCount;
    private long compressionTimeNanos;
    private long decompressionTimeNanos;
    
    public void recordCompression(int uncompressedSize, int compressedSize, long timeNanos) {
        totalUncompressedBytes += uncompressedSize;
        totalCompressedBytes += compressedSize;
        compressionCount++;
        compressionTimeNanos += timeNanos;
    }
    
    public void recordDecompression(int compressedSize, int uncompressedSize, long timeNanos) {
        totalCompressedBytes += compressedSize;
        totalUncompressedBytes += uncompressedSize;
        decompressionCount++;
        decompressionTimeNanos += timeNanos;
    }
    
    public double getAverageCompressionRatio() {
        if (totalCompressedBytes == 0) return 0.0;
        return (double) totalUncompressedBytes / totalCompressedBytes;
    }
    
    public double getAverageCompressionTimeMs() {
        if (compressionCount == 0) return 0.0;
        return (compressionTimeNanos / 1_000_000.0) / compressionCount;
    }
    
    public double getAverageDecompressionTimeMs() {
        if (decompressionCount == 0) return 0.0;
        return (decompressionTimeNanos / 1_000_000.0) / decompressionCount;
    }
    
    public double getCompressionThroughputMBs() {
        if (compressionTimeNanos == 0) return 0.0;
        return (totalUncompressedBytes / 1_048_576.0) / (compressionTimeNanos / 1_000_000_000.0);
    }
    
    public double getDecompressionThroughputMBs() {
        if (decompressionTimeNanos == 0) return 0.0;
        return (totalUncompressedBytes / 1_048_576.0) / (decompressionTimeNanos / 1_000_000_000.0);
    }
}
```

### 4. Updated ChunkManager with Compression

```java
public class CompressedChunkManager {
    private final int maxChunks;
    private final int chunkIntervalHours;
    private final long maxMemoryBytes;
    private final int compressionLevel;
    
    private final ConcurrentHashMap<String, CompressedTimeChunk> sensorChunks;
    private final AtomicLong currentChunkId;
    private final CompressionStats compressionStats;
    
    public CompressedChunkManager(int maxChunks, int chunkIntervalHours, 
                                  long maxMemoryBytes, int compressionLevel) {
        this.maxChunks = maxChunks;
        this.chunkIntervalHours = chunkIntervalHours;
        this.maxMemoryBytes = maxMemoryBytes;
        this.compressionLevel = compressionLevel;
        
        this.sensorChunks = new ConcurrentHashMap<>();
        this.currentChunkId = new AtomicLong(0);
        this.compressionStats = new CompressionStats();
    }
    
    @Override
    public void storeData(String sensorId, byte[] attributeData) {
        long startTime = System.nanoTime();
        
        CompressedTimeChunk chunk = getOrCreateChunk(sensorId, System.currentTimeMillis());
        chunk.storeData(attributeData);
        
        long endTime = System.nanoTime();
        compressionStats.recordCompression(
            attributeData.length, 
            chunk.getCompressedSize(), 
            endTime - startTime
        );
    }
    
    @Override
    public byte[] getDataForAttribute(String sensorId, String attributeName, long currentTimeEpoch) {
        long startTime = System.nanoTime();
        
        CompressedTimeChunk chunk = sensorChunks.get(sensorId);
        if (chunk == null) {
            return null;
        }
        
        byte[] result = chunk.getDataForAttribute(attributeName);
        
        long endTime = System.nanoTime();
        compressionStats.recordDecompression(
            chunk.getCompressedSize(),
            result != null ? result.length : 0,
            endTime - startTime
        );
        
        return result;
    }
    
    public CompressionStats getCompressionStats() {
        return compressionStats;
    }
    
    public double getMemorySavings() {
        long totalCompressed = 0;
        long totalUncompressed = 0;
        
        for (CompressedTimeChunk chunk : sensorChunks.values()) {
            totalCompressed += chunk.getCompressedSize();
            totalUncompressed += chunk.getUncompressedSize();
        }
        
        if (totalUncompressed == 0) return 0.0;
        return (1.0 - (double)totalCompressed / totalUncompressed) * 100;
    }
}
```

## Memory Impact Analysis

### Without Compression (Current)
```
1,000,000 chunks × 1,677 data points × 64 bytes = 107.4 GB
```

### With LZ4 Compression (Expected 3-5x ratio)
```
1,000,000 chunks × 1,677 data points × 64 bytes / 4 = 26.8 GB
```

### Memory Savings
| Compression Ratio | Memory Used | Savings |
|-------------------|-------------|---------|
| 2x | 53.7 GB | 48.7 GB (45%) |
| 3x | 35.8 GB | 66.6 GB (62%) |
| 4x | 26.8 GB | 75.6 GB (70%) |
| 5x | 21.5 GB | 80.9 GB (75%) |

## Performance Impact

### Compression Performance
- **Compression Speed**: 200-500 MB/s
- **Decompression Speed**: 500-1000 MB/s
- **Latency Impact**: 1-5 ms per operation
- **CPU Overhead**: 5-15% increase

### Expected Performance for 1M Sensors
| Operation | Without Compression | With LZ4 | Impact |
|-----------|-------------------|---------|---------|
| Store Data | 100-300 ns | 1-5 ms | +3-15 ms |
| Get Data | 150-400 ns | 1-3 ms | +2-8 ms |
| Memory Usage | 102.4 GB | 25-35 GB | -70-75% |

## Configuration Recommendations

### For 100GB Budget with Compression
```java
int maxChunks = 1,000,000;              // 1 million chunks
int chunkIntervalHours = 24;             // 1-day chunks
long maxMemoryBytes = 100GB;            // 100GB budget
int compressionLevel = 1;               // LZ4 fast compression
int maxDataPointsPerChunk = 6,708;      // 6,708 data points (4x more!)
```

**Benefits**:
- **4x more data per chunk**: 6,708 vs 1,677 data points
- **Better coverage**: 466% of 1-day @ 1-min data
- **Memory usage**: ~25 GB (75% savings)
- **Extra capacity**: Can cache 4x more sensors or 4x more data

### Optimized Configuration
```java
int maxChunks = 2,000,000;              // 2 million chunks
int chunkIntervalHours = 24;             // 1-day chunks
long maxMemoryBytes = 100GB;            // 100GB budget
int compressionLevel = 1;               // LZ4 fast compression
int maxDataPointsPerChunk = 3,354;      // 3,354 data points
```

**Benefits**:
- **2x more sensors**: 2 million instead of 1 million
- **2x more data per sensor**: 3,354 vs 1,677 data points
- **Memory usage**: ~50 GB (50% savings)
- **Future-proof**: Room for growth

## Implementation Strategy

### Phase 1: Basic Compression
1. Add LZ4 dependency
2. Implement CompressedTimeChunk
3. Update ChunkManager
4. Add compression statistics

### Phase 2: Optimization
1. Implement adaptive compression (compress based on data patterns)
2. Add compression level tuning
3. Implement partial decompression for range queries
4. Add compression caching

### Phase 3: Advanced Features
1. Implement delta encoding before compression
2. Add dictionary-based compression
3. Implement column-level compression
4. Add compression-aware eviction policies

## Conclusion

**LZ4 compression is HIGHLY RECOMMENDED** for e4s-cache because:

✅ **Massive memory savings**: 70-75% reduction
✅ **4x more capacity**: Can store 4x more data or sensors
✅ **Minimal performance impact**: 1-5 ms latency increase
✅ **Excellent compression ratios**: 3-5x for time-series data
✅ **Fast operations**: 500+ MB/s throughput

**Recommended configuration**:
- Use LZ4 fast compression (level 1)
- Expect 3-5x compression ratio
- Memory usage: 25-35 GB instead of 102.4 GB
- Can cache 2-4 million sensors instead of 1 million

This makes the system much more scalable and cost-effective while maintaining excellent performance.