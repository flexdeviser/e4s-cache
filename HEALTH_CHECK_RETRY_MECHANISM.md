# Health Check Retry Mechanism

## Overview

The health monitoring system has been enhanced with a retry mechanism to avoid false positives from temporary network issues. Services are now marked as unhealthy only after 3 consecutive health check failures.

## Changes Made

### 1. ServiceInstance

**New Fields:**
- `consecutiveFailures` - Atomic counter for tracking consecutive failures

**New Methods:**
- `getConsecutiveFailures()` - Get current failure count
- `incrementConsecutiveFailures()` - Increment failure counter
- `resetConsecutiveFailures()` - Reset failure counter

**Behavior Changes:**
- `setHealthy(true)` now automatically resets failure counter
- Initial health status is `unknown` (not `healthy`)

### 2. HealthMonitor

**New Configuration:**
- `MAX_RETRY_COUNT = 3` - Maximum retry attempts before marking unhealthy

**Enhanced Health Check Logic:**
- Tracks consecutive failures for each service
- Only marks service as unhealthy after 3 consecutive failures
- Provides debug logging for retry attempts (1/3, 2/3, 3/3)
- Resets failure counter when service becomes healthy

### 3. CacheServiceClient

**Improved Error Messages:**
- Changed from "Service unavailable: UNAVAILABLE: io exception" to "offline"
- Changed from technical gRPC errors to user-friendly status codes
- Better logging for connection failures

## Behavior

### Before

```
WARN: Service cache-service-2 marked as unhealthy: Service unavailable: UNAVAILABLE: io exception
```

Services were marked unhealthy immediately on first failure.

### After

```
DEBUG: Service cache-service-2 health check failed (1/3): disconnected
DEBUG: Service cache-service-2 health check failed (2/3): disconnected
DEBUG: Service cache-service-2 health check failed (3/3): disconnected
WARN: Service cache-service-2 marked as unhealthy after 3 failures: disconnected
```

Services are marked unhealthy only after 3 consecutive failures.

## Retry Logic

### Failure Scenarios

1. **First Failure (1/3)**
   - Service remains healthy
   - Debug log: "Service X health check failed (1/3): disconnected"
   - Failure counter: 1

2. **Second Failure (2/3)**
   - Service remains healthy
   - Debug log: "Service X health check failed (2/3): disconnected"
   - Failure counter: 2

3. **Third Failure (3/3)**
   - Service marked as unhealthy
   - Warning log: "Service X marked as unhealthy after 3 failures: disconnected"
   - Failure counter: 3

4. **Recovery**
   - Service marked as healthy
   - Info log: "Service X recovered and is now healthy"
   - Failure counter: 0

### Temporary Network Issues

If a service has temporary network issues:

```
DEBUG: Service cache-service-2 health check failed (1/3): disconnected
DEBUG: Service cache-service-2 health check failed (2/3): disconnected
INFO: Service cache-service-2 recovered and is now healthy
```

The service recovers after 2 failures and is never marked unhealthy.

### Persistent Failures

If a service is truly offline:

```
DEBUG: Service cache-service-2 health check failed (1/3): disconnected
DEBUG: Service cache-service-2 health check failed (2/3): disconnected
DEBUG: Service cache-service-2 health check failed (3/3): disconnected
WARN: Service cache-service-2 marked as unhealthy after 3 failures: disconnected
DEBUG: Service cache-service-2 still unhealthy (4 failures): disconnected
DEBUG: Service cache-service-2 still unhealthy (5 failures): disconnected
```

The service is marked unhealthy after 3 failures and continues to be monitored.

## Error Messages

### Improved Status Codes

| Old Status | New Status | Description |
|------------|------------|-------------|
| "Service unavailable: UNAVAILABLE: io exception" | "offline" | Service is not reachable |
| "Service unavailable: UNAVAILABLE: ...other error" | "error: CODE" | Service error with gRPC code |
| "Health check failed: ..." | "error" | Generic health check error |

### Logging Levels

| Level | Scenario | Example |
|-------|----------|---------|
| DEBUG | Retry attempts (1/3, 2/3) | "Service X health check failed (1/3): disconnected" |
| WARN | Service marked unhealthy | "Service X marked as unhealthy after 3 failures: disconnected" |
| INFO | Service recovered | "Service X recovered and is now healthy" |
| DEBUG | Service still unhealthy | "Service X still unhealthy (4 failures): disconnected" |

## Configuration

### Retry Count

The retry count is configured in `HealthMonitor`:

```java
private static final int MAX_RETRY_COUNT = 3;
```

To change the retry count, modify this constant and recompile.

### Health Check Interval

The health check interval is configured in the YAML configuration file:

```yaml
health:
  checkIntervalMs: 5000  # Check every 5 seconds
```

With a 5-second interval and 3 retries, it takes 15 seconds to mark a service as unhealthy.

## Testing

### New Tests

Added tests for consecutive failure tracking:

```java
@Test
public void testConsecutiveFailures() {
    ServiceInstance instance = new ServiceInstance("service-1", "group-1", "localhost", 9090);
    
    assertEquals(0, instance.getConsecutiveFailures());
    
    instance.incrementConsecutiveFailures();
    assertEquals(1, instance.getConsecutiveFailures());
    
    instance.incrementConsecutiveFailures();
    assertEquals(2, instance.getConsecutiveFailures());
    
    instance.resetConsecutiveFailures();
    assertEquals(0, instance.getConsecutiveFailures());
}

@Test
public void testSetHealthyResetsFailures() {
    ServiceInstance instance = new ServiceInstance("service-1", "group-1", "localhost", 9090);
    
    instance.incrementConsecutiveFailures();
    instance.incrementConsecutiveFailures();
    assertEquals(2, instance.getConsecutiveFailures());
    
    instance.setHealthy(true);
    assertEquals(0, instance.getConsecutiveFailures());
}
```

### Test Results

All 62 tests pass successfully:
- ServiceInstanceTest: 8 tests (2 new)
- ConsistentHashPartitionerTest: 10 tests
- ServiceRegistryTest: 9 tests
- AttributeInfoTest: 8 tests
- AttributeDefTest: 10 tests
- TimeChunkTest: 17 tests

## Benefits

1. **Reduced False Positives**: Temporary network issues no longer mark services as unhealthy
2. **Better User Experience**: Clear, user-friendly error messages instead of technical gRPC errors
3. **Improved Debugging**: Detailed logging shows retry progression
4. **Configurable**: Easy to adjust retry count and interval
5. **Production Ready**: Handles real-world network instability gracefully

## Monitoring

### Key Metrics

Monitor these metrics to understand service health:

1. **Consecutive Failures**: Track how many services are approaching the retry limit
2. **Healthy Services**: Count of services that passed health checks
3. **Unhealthy Services**: Count of services that exceeded retry limit
4. **Recovery Rate**: How quickly services recover from failures

### Example Monitoring

```bash
# Watch for services approaching retry limit
grep "health check failed (2/3)" logs/e4s-cache.log

# Watch for services being marked unhealthy
grep "marked as unhealthy after 3 failures" logs/e4s-cache.log

# Watch for service recoveries
grep "recovered and is now healthy" logs/e4s-cache.log
```

## Troubleshooting

### Issue: Services Not Marked Unhealthy

**Symptom**: Services remain healthy despite being offline

**Possible Causes**:
1. Health check interval too long
2. Retry count too high
3. Network issues preventing health checks

**Solutions**:
1. Reduce health check interval
2. Reduce MAX_RETRY_COUNT
3. Check network connectivity

### Issue: Too Many False Positives

**Symptom**: Services marked unhealthy due to temporary issues

**Possible Causes**:
1. Retry count too low
2. Health check interval too short
3. Network instability

**Solutions**:
1. Increase MAX_RETRY_COUNT
2. Increase health check interval
3. Improve network stability

### Issue: Slow Recovery Detection

**Symptom**: Services take too long to be marked healthy again

**Possible Causes**:
1. Health check interval too long
2. Retry count too high

**Solutions**:
1. Reduce health check interval
2. Reduce MAX_RETRY_COUNT

## Best Practices

### 1. Configure Based on Environment

**Development:**
```yaml
health:
  checkIntervalMs: 10000  # Less frequent checks
```

**Production:**
```yaml
health:
  checkIntervalMs: 5000   # More frequent checks
```

### 2. Monitor Retry Patterns

Track retry patterns to identify problematic services:

```bash
# Count services with high failure rates
grep "health check failed" logs/e4s-cache.log | \
  awk '{print $4}' | sort | uniq -c | sort -rn
```

### 3. Set Up Alerts

Alert on services that are consistently failing:

```bash
# Alert if service has > 10 consecutive failures
if grep -c "still unhealthy (1[0-9] failures)" logs/e4s-cache.log > 0; then
    echo "ALERT: Service has high failure rate"
fi
```

### 4. Adjust Retry Count

Adjust retry count based on your requirements:

- **Low latency requirements**: Lower retry count (2-3)
- **High availability requirements**: Higher retry count (5-10)
- **Stable network**: Lower retry count (2-3)
- **Unstable network**: Higher retry count (5-10)

## Future Enhancements

Potential improvements to the retry mechanism:

1. **Exponential Backoff**: Increase delay between retries
2. **Circuit Breaker**: Stop checking after many consecutive failures
3. **Custom Retry Policies**: Per-service retry configurations
4. **Failure Reason Tracking**: Track different failure reasons separately
5. **Health Check History**: Maintain history of health check results
6. **Adaptive Retry**: Adjust retry count based on historical data

## Conclusion

The retry mechanism provides a more robust and production-ready health monitoring system. By requiring 3 consecutive failures before marking services as unhealthy, the system avoids false positives from temporary network issues while still detecting genuine service failures. The improved error messages and detailed logging make it easier to understand and debug service health issues.
