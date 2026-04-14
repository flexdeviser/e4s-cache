package com.e4s.cache.client;

import com.e4s.cache.config.CacheClientConfig;
import com.e4s.cache.config.ConfigLoader;
import com.e4s.cache.discovery.*;
import com.e4s.cache.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DistributedCacheClient {
    private static final Logger logger = LoggerFactory.getLogger(DistributedCacheClient.class);
    
    private final ServiceRegistry serviceRegistry;
    private final ConsistentHashPartitioner partitioner;
    private final CacheServiceClientPool clientPool;
    private final ServiceInstance localClient;
    
    public DistributedCacheClient(String clientId, List<ServiceInstance> services) {
        this.localClient = new ServiceInstance(clientId, "client", "localhost", 0);
        this.serviceRegistry = new ServiceRegistry();
        
        for (ServiceInstance service : services) {
            serviceRegistry.registerService(service);
        }
        
        this.partitioner = new ConsistentHashPartitioner(serviceRegistry.getAllServices());
        this.clientPool = new CacheServiceClientPool(serviceRegistry.getAllServices());
        
        logger.info("Created DistributedCacheClient: {} with {} services", 
            clientId, services.size());
    }
    
    public DistributedCacheClient(CacheClientConfig config) {
        this.localClient = new ServiceInstance(config.getClientId(), "client", "localhost", 0);
        this.serviceRegistry = new ServiceRegistry();
        
        for (CacheClientConfig.ServiceConfig serviceConfig : config.getServices()) {
            ServiceInstance service = new ServiceInstance(
                serviceConfig.getId(),
                serviceConfig.getGroup(),
                serviceConfig.getHost(),
                serviceConfig.getPort());
            serviceRegistry.registerService(service);
        }
        
        this.partitioner = new ConsistentHashPartitioner(serviceRegistry.getAllServices());
        this.clientPool = new CacheServiceClientPool(serviceRegistry.getAllServices());
        
        logger.info("Created DistributedCacheClient: {} with {} services", 
            config.getClientId(), config.getServices().size());
    }
    
    public GetSeriesResponse getSeries(String sensorId, long startTime, long endTime, 
                                        List<String> attributes) {
        ServiceInstance responsibleService = partitioner.getResponsibleService(sensorId);
        
        logger.debug("Routing getSeries for sensorId: {} to service: {}", 
            sensorId, responsibleService.getId());
        
        try {
            return clientPool.getSeries(responsibleService, sensorId, startTime, endTime, attributes);
        } catch (Exception e) {
            logger.warn("Failed to get series for sensorId: {}", sensorId);
            
            serviceRegistry.markServiceUnhealthy(responsibleService.getId());
            
            return GetSeriesResponse.newBuilder()
                .setSuccess(false)
                .setError("Failed to get series: " + e.getMessage())
                .build();
        }
    }
    
    public FillSeriesResponse fillSeries(String sensorId, List<DataPoint> dataPoints) {
        ServiceInstance responsibleService = partitioner.getResponsibleService(sensorId);
        
        logger.debug("Routing fillSeries for sensorId: {} to service: {}", 
            sensorId, responsibleService.getId());
        
        try {
            return clientPool.fillSeries(responsibleService, sensorId, dataPoints);
        } catch (Exception e) {
            logger.warn("Failed to fill series for sensorId: {}", sensorId);
            
            serviceRegistry.markServiceUnhealthy(responsibleService.getId());
            
            return FillSeriesResponse.newBuilder()
                .setSuccess(false)
                .setError("Failed to fill series: " + e.getMessage())
                .build();
        }
    }
    
    public CompletableFuture<GetSeriesResponse> getSeriesAsync(String sensorId, long startTime, 
                                                               long endTime, List<String> attributes) {
        return CompletableFuture.supplyAsync(() -> 
            getSeries(sensorId, startTime, endTime, attributes));
    }
    
    public CompletableFuture<FillSeriesResponse> fillSeriesAsync(String sensorId, 
                                                                  List<DataPoint> dataPoints) {
        return CompletableFuture.supplyAsync(() -> 
            fillSeries(sensorId, dataPoints));
    }
    
    public List<ServiceInstance> getReplicaServices(String sensorId, int replicaCount) {
        return partitioner.getReplicaServices(sensorId, replicaCount);
    }
    
    public GetSeriesResponse getSeriesWithReplica(String sensorId, long startTime, long endTime, 
                                                   List<String> attributes, int replicaCount) {
        List<ServiceInstance> replicas = getReplicaServices(sensorId, replicaCount);
        
        for (ServiceInstance replica : replicas) {
            try {
                GetSeriesResponse response = clientPool.getSeries(
                    replica, sensorId, startTime, endTime, attributes);
                
                if (response.getSuccess()) {
                    logger.debug("Successfully retrieved data from replica: {}", replica.getId());
                    return response;
                }
                
            } catch (Exception e) {
                logger.warn("Failed to get data from replica: {}", replica.getId());
                serviceRegistry.markServiceUnhealthy(replica.getId());
            }
        }
        
        return GetSeriesResponse.newBuilder()
            .setSuccess(false)
            .setError("Failed to get data from all replicas")
            .build();
    }
    
    public HealthCheckResponse healthCheck(ServiceInstance service) {
        return clientPool.healthCheck(service);
    }
    
    public List<HealthCheckResponse> healthCheckAll() {
        List<HealthCheckResponse> responses = new ArrayList<>();
        
        for (ServiceInstance service : serviceRegistry.getAllServices()) {
            responses.add(healthCheck(service));
        }
        
        return responses;
    }
    
    public int getServiceCount() {
        return serviceRegistry.getServiceCount();
    }
    
    public int getHealthyServiceCount() {
        return serviceRegistry.getHealthyServiceCount();
    }
    
    public void shutdown() {
        clientPool.shutdown();
        logger.info("Shutdown DistributedCacheClient");
    }
    
    public static void main(String[] args) {
        try {
            String configPath = args.length > 0 ? args[0] : null;
            CacheClientConfig config;
            
            if (configPath != null) {
                config = com.e4s.cache.config.ConfigLoader.loadClientConfig(configPath);
            } else {
                config = com.e4s.cache.config.ConfigLoader.loadClientConfig();
            }
            
            DistributedCacheClient client = new DistributedCacheClient(config);
            
            logger.info("Client connected to {} services", client.getServiceCount());
            logger.info("Healthy services: {}", client.getHealthyServiceCount());
            
            List<String> attributes = new ArrayList<>();
            attributes.add("temperature");
            attributes.add("humidity");
            
            GetSeriesResponse response = client.getSeries(
                "sensor-123", System.currentTimeMillis() - 86400000, 
                System.currentTimeMillis(), attributes);
            
            if (response.getSuccess()) {
                logger.info("Successfully retrieved {} attributes", response.getAttributeDataCount());
            } else {
                logger.error("Failed to retrieve data: {}", response.getError());
            }
            
            client.shutdown();
            
        } catch (Exception e) {
            logger.error("Failed to run distributed cache client", e);
            System.exit(1);
        }
    }
}
