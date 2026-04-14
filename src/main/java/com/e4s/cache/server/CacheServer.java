package com.e4s.cache.server;

import com.e4s.cache.grpc.CacheServiceGrpc;
import com.e4s.cache.lock.DistributedLockManager;
import com.e4s.cache.model.ChunkManager;
import com.e4s.cache.model.CompressedChunkManager;
import com.e4s.cache.model.ThreadSafeCompressedChunkManager;
import com.e4s.cache.service.CacheBackEnd;
import com.e4s.cache.service.CacheServiceImpl;
import com.e4s.cache.service.DatabaseCacheBackEnd;
import com.e4s.cache.service.RedisBackEnd;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class CacheServer {
    private static final Logger logger = LoggerFactory.getLogger(CacheServer.class);
    
    private final Server server;
    private final ThreadSafeCompressedChunkManager chunkManager;
    private final DistributedLockManager lockManager;
    private final CacheBackEnd backEnd;
    
    public CacheServer(int port, 
                       int maxChunks,
                       int chunkIntervalHours,
                       long maxMemoryBytes,
                       String redisHost,
                       int redisPort) {
        this.chunkManager = new ThreadSafeCompressedChunkManager(maxChunks, chunkIntervalHours, maxMemoryBytes);
        this.backEnd = new DatabaseCacheBackEnd(new RedisBackEnd());
        this.lockManager = new DistributedLockManager(
            createJedisPool(redisHost, redisPort), "e4s_cache");
        
        this.server = ServerBuilder.forPort(port)
            .addService(new CacheServiceImpl(chunkManager, lockManager, backEnd))
            .build();
    }
    
    private redis.clients.jedis.JedisPool createJedisPool(String host, int port) {
        return new redis.clients.jedis.JedisPool(host, port);
    }
    
    public void start() throws IOException {
        server.start();
        logger.info("Cache Server started, listening on port {}", server.getPort());
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down gRPC server due to JVM shutdown");
            CacheServer.this.stop();
        }));
    }
    
    public void stop() {
        if (server != null && !server.isTerminated()) {
            try {
                server.shutdown();
                server.awaitTermination(30, TimeUnit.SECONDS);
                logger.info("Cache Server stopped");
            } catch (InterruptedException e) {
                logger.error("Error stopping server", e);
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public void awaitTermination() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public ThreadSafeCompressedChunkManager getChunkManager() {
        return chunkManager;
    }
    
    public static void main(String[] args) {
        try {
            int port = 9090;
            int maxChunks = 2_000_000;              // 2 million chunks
            int chunkIntervalHours = 24;             // 1-day chunks
            long maxMemoryBytes = 100L * 1024 * 1024 * 1024; // 100GB
            String redisHost = "localhost";
            int redisPort = 6379;
            
            CacheServer server = new CacheServer(
                port, maxChunks, chunkIntervalHours, maxMemoryBytes, 
                redisHost, redisPort);
            
            server.start();
            server.awaitTermination();
            
        } catch (Exception e) {
            logger.error("Failed to start cache server", e);
            System.exit(1);
        }
    }
}