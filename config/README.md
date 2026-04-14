# Configuration Files

This directory contains configuration files for the e4s-cache distributed system.

## Service Configuration

Service configuration files define the settings for each cache service instance.

### File Format

Service configuration files use YAML format with the following structure:

```yaml
# Service identification
serviceId: cache-service-1          # Unique service identifier
serviceGroup: e4s-cache             # Logical service group
host: localhost                     # Service host address
port: 9090                          # Service port

# Cache configuration
cache:
  maxChunks: 2000000               # Maximum number of chunks
  chunkIntervalHours: 24           # Time interval per chunk (hours)
  maxMemoryBytes: 107374182400     # Maximum memory in bytes (100GB)

# Redis configuration for distributed locking
redis:
  host: localhost                  # Redis host
  port: 6379                       # Redis port

# Health monitoring configuration
health:
  checkIntervalMs: 5000            # Health check interval (milliseconds)

# Peer services (other cache services in the cluster)
peers:
  - id: cache-service-2
    host: localhost
    port: 9091
  - id: cache-service-3
    host: localhost
    port: 9092
```

### Available Service Configurations

- `cache-service.yaml` - Default service configuration template
- `cache-service-1.yaml` - Configuration for service 1 (port 9090)
- `cache-service-2.yaml` - Configuration for service 2 (port 9091)
- `cache-service-3.yaml` - Configuration for service 3 (port 9092)

### Starting a Service

To start a cache service with a specific configuration:

```bash
# Using default config file (config/cache-service.yaml)
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer

# Using custom config file
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-1.yaml

# Using environment variable
export E4S_CACHE_CONFIG=config/cache-service-1.yaml
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer
```

## Client Configuration

Client configuration files define the cache services that a client should connect to.

### File Format

Client configuration files use YAML format with the following structure:

```yaml
# Client identification
clientId: cache-client-1

# Cache services to connect to
services:
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

### Available Client Configurations

- `cache-client.yaml` - Default client configuration

### Starting a Client

To start a cache client with a specific configuration:

```bash
# Using default config file (config/cache-client.yaml)
java -cp e4s-cache.jar com.e4s.cache.client.DistributedCacheClient

# Using custom config file
java -cp e4s-cache.jar com.e4s.cache.client.DistributedCacheClient config/cache-client.yaml
```

## Configuration Validation

The configuration loader validates all configuration files and will fail with a clear error message if:

- Required fields are missing
- Port numbers are out of range (must be 1-65535)
- Peer services have invalid configurations
- Service IDs or hostnames are empty

## Example: Starting a 3-Node Cluster

### Terminal 1 - Service 1
```bash
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-1.yaml
```

### Terminal 2 - Service 2
```bash
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-2.yaml
```

### Terminal 3 - Service 3
```bash
java -cp e4s-cache.jar com.e4s.cache.server.DistributedCacheServer config/cache-service-3.yaml
```

### Terminal 4 - Client
```bash
java -cp e4s-cache.jar com.e4s.cache.client.DistributedCacheClient config/cache-client.yaml
```

## Configuration Best Practices

1. **Service IDs**: Use descriptive, unique service IDs (e.g., `cache-service-prod-1`)
2. **Service Groups**: Group related services together (e.g., `e4s-cache-prod`)
3. **Memory Configuration**: Adjust `maxMemoryBytes` based on available system memory
4. **Health Checks**: Set `checkIntervalMs` based on your monitoring requirements
5. **Peer Configuration**: Ensure all services have consistent peer configurations
6. **Environment Variables**: Use `E4S_CACHE_CONFIG` for environment-specific configurations

## Production Deployment

For production deployments:

1. Create separate configuration files for each environment (dev, staging, prod)
2. Use environment-specific hostnames and ports
3. Configure appropriate memory limits based on server capacity
4. Set health check intervals based on monitoring requirements
5. Ensure Redis is properly configured for distributed locking
6. Test configuration files before deployment

## Troubleshooting

### Configuration File Not Found
```
Error: Config file not found: /path/to/config.yaml
```
**Solution**: Ensure the configuration file exists at the specified path or use the default location.

### Invalid Configuration
```
Error: serviceId is required
```
**Solution**: Ensure all required fields are present in the configuration file.

### Port Already in Use
```
Error: Failed to bind to port 9090
```
**Solution**: Ensure the port is not already in use or use a different port in the configuration.
