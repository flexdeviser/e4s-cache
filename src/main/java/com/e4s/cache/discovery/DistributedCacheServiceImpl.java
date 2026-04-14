package com.e4s.cache.discovery;

import com.e4s.cache.grpc.*;
import com.e4s.cache.lock.DistributedLockManager;
import com.e4s.cache.model.AttributeDef;
import com.e4s.cache.model.AttributeInfo;
import com.e4s.cache.model.ThreadSafeCompressedChunkManager;
import com.e4s.cache.service.CacheBackEnd;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DistributedCacheServiceImpl extends CacheServiceGrpc.CacheServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(DistributedCacheServiceImpl.class);
    
    private final ThreadSafeCompressedChunkManager chunkManager;
    private final DistributedLockManager lockManager;
    private final CacheBackEnd backEnd;
    private final IServiceRegistry serviceRegistry;
    private final ConsistentHashPartitioner partitioner;
    private final CacheServiceClientPool clientPool;
    private final ServiceInstance localService;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    
    public DistributedCacheServiceImpl(ThreadSafeCompressedChunkManager chunkManager, 
                                       DistributedLockManager lockManager,
                                       CacheBackEnd backEnd,
                                       IServiceRegistry serviceRegistry,
                                       ConsistentHashPartitioner partitioner,
                                       CacheServiceClientPool clientPool,
                                       ServiceInstance localService) {
        this.chunkManager = chunkManager;
        this.lockManager = lockManager;
        this.backEnd = backEnd;
        this.serviceRegistry = serviceRegistry;
        this.partitioner = partitioner;
        this.clientPool = clientPool;
        this.localService = localService;
        
        logger.info("Created DistributedCacheServiceImpl for service: {}", localService.getId());
    }
    
    @Override
    public void getSeries(GetSeriesRequest request, StreamObserver<GetSeriesResponse> responseObserver) {
        String sensorId = request.getSensorId();
        long startTime = request.getStartTime();
        long endTime = request.getEndTime();
        List<String> attributes = request.getAttributesList();
        
        requestCount.incrementAndGet();
        logger.debug("GetSeries called for sensorId: {}, startTime: {}, endTime: {}, attributes: {}",
            sensorId, startTime, endTime, attributes);
        
        try {
            ServiceInstance responsibleService = partitioner.getResponsibleService(sensorId);
            
            if (!responsibleService.getId().equals(localService.getId())) {
                logger.debug("Forwarding getSeries request for sensorId: {} to service: {}", 
                    sensorId, responsibleService.getId());
                forwardGetSeries(responsibleService, request, responseObserver);
                return;
            }
            
            GetSeriesResponse.Builder responseBuilder = GetSeriesResponse.newBuilder();
            
            List<AttributeData> attributeDataList = new ArrayList<>();
            
            for (String attribute : attributes) {
                AttributeData attributeData = getAttributeData(sensorId, startTime, endTime, attribute);
                if (attributeData != null) {
                    attributeDataList.add(attributeData);
                }
            }
            
            responseBuilder.addAllAttributeData(attributeDataList);
            responseBuilder.setSuccess(true);
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            
            logger.debug("GetSeries completed for sensorId: {}, retrieved {} attributes",
                sensorId, attributeDataList.size());
            
        } catch (Exception e) {
            logger.error("Error in GetSeries for sensorId: {}", sensorId, e);
            responseObserver.onNext(
                GetSeriesResponse.newBuilder()
                    .setSuccess(false)
                    .setError(e.getMessage())
                    .build()
            );
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void fillSeries(FillSeriesRequest request, StreamObserver<FillSeriesResponse> responseObserver) {
        String sensorId = request.getSensorId();
        List<DataPoint> dataPoints = request.getDataPointsList();
        
        requestCount.incrementAndGet();
        logger.debug("FillSeries called for sensorId: {}, dataPoints: {}", sensorId, dataPoints.size());
        
        try {
            ServiceInstance responsibleService = partitioner.getResponsibleService(sensorId);
            
            if (!responsibleService.getId().equals(localService.getId())) {
                logger.debug("Forwarding fillSeries request for sensorId: {} to service: {}", 
                    sensorId, responsibleService.getId());
                forwardFillSeries(responsibleService, request, responseObserver);
                return;
            }
            
            FillSeriesResponse.Builder responseBuilder = FillSeriesResponse.newBuilder();
            
            DistributedLockManager lock = new DistributedLockManager(
                lockManager.getJedisPool(), sensorId);
            
            try {
                lock.lock();
                
                try {
                    int filledCount = backEnd.fillFromCache(sensorId, dataPoints);
                    
                    byte[] attributeData = createAttributeData(dataPoints.get(0).getAttribute(), 
                                                               dataPoints.get(0).getValue());
                    chunkManager.storeData(sensorId, attributeData);
                    
                    responseBuilder.setSuccess(true);
                    responseBuilder.setFilledCount(filledCount);
                    
                } finally {
                    lock.unlock();
                }
                
            } catch (Exception e) {
                logger.error("Error in FillSeries for sensorId: {}", sensorId, e);
                responseBuilder.setSuccess(false);
                responseBuilder.setError(e.getMessage());
            }
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            
            logger.debug("FillSeries completed for sensorId: {}, filled: {}", sensorId, 
                dataPoints.size());
            
        } catch (Exception e) {
            logger.error("Error processing FillSeries for sensorId: {}", sensorId, e);
            responseObserver.onNext(
                FillSeriesResponse.newBuilder()
                    .setSuccess(false)
                    .setError(e.getMessage())
                    .build()
            );
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void healthCheck(HealthCheckRequest request, 
                          StreamObserver<HealthCheckResponse> responseObserver) {
        int requestCount = this.requestCount.get();
        int serviceCount = serviceRegistry.getServiceCount();
        
        HealthCheckResponse response = HealthCheckResponse.newBuilder()
            .setHealthy(true)
            .setStatus(String.format("UP - Requests: %d, Services: %d", 
                requestCount, serviceCount))
            .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    private void forwardGetSeries(ServiceInstance targetService, GetSeriesRequest request,
                                  StreamObserver<GetSeriesResponse> responseObserver) {
        try {
            GetSeriesResponse response = clientPool.getSeries(
                targetService, 
                request.getSensorId(),
                request.getStartTime(),
                request.getEndTime(),
                request.getAttributesList()
            );
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.warn("Failed to forward getSeries request to service: {}", 
                targetService.getId());
            
            responseObserver.onNext(
                GetSeriesResponse.newBuilder()
                    .setSuccess(false)
                    .setError("Failed to forward request: " + e.getMessage())
                    .build()
            );
            responseObserver.onCompleted();
        }
    }
    
    private void forwardFillSeries(ServiceInstance targetService, FillSeriesRequest request,
                                   StreamObserver<FillSeriesResponse> responseObserver) {
        try {
            FillSeriesResponse response = clientPool.fillSeries(
                targetService,
                request.getSensorId(),
                request.getDataPointsList()
            );
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.warn("Failed to forward fillSeries request to service: {}", 
                targetService.getId());
            
            responseObserver.onNext(
                FillSeriesResponse.newBuilder()
                    .setSuccess(false)
                    .setError("Failed to forward request: " + e.getMessage())
                    .build()
            );
            responseObserver.onCompleted();
        }
    }
    
    private AttributeData getAttributeData(String sensorId, long startTime, long endTime, String attribute) {
        byte[] rawData = chunkManager.getDataForAttribute(sensorId, attribute, endTime);
        
        if (rawData == null) {
            logger.debug("Cache miss for sensorId: {}, attribute: {}", sensorId, attribute);
            
            DistributedLockManager lock = new DistributedLockManager(
                lockManager.getJedisPool(), sensorId);
            
            try {
                lock.lock();
                
                try {
                    rawData = chunkManager.getDataForAttribute(sensorId, attribute, endTime);
                    
                    if (rawData == null) {
                        rawData = backEnd.fetchFromDB(sensorId, attribute, startTime, endTime);
                        
                        if (rawData != null) {
                            byte[] attributeData = createAttributeData(attribute, 
                                new AttributeDef(attribute, 100, 8).decodeAttribute(rawData).getValue());
                            chunkManager.storeData(sensorId, attributeData);
                            logger.info("Primed cache for sensorId: {}, attribute: {}", sensorId, attribute);
                        }
                    }
                    
                    if (rawData == null) {
                        return null;
                    }
                    
                } finally {
                    lock.unlock();
                }
            } catch (Exception e) {
                logger.error("Error during cache priming for sensorId: {}", sensorId, e);
                return null;
            }
        }
        
        AttributeDef attrDef = new AttributeDef(attribute, 100, 8);
        AttributeInfo attrInfo = attrDef.decodeAttribute(rawData);
        
        return AttributeData.newBuilder()
            .setAttribute(attribute)
            .addTimestamps(startTime)
            .addTimestamps(endTime)
            .addValues(attrInfo.getValue())
            .build();
    }
    
    private byte[] createAttributeData(String attributeName, double value) {
        AttributeDef attrDef = new AttributeDef(attributeName, 100, 8);
        byte[] data = new byte[8];
        ByteBuffer.wrap(data).putDouble(value);
        return data;
    }
    
    public int getRequestCount() {
        return requestCount.get();
    }
}
