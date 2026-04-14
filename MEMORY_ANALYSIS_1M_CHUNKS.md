# Memory Analysis: maxChunks = 1,000,000

## Configuration
```java
int maxChunks = 1,000,000;              // 1 million chunks
int chunkIntervalHours = 24;             // 1-day chunks
long maxMemoryBytes = 1GB;              // 1GB memory budget
int MAX_ATTRIBUTE_LENGTH = 64;         // 64 bytes per data point
```

## Chunk Capacity Calculation

### Per Chunk Data Points
```java
private int maxDataPointsPerChunk() {
    return (int) (maxMemoryBytes / (maxChunks * MAX_ATTRIBUTE_LENGTH));
    // = 1,073,741,824 / (1,000,000 * 64)
    // = 1,073,741,824 / 64,000,000
    // = 16.77 data points per chunk
}
```

**Result**: Each chunk can only hold **16-17 data points**

## Memory Breakdown

### Per Chunk Memory
```
16.77 data points × 64 bytes = 1,073 bytes ≈ 1 KB per chunk
```

### Total Memory
```
1,000,000 chunks × 1 KB = 1,000,000 bytes = 1 MB
```

**Wait, that's only 1 MB total!** 

## The Problem

### Issue 1: Extremely Small Chunk Capacity
- **16-17 data points per chunk** is very small
- For 1-day chunks with 1-minute sampling: 1,440 data points needed
- **Cache hit rate would be < 1%**

### Issue 2: Wasted Memory Budget
- **Allocated**: 1 GB
- **Actually used**: 1 MB
- **Wasted**: 999 MB (99.9%)

### Issue 3: On-Heap Memory Overhead
```java
// Per TimeChunk object (on-heap)
- chunkId (int): 4 bytes
- startTime (Instant): ~24 bytes
- endTime (Instant): ~24 bytes
- maxDataPoints (int): 4 bytes
- dataPointCount (int): 4 bytes
- timestamp (long): 8 bytes
- ByteBuffer reference: 8 bytes
- **Total**: ~76 bytes per chunk

// 1,000,000 chunks × 76 bytes = 76 MB on-heap
```

### Issue 4: ConcurrentHashMap Overhead
```java
// Per ConcurrentHashMap entry
- String key ("sensor-XXXXXX"): ~40-50 bytes
- ConcurrentHashMap overhead: ~32-48 bytes
- TimeChunk reference: 8 bytes
- **Total**: ~80-100 bytes per entry

// 1,000,000 entries × 90 bytes = 90 MB
```

## Total Memory for maxChunks = 1,000,000

| Component | Memory |
|-----------|--------|
| Off-heap data buffers | 1 MB |
| On-heap TimeChunk objects | 76 MB |
| ConcurrentHashMap overhead | 90 MB |
| **Total** | **167 MB** |

## Analysis

### Memory Efficiency
- **Budget**: 1 GB
- **Used**: 167 MB
- **Efficiency**: 16.7% (83.3% wasted)

### Cache Effectiveness
- **Chunk capacity**: 16-17 data points
- **Required for 1-day @ 1-min**: 1,440 data points
- **Coverage**: 1.2% of required data
- **Cache hit rate**: < 1%

### Practical Impact
- **Almost no data cached** per sensor
- **High cache miss rate** (99%+)
- **Minimal performance benefit**
- **Wasted memory budget**

## Better Configuration for 1 Million Sensors

### Option 1: Increase Memory Budget
```java
int maxChunks = 1,000,000;
long maxMemoryBytes = 64GB;  // 64GB instead of 1GB
```

**Result**:
- Per chunk: 1,024 data points
- Total: 64 GB
- Coverage: 71% of 1-day @ 1-min data

### Option 2: Reduce maxChunks (Recommended)
```java
int maxChunks = 100,000;      // 100K instead of 1M
long maxMemoryBytes = 10GB;   // 10GB budget
```

**Result**:
- Per chunk: 1,562 data points
- Total: 10 GB
- Coverage: 108% of 1-day @ 1-min data
- **10% of sensors active at any time**

### Option 3: Sensor Grouping
```java
int maxChunks = 10,000;       // 10K groups
int sensorsPerGroup = 100;    // 100 sensors per group
long maxMemoryBytes = 10GB;   // 10GB budget
```

**Result**:
- Per group: 15,625 data points
- Total: 10 GB
- Coverage: 1,085% of 1-day @ 1-min data
- **1% of groups active at any time**

## Recommendation

### For maxChunks = 1,000,000
**❌ Not recommended with 1GB budget**

**Why**:
- Only 16-17 data points per chunk
- 99% cache miss rate
- 83% memory budget wasted
- Minimal performance benefit

### Recommended Configuration
```java
int maxChunks = 100,000;      // 100K active chunks
long maxMemoryBytes = 10GB;   // 10GB budget
int maxDataPointsPerChunk = 1,562;  // 1.5K data points per chunk
```

**Benefits**:
- 1,562 data points per chunk
- 10% of sensors active at any time
- 108% coverage of 1-day @ 1-min data
- Efficient memory usage

## Conclusion

**maxChunks = 1,000,000 with 1GB budget** is **not practical** because:
- Each chunk gets only 16-17 data points
- Total memory used is only 167 MB (83% wasted)
- Cache hit rate would be < 1%
- Almost no performance benefit

**Better approach**: Use 100,000 chunks with 10GB budget for effective caching of 1 million sensors.