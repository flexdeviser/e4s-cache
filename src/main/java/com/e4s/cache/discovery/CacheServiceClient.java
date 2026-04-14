package com.e4s.cache.discovery;

import com.e4s.cache.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CacheServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(CacheServiceClient.class);
    
    private final ManagedChannel channel;
    private final CacheServiceGrpc.CacheServiceBlockingStub blockingStub;
    private final String host;
    private final int port;
    
    public CacheServiceClient(String host, int port) {
        this.host = host;
        this.port = port;
        
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
        
        this.blockingStub = CacheServiceGrpc.newBlockingStub(channel);
        
        logger.info("Created CacheServiceClient for {}:{}", host, port);
    }
    
    public GetSeriesResponse getSeries(String sensorId, long startTime, long endTime, 
                                        List<String> attributes) {
        GetSeriesRequest request = GetSeriesRequest.newBuilder()
            .setSensorId(sensorId)
            .setStartTime(startTime)
            .setEndTime(endTime)
            .addAllAttributes(attributes)
            .build();
        
        try {
            GetSeriesResponse response = blockingStub.getSeries(request);
            logger.debug("getSeries response for sensorId: {}, success: {}", 
                sensorId, response.getSuccess());
            return response;
        } catch (io.grpc.StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                logger.warn("Service unavailable at {}:{} - connection refused", host, port);
                throw new RuntimeException("Service unavailable: " + e.getMessage(), e);
            }
            logger.error("Failed to get series for sensorId: {}", sensorId, e);
            throw new RuntimeException("Failed to get series: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to get series for sensorId: {}", sensorId, e);
            throw new RuntimeException("Failed to get series: " + e.getMessage(), e);
        }
    }
    
    public FillSeriesResponse fillSeries(String sensorId, List<DataPoint> dataPoints) {
        FillSeriesRequest request = FillSeriesRequest.newBuilder()
            .setSensorId(sensorId)
            .addAllDataPoints(dataPoints)
            .build();
        
        try {
            FillSeriesResponse response = blockingStub.fillSeries(request);
            logger.debug("fillSeries response for sensorId: {}, success: {}, filledCount: {}", 
                sensorId, response.getSuccess(), response.getFilledCount());
            return response;
        } catch (io.grpc.StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                logger.warn("Service unavailable at {}:{} - connection refused", host, port);
                throw new RuntimeException("Service unavailable: " + e.getMessage(), e);
            }
            logger.error("Failed to fill series for sensorId: {}", sensorId, e);
            throw new RuntimeException("Failed to fill series: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to fill series for sensorId: {}", sensorId, e);
            throw new RuntimeException("Failed to fill series: " + e.getMessage(), e);
        }
    }
    
    public HealthCheckResponse healthCheck() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
        
        try {
            HealthCheckResponse response = blockingStub.healthCheck(request);
            logger.debug("healthCheck response for {}:{}, healthy: {}", 
                host, port, response.getHealthy());
            return response;
        } catch (io.grpc.StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                logger.warn("Service {}:{} is offline - connection refused", host, port);
                return HealthCheckResponse.newBuilder()
                    .setHealthy(false)
                    .setStatus("offline")
                    .build();
            }
            logger.error("Failed health check for {}:{}", host, port, e);
            return HealthCheckResponse.newBuilder()
                .setHealthy(false)
                .setStatus("error: " + e.getStatus().getCode().name())
                .build();
        } catch (Exception e) {
            logger.error("Failed health check for {}:{}", host, port, e);
            return HealthCheckResponse.newBuilder()
                .setHealthy(false)
                .setStatus("error")
                .build();
        }
    }
    
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void shutdown() throws InterruptedException {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            logger.info("Shutdown CacheServiceClient for {}:{}", host, port);
        } catch (InterruptedException e) {
            logger.error("Interrupted while shutting down client for {}:{}", host, port, e);
            throw e;
        }
    }
}
