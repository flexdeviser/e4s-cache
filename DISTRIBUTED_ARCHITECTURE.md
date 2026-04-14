# Distributed System Architecture for e4s-cache

## Distributed System Overview

For a distributed e4-cache system with multiple services, we need to address:

1. **Service Discovery**: How services find each other
2. **Key Partitioning**: How sensor data is distributed across services
3. **Data Locality**: Whether same sensor data stays in same service
4. **Inter-Service Communication**: How services talk to each other
5. **Distributed Coordination**: How Redis is used for coordination

## 1. Service Discovery

### Options for Service Discovery

#### Option 1: Static Configuration (Simple)
```java
public class ServiceConfig {
    private static final List<String> PEER_ADDRESSES = Arrays.asList(
        "cache-server-1:9090",
        "cache-server-2:9090",
        "cache-server-3:9090"
    );
    
    public static String getPeerForSensor(String sensorId) {
        int index = Math.abs(sensorId.hashCode()) % PEER_ADDRESSES.size();
        return PEER_ADDRESSES.get(index);
    }
}
```

#### Option 2: Service Registry (Recommended)
```java
public class ServiceRegistry {
    private final Map<String, ServiceInstance> services = new ConcurrentHashMap<>();
    private final ZooKeeper zk;
    
    public void registerService(String serviceId, String host, int port) {
        try {
            String path = "/e4s-cache/services/" + serviceId;
            byte[] data = (host + ":" + port).getBytes();
            zk.create(path, data, CreateMode.EPHEMERAL);
        } catch (Exception e) {
            logger.error("Failed to register service", e);
        }
    }
    
    public List<ServiceInstance> discoverServices() {
        try {
            List<String> children = zk.getChildren("/e4s-cache/services");
            return children.stream()
                .map(this::parseServiceInstance)
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to discover services", e);
            return Collections.emptyList();
        }
    }
}
```

#### Option 3: gRPC Load Balancing
```java
public class CacheServiceClient {
    private final ManagedChannel channel;
    private final CacheServiceGrpc.CacheServiceBlockingStub stub;
    
    public CacheServiceClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
        this.stub = CacheServiceGrpc.newBlockingStub(channel);
    }
    
    public GetSeriesResponse getSeries(String sensorId, long startTime, long endTime, List<String> attributes) {
        GetSeriesRequest request = GetSeriesRequest.newBuilder()
            .setSensorId(sensorId)
            .setStartTime(startTime)
            .setEndTime(endTime)
            .addAllAttributes(attributes)
            .build();
        
        return stub.getSeries(request);
    }
}
```

## 2. Key Partitioning Strategies

### Strategy 1: Consistent Hashing (Recommended)

#### How It Works
```java
public class ConsistentHashPartitioner {
    private final List<ServiceInstance> ring;
    private final int VIRTUAL_NODES = 100; // Virtual nodes per service
    
    public ConsistentHashPartitioner(List<ServiceInstance> services) {
        this.ring = new ArrayList<>();
        for (ServiceInstance service : services) {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                ring.add(new VirtualNode(service, i));
            }
        }
        Collections.sort(ring);
    }
    
    public ServiceInstance getResponsibleService(String sensorId) {
        int hash = Math.abs(sensorId.hashCode());
        int index = hash % ring.size();
        return ring.get(index).getService();
    }
    
    public List<ServiceInstance> getReplicaServices(String sensorId) {
        int hash = Math.abs(sensorId.hashCode());
        int index = hash % ring.size();
        
        List<ServiceInstance> replicas = new ArrayList<>();
        for (int i = 0; i < 3; i++) { // Get 3 replicas
            int replicaIndex = (index + i) % ring.size();
            replicas.add(ring.get(replicaIndex).getService());
        }
        return replicas;
    }
}
```

#### Benefits
- **Uniform distribution**: Keys evenly distributed across services
- **Minimal rebalancing**: Adding/removing services only affects small portion of keys
- **High availability**: Multiple replicas for each key
- **Scalability**: Easy to add/remove services

### Strategy 2: Range-Based Partitioning

#### How It Works
```java
public class RangePartitioner {
    private final List<ServiceInstance> services;
    
    public RangePartitioner(List<ServiceInstance> services) {
        this.services = services;
    }
    
    public ServiceInstance getResponsibleService(String sensorId) {
        int hash = Math.abs(sensorId.hashCode());
        int index = hash % services.size();
        return services.get(index);
    }
    
    public ServiceInstance getResponsibleService(String sensorId, long timestamp) {
        // Time-based partitioning
        long timePartition = timestamp / (24 * 60 * 60 * 1000); // 1-day partitions
        int index = (int) (timePartition % services.size());
        return services.get(index);
    }
}
```

#### Benefits
- **Simple implementation**: Easy to understand and implement
- **Time-based**: Can partition by time for time-series data
- **Predictable**: Easy to determine which service has data

### Strategy 3: Geographic Partitioning

#### How It Works
```java
public class GeographicPartitioner {
    private final Map<String, ServiceInstance> regionMap;
    
    public GeographicPartitioner() {
        this.regionMap = new HashMap<>();
        regionMap.put("us-west", new ServiceInstance("cache-us-west", 9090));
        regionMap.put("us-east", new ServiceInstance("cache-us-east", 9090));
        regionMap.put("eu-west", new ServiceInstance("cache-eu-west", 9090));
    }
    
    public ServiceInstance getResponsibleService(String sensorId) {
        String region = extractRegionFromSensorId(sensorId);
        return regionMap.getOrDefault(region, regionMap.get("us-west"));
    }
    
    private String extractRegionFromSensorId(String sensorId) {
        // Extract region from sensor ID like "us-west-sensor-001"
        String[] parts = sensorId.split("-");
        return parts.length > 0 ? parts[0] : "us-west";
    }
}
```

#### Benefits
- **Data locality**: Data stored in same region as sensors
- **Low latency**: Reduced network latency
- **Compliance**: Data stays in same geographic region

## 3. Data Locality

### Current Implementation: Per-Service Chunks

#### How It Works
```java
public class DistributedChunkManager {
    private final String serviceId;
    private final ConsistentHashPartitioner partitioner;
    private final Map<String, ServiceInstance> serviceMap;
    private final CacheServiceClientPool clientPool;
    
    public DistributedChunkManager(String serviceId, List<ServiceInstance> services) {
        this.serviceId = serviceId;
        this.partitioner = new ConsistentHashPartitioner(services);
        this.serviceMap = services.stream()
            .collect(Collectors.toMap(ServiceInstance::getId, s -> s));
        this.clientPool = new CacheServiceClientPool(serviceMap);
    }
    
    public void storeData(String sensorId, byte[] attributeData) {
        ServiceInstance responsibleService = partitioner.getResponsibleService(sensorId);
        
        if (responsibleService.isLocal()) {
            // Store locally
            localChunkManager.storeData(sensorId, attributeData);
        } else {
            // Forward to responsible service
            clientPool.getClient(responsibleService).fillSeries(sensorId, 
                Collections.singletonList(createDataPoint(attributeData)));
        }
    }
    
    public byte[] getDataForAttribute(String sensorId, String attributeName, long currentTimeEpoch) {
        ServiceInstance responsibleService = partitioner.getResponsibleService(sensorId);
        
        if (responsibleService.isLocal()) {
            // Get locally
            return localChunkManager.getDataForAttribute(sensorId, attributeName, currentTimeEpoch);
        } else {
            // Fetch from responsible service
            return clientPool.getClient(responsibleService).getSeries(
                sensorId, currentTimeEpoch, currentTimeEpoch, 
                Collections.singletonList(attributeName));
        }
    }
}
```

### Data Locality Characteristics

| Strategy | Same Sensor Data Location | Benefits | Drawbacks |
|----------|---------------------------|----------|-----------|
| **Consistent Hashing** | Always in same service | Predictable, minimal rebalancing | May not be optimal for geographic distribution |
| **Range-Based** | May move between services | Time-based partitioning | Data may not be in optimal location |
| **Geographic** | Always in same region | Low latency, compliance | Requires region-aware sensor IDs |

## 4. Inter-Service Communication

### gRPC Communication

#### Service-to-Service Communication
```java
public class CacheServiceClientPool {
    private final Map<String, CacheServiceClient> clients;
    
    public CacheServiceClient getClient(ServiceInstance service) {
        return clients.computeIfAbsent(service.getId(), id -> 
            new CacheServiceClient(service.getHost(), service.getPort()));
    }
    
    public GetSeriesResponse getSeries(ServiceInstance service, String sensorId, 
                                       long startTime, long endTime, List<String> attributes) {
        return getClient(service).getSeries(sensorId, startTime, endTime, attributes);
    }
    
    public FillSeriesResponse fillSeries(ServiceInstance service, String sensorId, 
                                         List<DataPoint> dataPoints) {
        return getClient(service).fillSeries(sensorId, dataPoints);
    }
}
```

#### Service Discovery with gRPC
```java
public class CacheServiceDiscovery {
    private final Map<String, ServiceInstance> services = new ConcurrentHashMap<>();
    
    public void addService(ServiceInstance service) {
        services.put(service.getId(), service);
    }
    
    public void removeService(String serviceId) {
        services.remove(serviceId);
    }
    
    public List<ServiceInstance> getAllServices() {
        return new ArrayList<>(services.values());
    }
    
    public ServiceInstance getServiceById(String serviceId) {
        return services.get(serviceId);
    }
}
```

## 5. Distributed Coordination

### Redis for Distributed Locking

#### Current Implementation
```java
public class DistributedLockManager implements Lock {
    private final JedisPool jedisPool;
    private final String lockKey;
    private final String lockValue;
    
    public DistributedLockManager(JedisPool jedisPool, String sensorId) {
        this.jedisPool = jedisPool;
        this.lockKey = "cache_lock:" + sensorId;
        this.lockValue = Thread.currentThread().getName() + "_" + System.currentTimeMillis();
    }
    
    @Override
    public boolean tryLock() {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(lockKey, lockValue, 
                SetParams.setParams().nx().px(LOCK_LEASE_TIME * 1000));
            
            if ("OK".equals(result)) {
                locked = true;
                ownerThread = Thread.currentThread();
                startRenewalThread();
                return true;
            }
            return false;
        }
    }
}
```

#### Redis for Service Coordination
```java
public class ServiceCoordinator {
    private final JedisPool jedisPool;
    
    public void registerService(String serviceId, String host, int port) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "service:" + serviceId;
            String value = host + ":" + port;
            jedis.setex(key, 30, value); // 30 second TTL
        }
    }
    
    public List<ServiceInstance> discoverServices() {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys("service:*");
            return keys.stream()
                .map(key -> {
                    String value = jedis.get(key);
                    String[] parts = value.split(":");
                    return new ServiceInstance(key, parts[0], Integer.parseInt(parts[1]));
                })
                .collect(Collectors.toList());
        }
    }
    
    public void broadcastInvalidate(String sensorId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "invalidate:" + sensorId;
            jedis.publish(key, sensorId);
        }
    }
    
    public void subscribeInvalidations(Consumer<String> callback) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.subscribe(new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    callback.accept(message);
                }
            }, "invalidate:*");
        }
    }
}
```

## 6. Complete Distributed Architecture

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Load Balancer (Optional)                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────────────────────────┐
        │              Service Discovery Layer                      │
        │  - Service Registry (ZooKeeper/Consul)                 │
        │  - gRPC Service Discovery                           │
        │  - Health Checks                                      │
        └─────────────────────────────────────────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────────────────────────┐
        │              Consistent Hashing Layer                    │
        │  - 100 virtual nodes per service                     │
        │  - Ring topology for key distribution               │
        │  - Automatic rebalancing on service changes            │
        └─────────────────────────────────────────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────────────────────────┐
        │              Cache Service Instances                      │
        │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
        │  │ Service 1   │  │ Service 2   │  │ Service 3   │  │
        │  │ Port: 9090 │  │ Port: 9091 │  │ Port: 9092 │  │
        │  │ Host: ...   │  │ Host: ...   │  │ Host: ...   │  │
        │  └─────────────┘  └─────────────┘  └─────────────┘  │
        └─────────────────────────────────────────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────────────────────────┐
        │              Redis Cluster (Distributed Coordination)         │
        │  - Distributed locking                                 │
        │  - Service discovery                                   │
        │  - Cache invalidation                                 │
        │  - Health monitoring                                   │
        └─────────────────────────────────────────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────────────────────────┐
        │              Primary Database                            │
        │  - Single source of truth                              │
        │  - Read-through cache pattern                         │
        └─────────────────────────────────────────────────────────────┘
```

## 7. Implementation Examples

### Example 1: Consistent Hashing with 2 Services

#### Service 1 Configuration
```java
public class Service1Main {
    public static void main(String[] args) {
        String serviceId = "cache-service-1";
        String host = "192.168.1.10";
        int port = 9090;
        
        List<ServiceInstance> services = Arrays.asList(
            new ServiceInstance("cache-service-1", "192.168.1.10", 9090),
            new ServiceInstance("cache-service-2", "192.168.1.11", 9090)
        );
        
        ConsistentHashPartitioner partitioner = new ConsistentHashPartitioner(services);
        DistributedChunkManager chunkManager = new DistributedChunkManager(
            serviceId, services, partitioner);
        
        CacheServer server = new CacheServer(port, 2_000_000, 24, 100L * 1024 * 1024 * 1024, 
            "localhost", 6379);
        
        server.start();
    }
}
```

#### Service 2 Configuration
```java
public class Service2Main {
    public static void main(String[] args) {
        String serviceId = "cache-service-2";
        String host = "192.168.1.11";
        int port = 9090;
        
        List<ServiceInstance> services = Arrays.asList(
            new ServiceInstance("cache-service-1", "192.168.1.10", 9090),
            new ServiceInstance("cache-service-2", "192.168.1.11", 9090)
        );
        
        ConsistentHashPartitioner partitioner = new ConsistentHashPartitioner(services);
        DistributedChunkManager chunkManager = new DistributedChunkManager(
            serviceId, services, partitioner);
        
        CacheServer server = new CacheServer(port, 2_000_000, 24, 100L * 1024 * 1024 * 1024, 
            "localhost", 6379);
        
        server.start();
    }
}
```

### Example 2: Service Discovery with ZooKeeper

#### Service Registration
```java
public class ServiceRegistration {
    private final ZooKeeper zk;
    
    public ServiceRegistration(String zkHosts) throws Exception {
        this.zk = new ZooKeeper(zkHosts, 3000, new ServiceWatcher());
    }
    
    public void registerService(String serviceId, String host, int port) {
        try {
            String path = "/e4s-cache/services/" + serviceId;
            byte[] data = (host + ":" + port).getBytes();
            
            zk.create(path, data, CreateMode.EPHEMERAL);
            
            zk.exists(path + "/ready", new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getType() == Event.EventType.NodeDeleted) {
                        // Service went down, handle failure
                        handleServiceFailure(serviceId);
                    }
                }
            });
            
            logger.info("Registered service: {}", serviceId);
        } catch (Exception e) {
            logger.error("Failed to register service", e);
        }
    }
}
```

### Example 3: Inter-Service Communication

#### Forwarding Requests
```java
public class DistributedCacheServiceImpl extends CacheServiceGrpc.CacheServiceImplBase {
    private final String serviceId;
    private final ConsistentHashPartitioner partitioner;
    private final CacheServiceClientPool clientPool;
    private final ThreadSafeCompressedChunkManager localChunkManager;
    
    @Override
    public void getSeries(GetSeriesRequest request, StreamObserver<GetSeriesResponse> responseObserver) {
        String sensorId = request.getSensorId();
        
        ServiceInstance responsibleService = partitioner.getResponsibleService(sensorId);
        
        if (responsibleService.isLocal()) {
            // Handle locally
            handleLocalGetSeries(request, responseObserver);
        } else {
            // Forward to responsible service
            handleRemoteGetSeries(responsibleService, request, responseObserver);
        }
    }
    
    private void handleLocalGetSeries(GetSeriesRequest request, StreamObserver<GetSeriesResponse> responseObserver) {
        // Use local chunk manager
        byte[] rawData = localChunkManager.getDataForAttribute(
            request.getSensorId(), 
            request.getAttributesList().get(0),
            request.getEndTime()
        );
        
        // Process and return response
        GetSeriesResponse response = GetSeriesResponse.newBuilder()
            .setSuccess(rawData != null)
            .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    private void handleRemoteGetSeries(ServiceInstance service, GetSeriesRequest request, 
                                       StreamObserver<GetSeriesResponse> responseObserver) {
        try {
            GetSeriesResponse response = clientPool.getClient(service).getSeries(
                request.getSensorId(),
                request.getStartTime(),
                request.getEndTime(),
                request.getAttributesList()
            );
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Failed to forward request to service: {}", service.getId(), e);
            responseObserver.onNext(GetSeriesResponse.newBuilder()
                .setSuccess(false)
                .setError(e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }
}
```

## 8. Data Locality Analysis

### Question: Will same sensor data always sit in the same service?

### Answer: YES with Consistent Hashing

#### How It Works
```java
// Sensor ID: "sensor-001"
// Hash: "sensor-001".hashCode() = 123456789
// Ring size: 200 (100 virtual nodes × 2 services)
// Index: 123456789 % 200 = 89
// Service: ring[89].getService() = "cache-service-1"

// Sensor ID: "sensor-002"
// Hash: "sensor-002".hashCode() = 987654321
// Ring size: 200 (100 virtual nodes × 2 services)
// Index: 987654321 % 200 = 121
// Service: ring[121].getService() = "cache-service-2"
```

#### Benefits
- **Predictable**: Same sensor always goes to same service
- **Efficient**: No need to search across services
- **Low latency**: Direct routing to responsible service
- **Cache locality**: Data stays in same service for better performance

### Data Locality Guarantees

| Scenario | Data Location | Guarantee |
|----------|--------------|-----------|
| **Same sensor, same time** | Always same service | ✅ Guaranteed |
| **Same sensor, different time** | May move to different service | ⚠️ Time-based |
| **Different sensors** | Distributed across services | ✅ Distributed |
| **Service failure** | Data moves to replica service | ✅ High availability |

## 9. Configuration Examples

### 2-Service Configuration

#### Service 1
```java
public class Service1Config {
    public static void main(String[][] args) {
        String serviceId = "cache-service-1";
        String host = "192.168.1.10";
        int port = 9090;
        
        List<ServiceInstance> services = Arrays.asList(
            new ServiceInstance("cache-service-1", "192.168.1.10", 9090),
            new ServiceInstance("cache-service-2", "192.168.1.11", 9090)
        );
        
        int maxChunks = 1_000_000;              // 1M chunks per service
        long maxMemoryBytes = 50GB;            // 50GB per service
        int chunkIntervalHours = 24;             // 1-day chunks
        
        CacheServer server = new CacheServer(port, maxChunks, chunkIntervalHours, maxMemoryBytes, 
            "localhost", 6379);
        
        server.start();
    }
}
```

#### Service 2
```java
public class Service2Config {
    public static void main(String[][] args) {
        String serviceId = "cache-service-2";
        String host = "192.168.1.11";
        int port = 9100;
        
        List<ServiceInstance> services = Arrays.asList(
            new ServiceInstance("cache-service-1", "192.168.1.10", 9090),
            new ServiceInstance("cache-service-2", "192.168.1.11", 9100)
        );
        
        int maxChunks = 1_000_000;              // 1M chunks per service
        long maxMemoryBytes = 50GB;            // 50GB per service
        int chunkIntervalHours = 24;             // 1-day chunks
        
        CacheServer server = new CacheServer(port, maxChunks, chunkIntervalHours, maxMemoryBytes, 
            "localhost", 6379);
        
        server.start();
    }
```

## 10. Summary

### How Services Talk to Each Other

1. **gRPC Communication**: Services communicate via gRPC
2. **Service Discovery**: Services discover each other via ZooKeeper/Consul
3. **Load Balancing**: Consistent hashing determines routing
4. **Request Forwarding**: Services forward requests to responsible service

### How Keys Are Split

1. **Consistent Hashing**: Keys distributed using hash ring
2. **Virtual Nodes**: 100 virtual nodes per service for even distribution
3. **Ring Topology**: Keys mapped to services based on hash
4. **Automatic Rebalancing**: Keys redistributed on service changes

### Data Locality

**YES** - Same sensor data will always sit in the same service with consistent hashing:
- **Predictable routing**: Same sensor always goes to same service
- **Efficient access**: No need to search across services
- **Low latency**: Direct routing to responsible service
- **High availability**: Replica services provide backup

### Total Capacity with 2 Services

| Configuration | Per Service | Total | Sensors Supported |
|--------------|-------------|-------|------------------|
| 1M chunks, 50GB each | 50 GB | 100 GB | 2 million sensors |
| 2M chunks, 50GB each | 100 GB | 200 GB | 4 million sensors |

The distributed architecture provides excellent scalability, high availability, and predictable data locality for large-scale sensor deployments.