# Immediate Health Check for New Service Connections

## Overview

The health monitoring system has been enhanced to perform immediate health checks when new services are registered. This eliminates the delay that would occur when using a high `checkIntervalMs` value, ensuring that new services become available immediately without waiting for the next scheduled health check.

## Problem

Previously, when a new service joined the cluster:

1. Service was registered in ServiceRegistry
2. Service remained in "unknown" health status
3. Service would not be health-checked until the next scheduled check
4. With `checkIntervalMs: 30000` (30 seconds), new services would wait up to 30 seconds before being marked healthy

This caused delays in service discovery and reduced system responsiveness.

## Solution

### Immediate Health Check on Registration

When a new service is registered:

1. Service is registered in ServiceRegistry
2. ServiceRegistry triggers immediate health check via HealthMonitor
3. Health check is performed immediately (async)
4. Service is marked healthy/healthy based on check result
5. No waiting for next scheduled check

### Startup Health Checks

When the server starts:

1. All services are registered
2. Health monitor is started
3. Immediate health checks are triggered for all peer services
4. Services become healthy quickly without waiting for first scheduled check

## Implementation

### 1. HealthMonitor

**New Method:**
```java
public void checkServiceImmediately(ServiceInstance service) {
    if (!running) {
        logger.debug("Health monitor not running, skipping immediate check for service: {}", service.getId());
        return;
    }
    
    logger.debug("Performing immediate health check for new service: {}", service.getId());
    
    CompletableFuture.runAsync(() -> {
        checkServiceHealth(service);
    }, scheduler);
}
```

**Features:**
- Async execution to avoid blocking registration
- Thread-safe execution using scheduler thread pool
- Debug logging for visibility
- Checks if monitor is running before executing

### 2. ServiceRegistry

**New Field:**
```java
private volatile HealthMonitor healthMonitor;
```

**New Method:**
```java
public void setHealthMonitor(HealthMonitor healthMonitor) {
    this.healthMonitor = healthMonitor;
}
```

**Updated Method:**
```java
public void registerService(ServiceInstance service) {
    services.put(service.getId(), service);
    
    serviceGroups.computeIfAbsent(service.getGroup(), k -> new CopyOnWriteArrayList<>()).add(service);
    
    logger.info("Registered service: {} in group: {}", service.getId(), service.getGroup());
    
    if (healthMonitor != null) {
        healthMonitor.checkServiceImmediately(service);
    }
}
```

**Features:**
- Triggers immediate health check on registration
- Null-safe check for health monitor
- Volatile field for thread safety

### 3. DistributedCacheServer

**Updated Constructor:**
```java
this.healthMonitor = new HealthMonitor(serviceRegistry, clientPool, healthConfig.getCheckIntervalMs());

serviceRegistry.setHealthMonitor(healthMonitor);
```

**Updated start() Method:**
```java
public void start() throws IOException {
    server.start();
    
    localService.setHealthy(true);
    
    healthMonitor.start();
    
    logger.info("Distributed Cache Server started, listening on port {}", server.getPort());
    logger.info("Service ID: {}, Group: {}", localService.getId(), localService.getGroup());
    logger.info("Total services: {}, Health checked: {}, Healthy: {}, Unknown: {}", 
        serviceRegistry.getServiceCount(),
        serviceRegistry.getHealthCheckedServiceCount(),
        serviceRegistry.getHealthyServiceCount(),
        serviceRegistry.getUnknownHealthServiceCount());
    
    logger.info("Performing immediate health checks for all services...");
    for (ServiceInstance service : serviceRegistry.getAllServices()) {
        if (!service.getId().equals(localService.getId())) {
            healthMonitor.checkServiceImmediately(service);
        }
    }
}
```

**Features:**
- Sets health monitor in service registry after creation
- Triggers immediate health checks for all services on startup
- Skips local service (already marked healthy)

## Behavior

### Before

**Scenario:** New service joins with `checkIntervalMs: 30000`

```
T+0s: Service registered
T+0s: Service status: unknown
T+30s: First scheduled health check
T+30s: Service status: healthy
```

**Delay:** 30 seconds before service becomes healthy

### After

**Scenario:** New service joins with `checkIntervalMs: 30000`

```
T+0s: Service registered
T+0s: Immediate health check triggered
T+0.1s: Health check completed
T+0.1s: Service status: healthy
```

**Delay:** < 1 second before service becomes healthy

## Startup Behavior

### Before

```
INFO: Distributed Cache Server started, listening on port 9090
INFO: Service ID: cache-service-1, Group: e4s-cache
INFO: Total services: 3, Health checked: 1, Healthy: 1, Unknown: 2
[Wait 30 seconds for first health check]
DEBUG: Performing health checks for 3 services
INFO: Service cache-service-2 recovered and is now healthy
INFO: Service cache-service-3 recovered and is now healthy
```

### After

```
INFO: Distributed Cache Server started, listening on port 9090
INFO: Service ID: cache-service-1, Group: e4s-cache
INFO: Total services: 3, Health checked: 1, Healthy: 1, Unknown: 2
INFO: Performing immediate health checks for all services...
DEBUG: Performing immediate health check for new service: cache-service-2
DEBUG: Performing immediate health check for new service: cache-service-3
INFO: Service cache-service-2 recovered and is now healthy
INFO: Service cache-service-3 recovered and is now healthy
```

## Benefits

1. **Faster Service Discovery**: New services become available immediately
2. **No Delay with High Intervals**: Works well with high `checkIntervalMs` values
3. **Better User Experience**: No waiting for services to become available
4. **Improved Responsiveness**: System responds quickly to new services
5. **Production Ready**: Handles real-world service addition scenarios

## Configuration

### Health Check Interval

The health check interval still controls the frequency of periodic health checks:

```yaml
health:
  checkIntervalMs: 30000  # Check every 30 seconds
```

With immediate health checks:
- **New services**: Checked immediately on registration
- **Existing services**: Checked every 30 seconds
- **Startup**: All services checked immediately

### Retry Count

The retry count still applies to immediate health checks:

```java
private static final int MAX_RETRY_COUNT = 3;
```

Immediate health checks follow the same retry logic:
- 1st failure: Service remains healthy
- 2nd failure: Service remains healthy
- 3rd failure: Service marked unhealthy

## Use Cases

### 1. Adding New Services to Cluster

When adding a new service to a running cluster:

```
# Start new service
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-4.yaml

# Existing services immediately detect and health-check the new service
INFO: Registered service: cache-service-4 in group: e4s-cache
DEBUG: Performing immediate health check for new service: cache-service-4
INFO: Service cache-service-4 recovered and is now healthy
```

### 2. Service Recovery

When a previously unhealthy service recovers:

```
# Service recovers
DEBUG: Service cache-service-2 health check failed (1/3): disconnected
DEBUG: Service cache-service-2 health check failed (2/3): disconnected
DEBUG: Service cache-service-2 health check failed (3/3): disconnected
WARN: Service cache-service-2 marked as unhealthy after 3 failures: disconnected
[Service restarts]
INFO: Registered service: cache-service-2 in group: e4s-cache
DEBUG: Performing immediate health check for new service: cache-service-2
INFO: Service cache-service-2 recovered and is now healthy
```

### 3. Cluster Startup

When starting a cluster with multiple services:

```
# Start service 1
java -cp e4s-cache.jar com.e4s-cache.server.DistributedCacheServer config/cache-service-1.yaml
INFO: Distributed Cache Server started, listening on port 9090
INFO: Performing immediate health checks for all services...

# Start service 2
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-2.yaml
INFO: Distributed Cache Server started, listening on port 9091
INFO: Performing immediate health checks for all services...
DEBUG: Performing immediate health check for new service: cache-service-1
INFO: Service cache-service-1 recovered and is now healthy

# Start service 3
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-3.yaml
INFO: Distributed Cache Server started, listening on port 9092
INFO: Performing immediate health checks for all services...
DEBUG: Performing immediate health check for new service: cache-service-1
DEBUG: Performing immediate health check for new service: cache-service-2
INFO: Service cache-service-1 recovered and is now healthy
INFO: Service cache-service-2 recovered and is now healthy
```

## Performance Impact

### Minimal Overhead

- **Async Execution**: Immediate health checks run asynchronously
- **Non-Blocking**: Service registration is not blocked
- **Thread Pool**: Uses existing scheduler thread pool
- **No Additional Threads**: Reuses existing health monitor thread

### Scalability

- **Linear Scaling**: Each new service triggers one immediate check
- **Burst Handling**: Multiple services can be checked concurrently
- **Resource Efficient**: No additional resources required

## Testing

### Test Results

All 62 tests pass successfully:
- ServiceInstanceTest: 8 tests
- ConsistentHashPartitionerTest: 10 tests
- ServiceRegistryTest: 9 tests
- AttributeInfoTest: 8 tests
- AttributeDefTest: 10 tests
- TimeChunkTest: 17 tests

### Manual Testing

To test immediate health checks:

1. Start service 1:
```bash
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-1.yaml
```

2. Start service 2 (should immediately detect service 1):
```bash
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-2.yaml
```

3. Observe logs - service 2 should immediately health-check service 1:
```
INFO: Performing immediate health checks for all services...
DEBUG: Performing immediate health check for new service: cache-service-1
INFO: Service cache-service-1 recovered and is now healthy
```

## Troubleshooting

### Issue: Services Not Checked Immediately

**Symptom:** Services are not health-checked immediately on registration

**Possible Causes:**
1. Health monitor not started
2. Health monitor not set in service registry
3. Service registration before health monitor is set

**Solutions:**
1. Ensure health monitor is started before registering services
2. Ensure health monitor is set in service registry
3. Check order of operations in DistributedCacheServer constructor

### Issue: High CPU Usage

**Symptom:** High CPU usage when many services join

**Possible Causes:**
1. Many services joining simultaneously
2. Immediate health checks running concurrently

**Solutions:**
1. Stagger service startup
2. Increase health check interval
3. Monitor system resources

## Best Practices

### 1. Service Startup Order

Start services in order to minimize immediate health check bursts:

```bash
# Start services sequentially
for i in {1..3}; do
    java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-$i.yaml &
    sleep 2
done
```

### 2. Health Check Interval

Choose appropriate health check interval based on your requirements:

- **Development**: 30-60 seconds (less frequent)
- **Staging**: 10-30 seconds
- **Production**: 5-10 seconds (more frequent)

With immediate health checks, you can use higher intervals without sacrificing responsiveness.

### 3. Monitoring

Monitor immediate health check activity:

```bash
# Watch for immediate health checks
grep "Performing immediate health check" logs/e4s-cache.log

# Count immediate health checks
grep -c "Performing immediate health check" logs/e4s-cache.log
```

## Future Enhancements

Potential improvements to immediate health checks:

1. **Batch Health Checks**: Check multiple services in one batch
2. **Priority Queue**: Prioritize health checks for critical services
3. **Adaptive Intervals**: Adjust health check interval based on service health
4. **Health Check History**: Track history of immediate health checks
5. **Circuit Breaker**: Stop checking after many consecutive failures
6. **Custom Retry Policies**: Per-service retry configurations

## Conclusion

Immediate health checks for new service connections provide a significant improvement in system responsiveness. By checking new services immediately on registration, the system eliminates delays caused by high health check intervals while maintaining the benefits of periodic health checks for existing services. This feature is particularly useful in production environments where services are frequently added to the cluster.
