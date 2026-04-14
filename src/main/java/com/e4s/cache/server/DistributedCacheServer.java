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
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DistributedCacheServer {
    private static final Logger logger = LoggerFactory.getLogger(DistributedCacheServer.class);
    
    private final Server server;
    private final ThreadSafeCompressedChunkManager chunkManager;
    private final DistributedLockManager lockManager;
    private final CacheBackEnd backEnd;
    private final RedisServiceRegistry serviceRegistry;
    private final ConsistentHashPartitioner partitioner;
    private final CacheServiceClientPool clientPool;
    private final ServiceInstance localService;
    private final DistributedChunkManager distributedChunkManager;
    private final ScheduledExecutorService serviceDiscoveryScheduler;
    private volatile boolean running = false;
    
    public DistributedCacheServer(CacheServiceConfig config) {
        CacheServiceConfig.CacheConfig cacheConfig = config.getCache();
        CacheServiceConfig.RedisConfig redisConfig = config.getRedis();
        
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
        
        // Use Redis-based service registry
        this.serviceRegistry = new RedisServiceRegistry(
            localService, 
            redisConfig.getHost(), 
            redisConfig.getPort());
        
        // Get initial list of services from Redis
        List<ServiceInstance> allServices = serviceRegistry.getAllServices();
        this.partitioner = new ConsistentHashPartitioner(allServices);
        this.clientPool = new CacheServiceClientPool(allServices);
        
        this.serviceDiscoveryScheduler = Executors.newSingleThreadScheduledExecutor();
        
        this.distributedChunkManager = new DistributedChunkManager(
            serviceRegistry, partitioner, clientPool, localService);
        
        DistributedCacheServiceImpl serviceImpl = new DistributedCacheServiceImpl(
            chunkManager, lockManager, backEnd, serviceRegistry, 
            partitioner, clientPool, localService);
        
        this.server = ServerBuilder.forPort(config.getPort())
            .addService(serviceImpl)
            .build();
        
        logger.info("Created DistributedCacheServer: {}@{}:{} with Redis-based service discovery", 
            config.getServiceId(), config.getHost(), config.getPort());
    }
    
    private redis.clients.jedis.JedisPool createJedisPool(String host, int port) {
        return new redis.clients.jedis.JedisPool(host, port);
    }
    
    public void start() throws IOException {
        server.start();
        
        // Start Redis service registry
        serviceRegistry.start();
        
        running = true;
        
        // Schedule periodic service discovery updates
        serviceDiscoveryScheduler.scheduleAtFixedRate(
            this::updateServiceDiscovery, 
            5, 5, TimeUnit.SECONDS);
        
        logger.info("Distributed Cache Server started, listening on port {}", server.getPort());
        logger.info("Service ID: {}, Group: {}", localService.getId(), localService.getGroup());
        logger.info("Redis-based service discovery enabled");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down distributed cache server due to JVM shutdown");
            DistributedCacheServer.this.stop();
        }));
    }
    
    private void updateServiceDiscovery() {
        if (!running) {
            return;
        }
        
        try {
            List<ServiceInstance> currentServices = serviceRegistry.getAllServices();
            int previousCount = partitioner.getServiceCount();
            
            // Update partitioner if services changed
            if (currentServices.size() != previousCount) {
                logger.info("Service count changed: {} -> {}, updating partitioner", 
                    previousCount, currentServices.size());
                
                partitioner.updateServices(currentServices);
                
                // Update client pool with new services
                for (ServiceInstance service : currentServices) {
                    if (!service.getId().equals(localService.getId())) {
                        clientPool.getOrCreateClient(service);
                    }
                }
                
                logger.info("Updated service discovery: {} services available", currentServices.size());
            }
        } catch (Exception e) {
            logger.error("Failed to update service discovery", e);
        }
    }
    
    public void stop() {
        logger.info("Stopping distributed cache server...");
        
        running = false;
        
        // Stop service discovery scheduler
        serviceDiscoveryScheduler.shutdown();
        try {
            if (!serviceDiscoveryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                serviceDiscoveryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            serviceDiscoveryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Stop Redis service registry
        serviceRegistry.stop();
        
        // Stop gRPC server
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
    
    public RedisServiceRegistry getServiceRegistry() {
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