# Memory Usage Analysis for 1 Million Sensors

## Current Implementation Memory Calculation

### Per-Sensor Memory Breakdown

#### 1. TimeChunk Memory (per sensor)
**On-Heap Metadata:**
- chunkId (int): 4 bytes
- startTime (Instant): ~24 bytes
- endTime (Instant): ~24 bytes  
- maxDataPoints (int): 4 bytes
- dataPointCount (int): 4 bytes
- timestamp (long): 8 bytes
- ByteBuffer reference: 8 bytes
- **Total On-Heap: ~76 bytes per sensor**

**Off-Heap Data Buffer:**
- Default maxDataPoints calculation:
  ```java
  maxDataPoints = maxMemoryBytes / (AttributeDef.MAX_ATTRIBUTE_LENGTH * 10)
  maxDataPoints = 1GB / (72 * 10) = 1,073,741,824 / 720 ≈ 1,491,307 data points
  ```
- Data buffer size: 1,491,307 × 72 bytes = 107,374,104 bytes ≈ **102.4 MB per sensor**

#### 2. ChunkManager Memory (per sensor)
**ConcurrentHashMap Entry:**
- String key ("sensor-XXXXXX"): ~40-50 bytes
- ConcurrentHashMap overhead: ~32-48 bytes
- TimeChunk reference: 8 bytes
- **Total: ~80-100 bytes per sensor**

### Total Memory for 1 Million Sensors

#### Off-Heap Memory (Data Storage)
```
1,000,000 sensors × 102.4 MB per sensor = 102,400,000 MB = 102.4 TB
```

#### On-Heap Memory (Metadata)
```
1,000,000 sensors × (76 bytes + 90 bytes) = 166,000,000 bytes ≈ 158.4 MB
```

### Memory Summary

| Component | Per Sensor | Total (1M sensors) |
|-----------|------------|-------------------|
| Off-Heap Data Buffer | 102.4 MB | 102.4 TB |
| On-Heap TimeChunk Metadata | 76 bytes | 72.6 MB |
| On-Heap ConcurrentHashMap | 90 bytes | 85.8 MB |
| **Total** | **102.4 MB + 166 bytes** | **102.4 TB + 158.4 MB** |

## Current Configuration Issues

### Problem Analysis
1. **Massive Memory Requirement**: 102.4 TB is impractical for most systems
2. **Inefficient Memory Usage**: Each sensor gets full chunk capacity regardless of actual data volume
3. **No Sensor-Level Optimization**: All sensors treated equally regardless of data frequency

### Root Causes
1. **Fixed Chunk Capacity**: All chunks have same capacity (1.49M data points)
2. **No Data Volume Awareness**: Doesn't account for varying data rates per sensor
3. **No Compression**: Raw data storage without compression
4. **No Data Deduplication**: Duplicate values stored separately

## Optimization Strategies

### 1. Reduce Chunk Capacity (Immediate Impact)

**Current Configuration:**
```java
int maxChunks = 1000;
int chunkIntervalHours = 24;
long maxMemoryBytes = 1GB;
```

**Optimized Configuration:**
```java
int maxChunks = 100000; // More chunks, smaller capacity
int chunkIntervalHours = 24;
long maxMemoryBytes = 1GB;
```

**New Calculation:**
```java
maxDataPoints = 1GB / (72 * 10) = 1,491,307 data points per chunk
// But now distributed across 100,000 chunks instead of 1,000,000 sensors
```

**Memory Impact:**
- Active sensors (100,000): 100,000 × 102.4 MB = 10.24 TB
- Inactive sensors (900,000): Only metadata (~85.8 MB)
- **Total: 10.24 TB + 85.8 MB**

### 2. Dynamic Chunk Sizing (Recommended)

**Implementation:**
```java
public class AdaptiveChunkManager {
    private final Map<String, Integer> sensorDataRates;
    
    private int calculateChunkCapacity(String sensorId) {
        int dataRate = sensorDataRates.getOrDefault(sensorId, 100);
        // High-frequency sensors get larger chunks
        if (dataRate > 1000) {
            return 100000; // 100K data points
        } else if (dataRate > 100) {
            return 10000; // 10K data points  
        } else {
            return 1000; // 1K data points
        }
    }
}
```

**Memory Impact:**
- High-frequency sensors (10%): 100,000 × 7.2 MB = 720 GB
- Medium-frequency sensors (30%): 300,000 × 720 KB = 216 GB
- Low-frequency sensors (60%): 600,000 × 72 KB = 43.2 GB
- **Total: ~979.2 GB**

### 3. Data Compression (High Impact)

**Implementation:**
```java
public class CompressedTimeChunk {
    private ByteBuffer compressedBuffer;
    private CompressionAlgorithm algorithm;
    
    public void storeData(byte[] data) {
        byte[] compressed = compress(data);
        compressedBuffer.put(compressed);
    }
    
    private byte[] compress(byte[] data) {
        // Use Snappy, LZ4, or ZSTD for fast compression
        return compressor.compress(data);
    }
}
```

**Memory Impact (assuming 50% compression ratio):**
- Original: 979.2 GB
- Compressed: 489.6 GB
- **Savings: 489.6 GB (50% reduction)**

### 4. Sensor Grouping (Architectural Change)

**Implementation:**
```java
public class SensorGroupManager {
    private final Map<String, SensorGroup> groups;
    
    public void storeData(String sensorId, byte[] data) {
        String groupId = getGroupId(sensorId);
        SensorGroup group = groups.get(groupId);
        group.storeData(sensorId, data);
    }
    
    private String getGroupId(String sensorId) {
        // Group sensors by location, type, or data rate
        return sensorId.substring(0, 6); // First 6 characters
    }
}
```

**Memory Impact:**
- 1,000,000 sensors → 1,000 groups
- Each group manages 1,000 sensors
- Shared metadata and buffers
- **Estimated: 100-200 GB total**

### 5. Tiered Storage (Production Solution)

**Implementation:**
```java
public class TieredStorageManager {
    private final MemoryCache hotCache;    // Recent data
    private final DiskCache warmCache;     // Less recent data  
    private final Database coldStorage;   // Historical data
    
    public void storeData(String sensorId, byte[] data) {
        if (isRecent(data)) {
            hotCache.store(sensorId, data);
        } else if (isLessRecent(data)) {
            warmCache.store(sensorId, data);
        } else {
            coldStorage.store(sensorId, data);
        }
    }
}
```

**Memory Impact:**
- Hot cache (1% data, 1 day): 1.024 TB
- Warm cache (9% data, 1 week): 9.216 TB (disk)
- Cold storage (90% data, 3+ weeks): Database
- **RAM: 1.024 TB, Disk: 9.216 TB**

## Recommended Solution for 1 Million Sensors

### Phase 1: Immediate Optimizations (Current Architecture)
1. **Reduce chunk capacity**: 10K-100K data points per chunk
2. **Implement sensor grouping**: 1,000 sensors per group
3. **Add data compression**: 50% compression ratio

**Expected Memory: 200-500 GB**

### Phase 2: Architecture Improvements
1. **Dynamic chunk sizing**: Based on sensor data rate
2. **Tiered storage**: Hot/warm/cold data separation
3. **Data deduplication**: Remove duplicate values

**Expected Memory: 50-100 GB RAM + 1-2 TB disk**

### Phase 3: Advanced Optimizations
1. **Column-level compression**: Compress each attribute separately
2. **Delta encoding**: Store differences instead of absolute values
3. **Probabilistic data structures**: Bloom filters for existence checks

**Expected Memory: 10-50 GB RAM + 500 GB-1 TB disk**

## Configuration Recommendations

### For 1 Million Sensors

**Conservative Configuration:**
```java
int maxChunks = 100000;           // 100K active chunks
int chunkIntervalHours = 24;     // 1-day chunks
long maxMemoryBytes = 100L * 1024 * 1024 * 1024; // 100GB RAM
int maxDataPointsPerChunk = 10000; // 10K data points per chunk
```

**Memory Calculation:**
- Active sensors (100K): 100K × 720 KB = 72 GB
- Inactive sensors (900K): 900K × 166 bytes = 139.5 MB
- **Total: ~72.1 GB**

**Aggressive Configuration:**
```java
int maxChunks = 500000;           // 500K active chunks
int chunkIntervalHours = 24;     // 1-day chunks
long maxMemoryBytes = 50L * 1024 * 1024 * 1024; // 50GB RAM
int maxDataPointsPerChunk = 1000; // 1K data points per chunk
```

**Memory Calculation:**
- Active sensors (500K): 500K × 72 KB = 36 GB
- Inactive sensors (500K): 500K × 166 bytes = 77.5 MB
- **Total: ~36.1 GB**

## Conclusion

### Current Implementation
- **Memory Required**: 102.4 TB + 158.4 MB
- **Feasibility**: ❌ Not practical for most systems

### Optimized Implementation
- **Memory Required**: 36-72 GB RAM
- **Feasibility**: ✅ Practical for production systems

### Key Recommendations
1. **Reduce chunk capacity** from 1.49M to 1K-10K data points
2. **Implement sensor grouping** to share resources
3. **Add data compression** for 50% memory reduction
4. **Use tiered storage** for different data ages
5. **Monitor memory usage** and adjust configuration dynamically

The current implementation is not suitable for 1 million sensors without significant optimization. However, with the recommended changes, it can efficiently handle this scale with 36-72 GB of RAM.