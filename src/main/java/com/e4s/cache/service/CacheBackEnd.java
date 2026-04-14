package com.e4s.cache.service;

import java.util.List;

public interface CacheBackEnd {
    byte[] fetchFromDB(String sensorId, String attribute, long startTime, long endTime);
    
    int fillFromCache(String sensorId, List<com.e4s.cache.grpc.DataPoint> dataPoints);
}