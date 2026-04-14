# e4s-cache

High-performance distributed time-series cache based on chunk-based columnar architecture with off-heap memory management.

## Architecture

### Core Components

1. **ChunkManager**: Manages time-based chunked data storage with sliding window eviction
2. **DistributedLockManager**: Implements distributed locking to prevent cache stampedes
3. **ChunkManager**: Handles 3-week sliding window with O(1) eviction
4. **CacheServiceImpl**: gRPC service implementation
5. **CacheBackEnd**: Abstract interface for data source operations

### Key Features

- **Chunk-Based Columnar Storage**: Data organized in time-based chunks (1-day intervals) with columnar layout for optimal CPU cache utilization
- **Read-Through Pattern**: Automatic cache population from primary database on cache misses
- **Distributed Locking**: Prevents cache stampedes using Redis-based distributed locks
- **Off-Heap Memory Management**: Uses direct buffers to minimize GC pressure
- **AP Consistency Model**: Prioritizes availability and partition tolerance with eventual consistency
- **3-Week Sliding Window**: Automated memory management with strict TTL

## Building

```bash
mvn clean package
```

## Running the Server

```bash
java -jar target/e4s-cache-1.0.0.jar
```

## Running the Client

```bash
java -cp target/e4s-cache-1.0.0.jar com.e4s.cache.client.CacheClient
```

## Configuration

Default configuration (can be modified in CacheServer main method):
- Port: 9090
- Max Chunks: 1000
- Chunk Interval: 1 hour
- Memory Budget: 1GB
- Redis Host: localhost:6379

## API Operations

### GetSeries
Retrieves time-series data for specified attributes:

```protobuf
GetSeriesRequest {
    string sensor_id
    int64 start_time
    int64 end_time
    repeated string attributes
}
```

### FillSeries
Fills cache with new data points:

```protobuf
FillSeriesRequest {
    string sensor_id
    repeated DataPoint data_points
}
```

## Design Principles

1. **AP Consistency**: Eventual consistency model with distributed coordination
2. **Read-Through Pattern**: Single source of truth with intelligent caching
3. **Columnar Storage**: Optimized for sequential scan operations
4. **Off-Heap Memory**: Minimized GC overhead with direct buffers
5. **Distributed Locking**: Prevents cache stampede during read-through misses

## Testing

```bash
mvn test
```

## License

MIT License