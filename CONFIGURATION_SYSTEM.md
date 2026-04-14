# Configuration System Documentation

## Overview

The e4s-cache distributed system uses YAML-based configuration files for easy management of service and client settings. This approach provides:

- **Human-readable configuration** - Easy to understand and modify
- **Environment flexibility** - Separate configs for dev, staging, prod
- **Validation** - Automatic validation of configuration values
- **Type safety** - Strongly typed configuration classes
- **Default values** - Sensible defaults for optional settings

## Architecture

### Configuration Classes

#### CacheServiceConfig
Main configuration class for cache services with nested configuration sections:

- **Service Identification**: `serviceId`, `serviceGroup`, `host`, `port`
- **Cache Settings**: `maxChunks`, `chunkIntervalHours`, `maxMemoryBytes`
- **Redis Settings**: `host`, `port`
- **Health Monitoring**: `checkIntervalMs`
- **Peer Services**: List of peer service configurations

#### CacheClientConfig
Configuration class for cache clients:

- **Client Identification**: `clientId`
- **Service List**: List of cache services to connect to

### ConfigLoader

The `ConfigLoader` class provides methods for loading configuration files:

```java
// Load service configuration with default path
CacheServiceConfig config = ConfigLoader.loadServiceConfig();

// Load service configuration from specific path
CacheServiceConfig config = ConfigLoader.loadServiceConfig("config/cache-service-1.yaml");

// Load client configuration with default path
CacheClientConfig config = ConfigLoader.loadClientConfig();

// Load client configuration from specific path
CacheClientConfig config = ConfigLoader.loadClientConfig("config/cache-client.yaml");

// Load any configuration type
MyConfig config = ConfigLoader.loadConfig("config/my-config.yaml", MyConfig.class);
```

### Environment Variable Support

Configuration file path can be specified via environment variable:

```bash
export E4S_CACHE_CONFIG=/path/to/config.yaml
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer
```

## Configuration File Structure

### Service Configuration

```yaml
# Service identification
serviceId: cache-service-1          # Required: Unique service identifier
serviceGroup: e4s-cache             # Required: Logical service group
host: localhost                     # Required: Service host address
port: 9090                          # Required: Service port (1-65535)

# Cache configuration
cache:
  maxChunks: 2000000               # Optional: Maximum number of chunks (default: 2,000,000)
  chunkIntervalHours: 24           # Optional: Time interval per chunk in hours (default: 24)
  maxMemoryBytes: 107374182400     # Optional: Maximum memory in bytes (default: 100GB)

# Redis configuration for distributed locking
redis:
  host: localhost                  # Optional: Redis host (default: localhost)
  port: 6379                       # Optional: Redis port (default: 6379)

# Health monitoring configuration
health:
  checkIntervalMs: 5000            # Optional: Health check interval in ms (default: 5000)

# Peer services (other cache services in the cluster)
peers:                             # Optional: List of peer services
  - id: cache-service-2
    host: localhost
    port: 9091
  - id: cache-service-3
    host: localhost
    port: 9092
```

### Client Configuration

```yaml
# Client identification
clientId: cache-client-1            # Required: Unique client identifier

# Cache services to connect to
services:                          # Required: List of cache services
  - id: cache-service-1
    group: e4s-cache
    host: localhost
    port: 9090
  - id: cache-service-2
    group: e4s-cache
    host: localhost
    port: 9091
  - id: cache-service-3
    group: e4s-cache
    host: localhost
    port: 9092
```

## Configuration Validation

The configuration loader performs comprehensive validation:

### Required Fields
- Service: `serviceId`, `serviceGroup`, `host`, `port`
- Client: `clientId`, at least one service

### Port Validation
- Must be between 1 and 65535
- Applies to service port, Redis port, and peer service ports

### Peer Service Validation
- Each peer must have: `id`, `host`, `port`
- Port must be valid (1-65535)

### Service Validation
- Each service must have: `id`, `group`, `host`, `port`
- Port must be valid (1-65535)

## Default Values

### Service Configuration Defaults

| Setting | Default Value | Description |
|---------|---------------|-------------|
| `cache.maxChunks` | 2,000,000 | Maximum number of chunks |
| `cache.chunkIntervalHours` | 24 | Time interval per chunk (hours) |
| `cache.maxMemoryBytes` | 107374182400 (100GB) | Maximum memory in bytes |
| `redis.host` | localhost | Redis host address |
| `redis.port` | 6379 | Redis port |
| `health.checkIntervalMs` | 5000 | Health check interval (ms) |
| `peers` | [] | Empty list of peer services |

## Usage Examples

### Starting a Single Service

```bash
# Using default configuration
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer

# Using custom configuration
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-1.yaml
```

### Starting a Cluster

```bash
# Terminal 1
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-1.yaml

# Terminal 2
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-2.yaml

# Terminal 3
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-3.yaml
```

### Starting a Client

```bash
# Using default configuration
java -cp e4s-cache.jar com.e4s.cache.client.DistributedCacheClient

# Using custom configuration
java -cp e4s-cache.jar com.e4s.cache.client.DistributedCacheClient config/cache-client.yaml
```

### Programmatic Usage

```java
// Load service configuration
CacheServiceConfig config = ConfigLoader.loadServiceConfig("config/cache-service-1.yaml");

// Create distributed server
DistributedCacheServer server = new DistributedCacheServer(config);
server.start();

// Load client configuration
CacheClientConfig clientConfig = ConfigLoader.loadClientConfig("config/cache-client.yaml");

// Create distributed client
DistributedCacheClient client = new DistributedCacheClient(clientConfig);
```

## Environment-Specific Configurations

### Development Environment

```yaml
# config/cache-service-dev.yaml
serviceId: cache-service-dev-1
serviceGroup: e4s-cache-dev
host: localhost
port: 9090

cache:
  maxChunks: 100000
  maxMemoryBytes: 1073741824  # 1GB

redis:
  host: localhost
  port: 6379

health:
  checkIntervalMs: 10000  # Less frequent checks in dev

peers: []
```

### Production Environment

```yaml
# config/cache-service-prod-1.yaml
serviceId: cache-service-prod-1
serviceGroup: e4s-cache-prod
host: cache-prod-1.example.com
port: 9090

cache:
  maxChunks: 2000000
  maxMemoryBytes: 107374182400  # 100GB

redis:
  host: redis-prod.example.com
  port: 6379

health:
  checkIntervalMs: 5000  # Frequent checks in prod

peers:
  - id: cache-service-prod-2
    host: cache-prod-2.example.com
    port: 9090
  - id: cache-service-prod-3
    host: cache-prod-3.example.com
    port: 9090
```

## Best Practices

### 1. Naming Conventions
- Use descriptive service IDs: `cache-service-prod-1`
- Use logical service groups: `e4s-cache-prod`
- Use environment-specific suffixes: `-dev`, `-staging`, `-prod`

### 2. Memory Configuration
- Calculate memory based on: `maxChunks × chunkSize × compressionRatio`
- Leave headroom for JVM overhead
- Monitor actual memory usage in production

### 3. Health Check Intervals
- Development: 10-30 seconds (less frequent)
- Staging: 5-10 seconds
- Production: 1-5 seconds (more frequent)

### 4. Peer Configuration
- Ensure all services have consistent peer lists
- Include all services in the cluster
- Update peer lists when adding/removing services

### 5. Configuration Management
- Store configuration files in version control
- Use environment-specific configurations
- Document configuration changes
- Test configuration files before deployment

### 6. Security
- Don't commit sensitive configuration to version control
- Use environment variables for sensitive data
- Restrict file permissions on production configs
- Consider using configuration management tools

## Troubleshooting

### Common Issues

#### Configuration File Not Found
```
Error: Config file not found: /path/to/config.yaml
```
**Solution**: Check file path and ensure file exists

#### Invalid Port Number
```
Error: port must be between 1 and 65535
```
**Solution**: Use valid port number (1-65535)

#### Missing Required Field
```
Error: serviceId is required
```
**Solution**: Add required field to configuration

#### YAML Syntax Error
```
Error: Failed to load configuration
```
**Solution**: Validate YAML syntax (use online YAML validator)

### Debug Mode

Enable debug logging to troubleshoot configuration issues:

```bash
java -Dlogback.configurationFile=config/logback-debug.xml \
     -cp e4s-cache.jar \
     com.e4s.cache.server.DistributedCacheServer
```

## Migration from Command-Line Arguments

### Old Approach (Command-Line Arguments)

```bash
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer \
  cache-service-1 \
  e4s-cache \
  localhost \
  9090 \
  cache-service-2:localhost:9091,cache-service-3:localhost:9092
```

### New Approach (Configuration File)

```bash
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer \
  config/cache-service-1.yaml
```

### Benefits of Configuration Files

1. **Readability**: Easier to understand and modify
2. **Reusability**: Same config can be used across deployments
3. **Validation**: Automatic validation of configuration values
4. **Documentation**: Self-documenting configuration
5. **Version Control**: Easy to track changes
6. **Environment Management**: Separate configs for different environments

## Future Enhancements

Potential improvements to the configuration system:

1. **Configuration Hot Reload**: Reload configuration without restart
2. **Configuration Encryption**: Encrypt sensitive configuration values
3. **Configuration Templates**: Use templates for common configurations
4. **Configuration Validation**: More comprehensive validation rules
5. **Configuration UI**: Web-based configuration editor
6. **Configuration Backup**: Automatic backup of configuration changes
7. **Configuration Rollback**: Rollback to previous configuration versions
8. **Configuration Diff**: Compare configuration versions

## Conclusion

The YAML-based configuration system provides a flexible, maintainable, and user-friendly approach to managing e4s-cache distributed system settings. By following best practices and using environment-specific configurations, you can easily manage deployments across different environments while maintaining consistency and reliability.
