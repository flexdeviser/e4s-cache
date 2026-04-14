package com.e4s.cache.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DistributedChunkManager {
    private static final Logger logger = LoggerFactory.getLogger(DistributedChunkManager.class);
    
    private final IServiceRegistry serviceRegistry;
    private final ConsistentHashPartitioner partitioner;
    private final CacheServiceClientPool clientPool;
    private final ServiceInstance localService;
    private final ExecutorService executorService;
    
    public DistributedChunkManager(IServiceRegistry serviceRegistry,
                                   ConsistentHashPartitioner partitioner,
                                   CacheServiceClientPool clientPool,
                                   ServiceInstance localService) {
        this.serviceRegistry = serviceRegistry;
        this.partitioner = partitioner;
        this.clientPool = clientPool;
        this.localService = localService;
        this.executorService = Executors.newFixedThreadPool(10);
        
        logger.info("Created DistributedChunkManager for service: {}", localService.getId());
    }
    
    public byte[] getDataForAttribute(String sensorId, String attribute, long timestamp) {
        ServiceInstance responsibleService = partitioner.getResponsibleService(sensorId);
        
        if (!responsibleService.getId().equals(localService.getId())) {
            logger.debug("Routing getDataForAttribute for sensorId: {} to service: {}", 
                sensorId, responsibleService.getId());
            
            try {
                List<String> attributes = new ArrayList<>();
                attributes.add(attribute);
                
                var response = clientPool.getSeries(responsibleService, sensorId, 
                    timestamp - 86400000, timestamp, attributes);
                
                if (response.getSuccess() && response.getAttributeDataCount() > 0) {
                    var attrData = response.getAttributeData(0);
                    if (attrData.getValuesCount() > 0) {
                        byte[] data = new byte[8];
                        long bits = Double.doubleToLongBits(attrData.getValues(0));
                        data[0] = (byte) (bits >> 56);
                        data[1] = (byte) (bits >> 48);
                        data[2] = (byte) (bits >> 40);
                        data[3] = (byte) (bits >> 32);
                        data[4] = (byte) (bits >> 24);
                        data[5] = (byte) (bits >> 16);
                        data[6] = (byte) (bits >> 8);
                        data[7] = (byte) bits;
                        return data;
                    }
                }
                
                return null;
                
            } catch (Exception e) {
                logger.warn("Failed to get data from service: {} for sensorId: {}", 
                    responsibleService.getId(), sensorId);
                return null;
            }
        }
        
        logger.debug("Local request for sensorId: {}", sensorId);
        return null;
    }
    
    public void storeData(String sensorId, byte[] data) {
        ServiceInstance responsibleService = partitioner.getResponsibleService(sensorId);
        
        if (!responsibleService.getId().equals(localService.getId())) {
            logger.debug("Routing storeData for sensorId: {} to service: {}", 
                sensorId, responsibleService.getId());
            
            try {
                List<com.e4s.cache.grpc.DataPoint> dataPoints = new ArrayList<>();
                double value = Double.longBitsToDouble(
                    ((long) data[0] << 56) | 
                    ((long) data[1] & 0xFF) << 48 | 
                    ((long) data[2] & 0xFF) << 40 | 
                    ((long) data[3] & 0xFF) << 32 | 
                    ((long) data[4] & 0xFF) << 24 | 
                    ((long) data[5] & 0xFF) << 16 | 
                    ((long) data[6] & 0xFF) << 8 | 
                    ((long) data[7] & 0xFF)
                );
                
                var dataPoint = com.e4s.cache.grpc.DataPoint.newBuilder()
                    .setTimestamp(System.currentTimeMillis())
                    .setAttribute("value")
                    .setValue(value)
                    .build();
                dataPoints.add(dataPoint);
                
                var response = clientPool.fillSeries(responsibleService, sensorId, dataPoints);
                
                if (!response.getSuccess()) {
                    logger.warn("Failed to store data on service: {} for sensorId: {}", 
                        responsibleService.getId(), sensorId);
                }
                
            } catch (Exception e) {
                logger.warn("Failed to store data on service: {} for sensorId: {}", 
                    responsibleService.getId(), sensorId);
            }
        } else {
            logger.debug("Local store for sensorId: {}", sensorId);
        }
    }
    
    public CompletableFuture<byte[]> getDataForAttributeAsync(String sensorId, String attribute, 
                                                               long timestamp) {
        return CompletableFuture.supplyAsync(() -> 
            getDataForAttribute(sensorId, attribute, timestamp), executorService);
    }
    
    public CompletableFuture<Void> storeDataAsync(String sensorId, byte[] data) {
        return CompletableFuture.runAsync(() -> 
            storeData(sensorId, data), executorService);
    }
    
    public List<ServiceInstance> getReplicaServices(String sensorId, int replicaCount) {
        return partitioner.getReplicaServices(sensorId, replicaCount);
    }
    
    public ServiceInstance getResponsibleService(String sensorId) {
        return partitioner.getResponsibleService(sensorId);
    }
    
    public int getServiceCount() {
        return serviceRegistry.getServiceCount();
    }
    
    public void shutdown() {
        executorService.shutdown();
        logger.info("Shutdown DistributedChunkManager");
    }
}
