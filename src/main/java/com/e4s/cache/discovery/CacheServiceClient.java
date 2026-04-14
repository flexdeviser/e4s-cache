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
    private final ServiceInstance service;
    private volatile boolean connected = false;
    
    public CacheServiceClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.service = new ServiceInstance("client-" + host + ":" + port, "client", host, port);
        
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
        
        this.blockingStub = CacheServiceGrpc.newBlockingStub(channel);
        
        logger.info("Created CacheServiceClient for {}:{}", host, port);
    }
    
    public CacheServiceClient(String host, int port, ServiceEventListener eventListener) {
        this.host = host;
        this.port = port;
        this.service = new ServiceInstance("client-" + host + ":" + port, "client", host, port);
        
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .intercept(new ConnectionStateListener(service, eventListener))
            .build();
        
        this.blockingStub = CacheServiceGrpc.newBlockingStub(channel);
        
        logger.info("Created CacheServiceClient with connection listener for {}:{}, channel state: {}", 
            host, port, channel.getState(false));
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
            logger.debug("Making health check call to {}:{}, channel state: {}", 
                host, port, channel.getState(false));
            
            // Use withWaitForReady() to wait for the channel to be ready
            // Use withDeadlineAfter() to give the channel time to connect
            HealthCheckResponse response = blockingStub
                .withWaitForReady()
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .healthCheck(request);
            
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
            if (e.getStatus().getCode() == io.grpc.Status.Code.DEADLINE_EXCEEDED) {
                logger.warn("Service {}:{} health check timed out", host, port);
                return HealthCheckResponse.newBuilder()
                    .setHealthy(false)
                    .setStatus("timeout")
                    .build();
            }
            logger.error("Failed health check for {}:{}, status: {}", host, port, e.getStatus(), e);
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
    
    public boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            logger.debug("Waiting for channel to {}:{} to be ready, current state: {}", 
                host, port, channel.getState(false));
            
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (System.nanoTime() < deadline) {
                io.grpc.ConnectivityState state = channel.getState(false);
                if (state == io.grpc.ConnectivityState.READY) {
                    logger.debug("Channel to {}:{} is ready", host, port);
                    return true;
                }
                if (state == io.grpc.ConnectivityState.SHUTDOWN || 
                    state == io.grpc.ConnectivityState.TRANSIENT_FAILURE) {
                    logger.warn("Channel to {}:{} failed to connect, state: {}", host, port, state);
                    return false;
                }
                Thread.sleep(100);
            }
            
            logger.warn("Channel to {}:{} not ready after timeout, final state: {}", 
                host, port, channel.getState(false));
            return false;
        } catch (Exception e) {
            logger.error("Error waiting for channel to {}:{} to be ready", host, port, e);
            return false;
        }
    }
    
    public io.grpc.ConnectivityState getState() {
        return channel.getState(false);
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
