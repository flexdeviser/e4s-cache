package com.e4s.cache.server;

import com.e4s.cache.config.CacheServiceConfig;
import com.e4s.cache.config.ConfigLoader;
import com.e4s.cache.discovery.*;
import com.e4s.cache.grpc.CacheServiceGrpc;
import com.e4s.cache.lock.DistributedLockManager;
import com.e4s.cache.model.ThreadSafeCompressedChunkManager;
import com.e4s.cache.service.CacheBackEnd;
import com.e4s.cache.service.DatabaseCacheBackEnd;
import com.e4s.cache.service.RedisBackEnd;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DistributedCacheServer {
    private static final Logger logger = LoggerFactory.getLogger(DistributedCacheServer.class);
    
    private final Server server;
    private final ThreadSafeCompressedChunkManager chunkManager;
    private final DistributedLockManager lockManager;
    private final CacheBackEnd backEnd;
    private final ServiceRegistry serviceRegistry;
    private final ConsistentHashPartitioner partitioner;
    private final CacheServiceClientPool clientPool;
    private final ServiceInstance localService;
    private final HealthMonitor healthMonitor;
    private final DistributedChunkManager distributedChunkManager;
    
    public DistributedCacheServer(CacheServiceConfig config) {
        CacheServiceConfig.CacheConfig cacheConfig = config.getCache();
        CacheServiceConfig.RedisConfig redisConfig = config.getRedis();
        CacheServiceConfig.HealthConfig healthConfig = config.getHealth();
        
        this.chunkManager = new ThreadSafeCompressedChunkManager(
            cacheConfig.getMaxChunks(), 
            cacheConfig.getChunkIntervalHours(), 
            cacheConfig.getMaxMemoryBytes());
        this.backEnd = new DatabaseCacheBackEnd(new RedisBackEnd());
        this.lockManager = new DistributedLockManager(
            createJedisPool(redisConfig.getHost(), redisConfig.getPort()), "e4s_cache");
        
        this.localService = new ServiceInstance(
            config.getServiceId(), 
            config.getServiceGroup(), 
            config.getHost(), 
            config.getPort());
        this.serviceRegistry = new ServiceRegistry();
        
        serviceRegistry.registerService(localService);
        
        for (CacheServiceConfig.PeerConfig peerConfig : config.getPeers()) {
            ServiceInstance peer = new ServiceInstance(
                peerConfig.getId(), 
                config.getServiceGroup(), 
                peerConfig.getHost(), 
                peerConfig.getPort());
            serviceRegistry.registerService(peer);
        }
        
        List<ServiceInstance> allServices = serviceRegistry.getAllServices();
        this.partitioner = new ConsistentHashPartitioner(allServices);
        this.clientPool = new CacheServiceClientPool(allServices, serviceRegistry.getEventListener());
        this.healthMonitor = new HealthMonitor(serviceRegistry, clientPool, healthConfig.getCheckIntervalMs());
        
        serviceRegistry.setHealthMonitor(healthMonitor);
        
        this.distributedChunkManager = new DistributedChunkManager(
            serviceRegistry, partitioner, clientPool, localService);
        
        DistributedCacheServiceImpl serviceImpl = new DistributedCacheServiceImpl(
            chunkManager, lockManager, backEnd, serviceRegistry, 
            partitioner, clientPool, localService);
        
        this.server = ServerBuilder.forPort(config.getPort())
            .addService(serviceImpl)
            .build();
        
        logger.info("Created DistributedCacheServer: {}@{}:{} with {} peer services", 
            config.getServiceId(), config.getHost(), config.getPort(), config.getPeers().size());
        logger.info("Event-driven health monitoring enabled");
    }
    
    private redis.clients.jedis.JedisPool createJedisPool(String host, int port) {
        return new redis.clients.jedis.JedisPool(host, port);
    }
    
    public void start() throws IOException {
        server.start();
        
        localService.setHealthy(true);
        
        serviceRegistry.getEventListener().start();
        
        healthMonitor.start();
        
        logger.info("Distributed Cache Server started, listening on port {}", server.getPort());
        logger.info("Service ID: {}, Group: {}", localService.getId(), localService.getGroup());
        logger.info("Total services: {}, Health checked: {}, Healthy: {}, Unknown: {}", 
            serviceRegistry.getServiceCount(),
            serviceRegistry.getHealthCheckedServiceCount(),
            serviceRegistry.getHealthyServiceCount(),
            serviceRegistry.getUnknownHealthServiceCount());
        
        logger.info("Event-driven health monitoring enabled - services will be detected automatically");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down distributed cache server due to JVM shutdown");
            DistributedCacheServer.this.stop();
        }));
    }
    
    public void stop() {
        logger.info("Stopping distributed cache server...");
        
        serviceRegistry.getEventListener().stop();
        
        healthMonitor.stop();
        
        if (server != null && !server.isTerminated()) {
            try {
                server.shutdown();
                server.awaitTermination(30, TimeUnit.SECONDS);
                logger.info("gRPC server stopped");
            } catch (InterruptedException e) {
                logger.error("Error stopping server", e);
                Thread.currentThread().interrupt();
            }
        }
        
        distributedChunkManager.shutdown();
        clientPool.shutdown();
        
        try {
            lockManager.getJedisPool().close();
        } catch (Exception e) {
            logger.error("Error closing Jedis pool", e);
        }
        
        logger.info("Distributed cache server stopped");
    }
    
    public void awaitTermination() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public ThreadSafeCompressedChunkManager getChunkManager() {
        return chunkManager;
    }
    
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }
    
    public ConsistentHashPartitioner getPartitioner() {
        return partitioner;
    }
    
    public ServiceInstance getLocalService() {
        return localService;
    }
    
    public int getServiceCount() {
        return serviceRegistry.getServiceCount();
    }
    
    public int getHealthyServiceCount() {
        return serviceRegistry.getHealthyServiceCount();
    }
    
    public static void main(String[] args) {
        try {
            String configPath = args.length > 0 ? args[0] : null;
            CacheServiceConfig config;
            
            if (configPath != null) {
                config = ConfigLoader.loadServiceConfig(configPath);
            } else {
                config = ConfigLoader.loadServiceConfig();
            }
            
            DistributedCacheServer server = new DistributedCacheServer(config);
            
            server.start();
            server.awaitTermination();
            
        } catch (Exception e) {
            logger.error("Failed to start distributed cache server", e);
            System.exit(1);
        }
    }
}
