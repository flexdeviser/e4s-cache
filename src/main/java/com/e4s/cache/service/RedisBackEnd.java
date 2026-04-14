package com.e4s.cache.service;

import com.e4s.cache.grpc.DataPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

public class RedisBackEnd implements CacheBackEnd {
    private static final Logger logger = LoggerFactory.getLogger(RedisBackEnd.class);
    private static final Random random = new Random();
    
    @Override
    public byte[] fetchFromDB(String sensorId, String attribute, long startTime, long endTime) {
        logger.debug("Simulating DB fetch for sensorId: {}, attribute: {}", sensorId, attribute);
        
        byte[] data = new byte[8];
        random.nextBytes(data);
        
        double value = ByteBuffer.wrap(data).getDouble();
        
        logger.debug("Generated random value for sensorId: {}, attribute: {}, value: {}", 
            sensorId, attribute, value);
        
        return data;
    }
    
    @Override
    public int fillFromCache(String sensorId, List<DataPoint> dataPoints) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return 0;
        }
        
        logger.debug("Simulating cache fill for sensorId: {}, points: {}", 
            sensorId, dataPoints.size());
        
        return dataPoints.size();
    }
    
    private double generateRandomValue() {
        double range = 100.0 - (-100.0);
        return random.nextDouble() * range + (-100.0);
    }
}