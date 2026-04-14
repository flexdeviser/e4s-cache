package com.e4s.cache.discovery;

import com.e4s.cache.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CacheServiceClientPool {
    private static final Logger logger = LoggerFactory.getLogger(CacheServiceClientPool.class);
    
    private final Map<String, CacheServiceClient> clients = new ConcurrentHashMap<>();
    private final Map<String, ServiceInstance> serviceMap;
    
    public CacheServiceClientPool(List<ServiceInstance> services) {
        this.serviceMap = services.stream()
            .collect(Collectors.toMap(ServiceInstance::getId, s -> s));
        
        for (ServiceInstance service : services) {
            clients.computeIfAbsent(service.getId(), id -> 
                new CacheServiceClient(service.getHost(), service.getPort()));
        }
        
        logger.info("Created client pool for {} services", services.size());
    }
    
    public CacheServiceClient getClient(ServiceInstance service) {
        return clients.get(service.getId());
    }
    
    public CacheServiceClient getClient(String serviceId) {
        return clients.get(serviceId);
    }
    
    public GetSeriesResponse getSeries(ServiceInstance service, String sensorId, 
                                        long startTime, long endTime, List<String> attributes) {
        CacheServiceClient client = getClient(service);
        if (client == null) {
            logger.warn("No client found for service: {}", service.getId());
            return GetSeriesResponse.newBuilder()
                .setSuccess(false)
                .setError("Service not found: " + service.getId())
                .build();
        }
        
        try {
            return client.getSeries(sensorId, startTime, endTime, attributes);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Service unavailable")) {
                logger.warn("Service {} unavailable for getSeries request", service.getId());
            } else {
                logger.error("Failed to get series from service: {}", service.getId(), e);
            }
            return GetSeriesResponse.newBuilder()
                .setSuccess(false)
                .setError("Failed to get series: " + e.getMessage())
                .build();
        }
    }
    
    public FillSeriesResponse fillSeries(ServiceInstance service, String sensorId, 
                                          List<DataPoint> dataPoints) {
        CacheServiceClient client = getClient(service);
        if (client == null) {
            logger.warn("No client found for service: {}", service.getId());
            return FillSeriesResponse.newBuilder()
                .setSuccess(false)
                .setError("Service not found: " + service.getId())
                .build();
        }
        
        try {
            return client.fillSeries(sensorId, dataPoints);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Service unavailable")) {
                logger.warn("Service {} unavailable for fillSeries request", service.getId());
            } else {
                logger.error("Failed to fill series on service: {}", service.getId(), e);
            }
            return FillSeriesResponse.newBuilder()
                .setSuccess(false)
                .setError("Failed to fill series: " + e.getMessage())
                .build();
        }
    }
    
    public HealthCheckResponse healthCheck(ServiceInstance service) {
        CacheServiceClient client = getClient(service);
        if (client == null) {
            return HealthCheckResponse.newBuilder()
                .setHealthy(false)
                .setStatus("Service not found")
                .build();
        }
        
        try {
            return client.healthCheck();
        } catch (Exception e) {
            logger.warn("Failed health check for service: {}", service.getId());
            return HealthCheckResponse.newBuilder()
                .setHealthy(false)
                .setStatus("Health check failed: " + e.getMessage())
                .build();
        }
    }
    
    public void shutdown() {
        for (CacheServiceClient client : clients.values()) {
            try {
                client.shutdown();
            } catch (Exception e) {
                logger.error("Failed to shutdown client", e);
            }
        }
    }
}