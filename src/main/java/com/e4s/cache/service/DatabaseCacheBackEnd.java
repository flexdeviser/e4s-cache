package com.e4s.cache.service;

import com.e4s.cache.grpc.DataPoint;
import com.e4s.cache.model.AttributeDef;
import com.e4s.cache.model.AttributeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DatabaseCacheBackEnd implements CacheBackEnd {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseCacheBackEnd.class);
    private static final int CACHE_FILL_BATCH_SIZE = 100;
    
    private final CacheBackEnd realDatabase;
    
    public DatabaseCacheBackEnd(CacheBackEnd realDatabase) {
        this.realDatabase = realDatabase;
    }
    
    @Override
    public byte[] fetchFromDB(String sensorId, String attribute, long startTime, long endTime) {
        logger.debug("Fetching from DB for sensorId: {}, attribute: {}", sensorId, attribute);
        
        return realDatabase.fetchFromDB(sensorId, attribute, startTime, endTime);
    }
    
    @Override
    public int fillFromCache(String sensorId, List<DataPoint> dataPoints) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return 0;
        }
        
        logger.debug("Filling cache for sensorId: {}, points: {}", sensorId, dataPoints.size());
        
        List<DataPoint> batch = new ArrayList<>(CACHE_FILL_BATCH_SIZE);
        int filledCount = 0;
        
        for (DataPoint dataPoint : dataPoints) {
            batch.add(dataPoint);
            filledCount++;
            
            if (batch.size() >= CACHE_FILL_BATCH_SIZE) {
                fillBatch(sensorId, batch);
                batch.clear();
                logger.debug("Filled batch for sensorId: {}, points: {}", sensorId, filledCount);
            }
        }
        
        if (!batch.isEmpty()) {
            fillBatch(sensorId, batch);
            logger.debug("Filled remaining batch for sensorId: {}, points: {}", sensorId, filledCount);
        }
        
        return filledCount;
    }
    
    private void fillBatch(String sensorId, List<DataPoint> batch) {
        for (DataPoint dataPoint : batch) {
            String attributeName = dataPoint.getAttribute();
            double value = dataPoint.getValue();
            
            AttributeDef attrDef = new AttributeDef(attributeName, 100, 8);
            byte[] data = new byte[8];
            ByteBuffer.wrap(data).putDouble(value);
            
            byte[] attributeData = data;
            
            try {
                realDatabase.fillFromCache(sensorId, batch);
                logger.debug("Filled cache for sensorId: {}, attribute: {}, value: {}", 
                    sensorId, attributeName, value);
            } catch (Exception e) {
                logger.error("Error filling cache for sensorId: {}, attribute: {}", 
                    sensorId, attributeName, e);
            }
        }
    }
}