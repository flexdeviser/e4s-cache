package com.e4s.cache.client;

import com.e4s.cache.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.e4s.cache.model.AttributeDef;
import com.e4s.cache.model.ThreadSafeCompressedChunkManager;

public class CacheClient {
    private final ManagedChannel channel;
    private final CacheServiceGrpc.CacheServiceBlockingStub blockingStub;
    private final CacheServiceGrpc.CacheServiceStub asyncStub;
    
    public CacheClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
        
        this.blockingStub = CacheServiceGrpc.newBlockingStub(channel);
        this.asyncStub = CacheServiceGrpc.newStub(channel);
    }
    
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    
    public void getSeries(String sensorId, long startTime, long endTime, List<String> attributes) {
        GetSeriesRequest request = GetSeriesRequest.newBuilder()
            .setSensorId(sensorId)
            .setStartTime(startTime)
            .setEndTime(endTime)
            .addAllAttributes(attributes)
            .build();
        
        GetSeriesResponse response = blockingStub.getSeries(request);
        
        if (response.getSuccess()) {
            System.out.printf("Successfully retrieved %d attributes for sensor %s\n",
                response.getAttributeDataCount(), sensorId);
            
            for (AttributeData data : response.getAttributeDataList()) {
                System.out.printf("Attribute: %s, values: %s\n",
                    data.getAttribute(), data.getValuesList());
            }
        } else {
            System.err.printf("Failed to get series: %s\n", response.getError());
        }
    }
    
    public void fillSeries(String sensorId, List<DataPoint> dataPoints) {
        FillSeriesRequest request = FillSeriesRequest.newBuilder()
            .setSensorId(sensorId)
            .addAllDataPoints(dataPoints)
            .build();
        
        FillSeriesResponse response = blockingStub.fillSeries(request);
        
        if (response.getSuccess()) {
            System.out.printf("Successfully filled %d data points for sensor %s\n",
                response.getFilledCount(), sensorId);
        } else {
            System.err.printf("Failed to fill series: %s\n", response.getError());
        }
    }
    
    public void healthCheck() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
        HealthCheckResponse response = blockingStub.healthCheck(request);
        
        if (response.getHealthy()) {
            System.out.println("Server health check passed: " + response.getStatus());
        } else {
            System.err.println("Server health check failed: " + response.getStatus());
        }
    }
    
    public static void main(String[] args) {
        try {
            CacheClient client = new CacheClient("localhost", 9090);
            
            System.out.println("=== Health Check ===");
            client.healthCheck();
            
            System.out.println("\n=== Get Series ===");
            long startTime = System.currentTimeMillis() - 86400000L * 7; // 7 days ago
            long endTime = System.currentTimeMillis();
            client.getSeries("sensor-001", startTime, endTime, 
                List.of(AttributeDef.VOLTAGE, AttributeDef.CURRENT));
            
            System.out.println("\n=== Fill Series ===");
            List<DataPoint> dataPoints = List.of(
                DataPoint.newBuilder()
                    .setTimestamp(System.currentTimeMillis())
                    .setAttribute(AttributeDef.VOLTAGE)
                    .setValue(220.5)
                    .build(),
                DataPoint.newBuilder()
                    .setTimestamp(System.currentTimeMillis())
                    .setAttribute(AttributeDef.CURRENT)
                    .setValue(10.2)
                    .build()
            );
            client.fillSeries("sensor-002", dataPoints);
            
            System.out.println("\n=== Chunk Info ===");
            com.e4s.cache.server.CacheServer server = new com.e4s.cache.server.CacheServer(
                9090, 2_000_000, 24, 100L * 1024 * 1024 * 1024, "localhost", 6379);
            com.e4s.cache.model.ThreadSafeCompressedChunkManager chunkManager = server.getChunkManager();
            
            System.out.printf("Total chunks: %d%n", chunkManager.getChunkCount());
            System.out.printf("Total data points: %d%n", chunkManager.getTotalDataPoints());
            System.out.printf("Total compressed bytes: %d MB%n", chunkManager.getTotalCompressedBytes() / (1024 * 1024));
            System.out.printf("Total uncompressed bytes: %d MB%n", chunkManager.getTotalUncompressedBytes() / (1024 * 1024));
            System.out.printf("Compression ratio: %.2fx%n", chunkManager.getCompressionRatio());
            System.out.printf("Memory savings: %.2f%%%n", chunkManager.getMemorySavings());
            System.out.println("Multi-threading: Enabled (per-sensor ReadWriteLock + lock striping)");
            
            chunkManager.getAllChunkInfo().forEach((sensorId, info) -> 
                System.out.printf("Sensor: %s, Chunk: %d, Points: %d, Compressed: %d KB, Ratio: %.2fx, Expired: %b%n",
                    sensorId, info.getChunkId(), info.getDataPointCount(), 
                    info.getCompressedSize() / 1024, info.getCompressionRatio(), info.isExpired()));
            
            client.shutdown();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}