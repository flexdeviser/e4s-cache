# Memory Usage Analysis - CORRECTED CALCULATION

## Current Implementation Memory Calculation

### Configuration Parameters
```java
int maxChunks = 1000;                    // Maximum active chunks
int chunkIntervalHours = 24;             // 1-day chunks
long maxMemoryBytes = 1GB;              // Total memory budget: 1,073,741,824 bytes
int MAX_ATTRIBUTE_LENGTH = 64;          // Size per data point
```

### Current (Incorrect) Calculation
```java
private int maxDataPointsPerChunk() {
    return (int) (maxMemoryBytes / (AttributeDef.MAX_ATTRIBUTE_LENGTH * 10));
    // = 1,073,741,824 / (64 * 10) 
    // = 1,073,741,824 / 640 
    // = 1,677,721 data points per chunk
}
```

**Problem**: This calculation assumes each chunk gets the entire memory budget!

### Memory Per Chunk (Current Implementation)
```
Buffer size = 1,677,721 data points × 64 bytes = 107,374,144 bytes = 102.4 MB per chunk
```

### Total Memory for 1,000 Chunks
```
1,000 chunks × 102.4 MB = 102.4 GB
```

**❌ This exceeds the 1GB memory budget by 102x!**

## Correct Calculation

### Proper Memory Distribution
```java
private int maxDataPointsPerChunk() {
    return (int) (maxMemoryBytes / (maxChunks * AttributeDef.MAX_ATTRIBUTE_LENGTH));
    // = 1,073,741,824 / (1000 * 64)
    // = 1,073,741,824 / 64,000
    // = 16,777 data points per chunk
}
```

### Memory Per Chunk (Corrected)
```
Buffer size = 16,777 data points × 64 bytes = 1,073,728 bytes = 1.0 MB per chunk
```

### Total Memory for 1,000 Chunks
```
1,000 chunks × 1.0 MB = 1,000 MB = 1 GB ✅
```

## Memory for 1 Million Sensors

### Current Implementation (with maxChunks = 1000)
**Active Sensors (1,000)**: 1,000 × 1.0 MB = 1 GB
**Inactive Sensors (999,000)**: 999,000 × 166 bytes = 159 MB
**Total**: 1 GB + 159 MB = 1.16 GB

### But There's a Problem!
With only 1,000 chunks available for 1,000,000 sensors:
- **Only 0.1% of sensors** can have active chunks at any time
- **99.9% of sensors** will experience cache misses
- **High cache miss rate** defeats the purpose of caching

## Realistic Memory Requirements for 1 Million Sensors

### Option 1: Increase maxChunks (Memory-Intensive)
```java
int maxChunks = 1,000,000;              // One chunk per sensor
int maxDataPointsPerChunk = 1,677;     // Reduced capacity
```

**Memory Calculation**:
- 1,000,000 chunks × 1,677 data points × 64 bytes = 107.4 GB
- **Total**: ~107.4 GB

### Option 2: Sensor Grouping (Recommended)
```java
int maxChunks = 10,000;                // 10,000 groups
int sensorsPerGroup = 100;              // 100 sensors per group
int maxDataPointsPerChunk = 16,777;    // 16K data points per group
```

**Memory Calculation**:
- 10,000 groups × 16,777 data points × 64 bytes = 10.7 GB
- **Total**: ~10.7 GB

### Option 3: Dynamic Chunk Sizing (Optimal)
```java
int maxChunks = 100,000;               // 100K active chunks
int maxDataPointsPerChunk = 10,000;    // 10K data points per chunk
```

**Memory Calculation**:
- 100,000 chunks × 10,000 data points × 64 bytes = 64 GB
- **Total**: ~64 GB

## Recommended Configuration for 1 Million Sensors

### Conservative Approach (10 GB RAM)
```java
int maxChunks = 10,000;                // 10K active chunks
int chunkIntervalHours = 24;           // 1-day chunks
long maxMemoryBytes = 10GB;            // 10GB memory budget
int maxDataPointsPerChunk = 16,777;   // 16K data points per chunk
```

**Memory**: 10.7 GB
**Coverage**: 1% of sensors active at any time

### Balanced Approach (50 GB RAM)
```java
int maxChunks = 50,000;                // 50K active chunks
int chunkIntervalHours = 24;           // 1-day chunks
long maxMemoryBytes = 50GB;            // 50GB memory budget
int maxDataPointsPerChunk = 16,777;   // 16K data points per chunk
```

**Memory**: 53.4 GB
**Coverage**: 5% of sensors active at any time

### Aggressive Approach (100 GB RAM)
```java
int maxChunks = 100,000;               // 100K active chunks
int chunkIntervalHours = 24;           // 1-day chunks
long maxMemoryBytes = 100GB;           // 100GB memory budget
int maxDataPointsPerChunk = 16,777;   // 16K data points per chunk
```

**Memory**: 106.8 GB
**Coverage**: 10% of sensors active at any time

## Implementation Fix Required

### Current Bug in ChunkManager
```java
private int maxDataPointsPerChunk() {
    // ❌ WRONG: Uses entire memory budget per chunk
    return (int) (maxMemoryBytes / (AttributeDef.MAX_ATTRIBUTE_LENGTH * 10));
}
```

### Corrected Implementation
```java
private int maxDataPointsPerChunk() {
    // ✅ CORRECT: Distributes memory budget across all chunks
    return (int) (maxMemoryBytes / (maxChunks * AttributeDef.MAX_ATTRIBUTE_LENGTH));
}
```

## Summary

### Current Implementation Issues
1. **Memory Calculation Bug**: Allocates 102.4 MB per chunk instead of 1 MB
2. **Insufficient Chunks**: Only 1,000 chunks for 1,000,000 sensors
3. **Poor Coverage**: Only 0.1% of sensors can be cached at once

### Corrected Memory Requirements
- **Per Chunk**: 1 MB (not 102.4 MB)
- **For 1,000 Chunks**: 1 GB total
- **For 1 Million Sensors**: 10-100 GB (depending on configuration)

### Recommendation
Fix the `maxDataPointsPerChunk()` calculation and increase `maxChunks` to at least 10,000-100,000 for 1 million sensors.