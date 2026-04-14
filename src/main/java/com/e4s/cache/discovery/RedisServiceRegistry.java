package com.e4s.cache.discovery;

import com.e4s.cache.config.CacheServiceConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RedisServiceRegistry implements IServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(RedisServiceRegistry.class);
    private static final String SERVICE_KEY_PREFIX = "e4s:service:";
    private static final int SERVICE_TTL_SECONDS = 30;
    
    private final JedisPool jedisPool;
    private final ServiceInstance localService;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    
    public RedisServiceRegistry(ServiceInstance localService, String redisHost, int redisPort) {
        this.localService = localService;
        this.gson = new Gson();
        
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        
        this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        
        logger.info("Created RedisServiceRegistry for service: {}", localService.getId());
    }
    
    public void start() {
        if (running) {
            logger.warn("RedisServiceRegistry already running");
            return;
        }
        
        running = true;
        
        // Register local service
        registerService();
        
        // Schedule periodic registration refresh (every 15 seconds, TTL is 30 seconds)
        scheduler.scheduleAtFixedRate(this::registerService, 15, 15, TimeUnit.SECONDS);
        
        // Add shutdown hook to unregister service on JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered, unregistering service: {}", localService.getId());
            unregisterService();
        }));
        
        logger.info("Started RedisServiceRegistry for service: {}", localService.getId());
    }
    
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Unregister service with shutdown status
        unregisterService();
        
        jedisPool.close();
        
        logger.info("Stopped RedisServiceRegistry for service: {}", localService.getId());
    }
    
    private void registerService() {
        try (var jedis = jedisPool.getResource()) {
            String serviceKey = SERVICE_KEY_PREFIX + localService.getId();
            JsonObject serviceJson = new JsonObject();
            serviceJson.addProperty("id", localService.getId());
            serviceJson.addProperty("group", localService.getGroup());
            serviceJson.addProperty("host", localService.getHost());
            serviceJson.addProperty("port", localService.getPort());
            serviceJson.addProperty("status", "healthy");
            serviceJson.addProperty("lastHeartbeat", System.currentTimeMillis());
            
            jedis.setex(serviceKey, SERVICE_TTL_SECONDS, gson.toJson(serviceJson));
            
            logger.debug("Registered service: {} with TTL: {}s", localService.getId(), SERVICE_TTL_SECONDS);
        } catch (Exception e) {
            logger.error("Failed to register service: {}", localService.getId(), e);
        }
    }
    
    private void unregisterService() {
        try (var jedis = jedisPool.getResource()) {
            String serviceKey = SERVICE_KEY_PREFIX + localService.getId();
            
            // Update status to shutdown before removing
            JsonObject serviceJson = new JsonObject();
            serviceJson.addProperty("id", localService.getId());
            serviceJson.addProperty("group", localService.getGroup());
            serviceJson.addProperty("host", localService.getHost());
            serviceJson.addProperty("port", localService.getPort());
            serviceJson.addProperty("status", "shutdown");
            serviceJson.addProperty("lastHeartbeat", System.currentTimeMillis());
            
            // Set with a short TTL (5 seconds) to allow other services to see the shutdown status
            jedis.setex(serviceKey, 5, gson.toJson(serviceJson));
            
            logger.info("Unregistered service: {} with status: shutdown", localService.getId());
        } catch (Exception e) {
            logger.error("Failed to unregister service: {}", localService.getId(), e);
        }
    }
    
    public List<ServiceInstance> getAllServices() {
        List<ServiceInstance> services = new ArrayList<>();
        
        try (var jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(SERVICE_KEY_PREFIX + "*");
            
            if (keys != null) {
                for (String key : keys) {
                    String serviceJson = jedis.get(key);
                    if (serviceJson != null) {
                        JsonObject json = gson.fromJson(serviceJson, JsonObject.class);
                        String status = json.has("status") ? json.get("status").getAsString() : "unknown";
                        
                        // Skip services that have shut down
                        if ("shutdown".equals(status)) {
                            logger.debug("Skipping shutdown service: {}", json.get("id").getAsString());
                            continue;
                        }
                        
                        ServiceInstance service = new ServiceInstance(
                            json.get("id").getAsString(),
                            json.get("group").getAsString(),
                            json.get("host").getAsString(),
                            json.get("port").getAsInt()
                        );
                        services.add(service);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get all services", e);
        }
        
        return services;
    }
    
    public List<ServiceInstance> getPeerServices() {
        List<ServiceInstance> allServices = getAllServices();
        List<ServiceInstance> peers = new ArrayList<>();
        
        for (ServiceInstance service : allServices) {
            if (!service.getId().equals(localService.getId())) {
                peers.add(service);
            }
        }
        
        return peers;
    }
    
    public ServiceInstance getService(String serviceId) {
        try (var jedis = jedisPool.getResource()) {
            String serviceKey = SERVICE_KEY_PREFIX + serviceId;
            String serviceJson = jedis.get(serviceKey);
            
            if (serviceJson != null) {
                JsonObject json = gson.fromJson(serviceJson, JsonObject.class);
                return new ServiceInstance(
                    json.get("id").getAsString(),
                    json.get("group").getAsString(),
                    json.get("host").getAsString(),
                    json.get("port").getAsInt()
                );
            }
        } catch (Exception e) {
            logger.error("Failed to get service: {}", serviceId, e);
        }
        
        return null;
    }
    
    public int getServiceCount() {
        try (var jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(SERVICE_KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            logger.error("Failed to get service count", e);
            return 0;
        }
    }
    
    public ServiceInstance getLocalService() {
        return localService;
    }
    
    public JedisPool getJedisPool() {
        return jedisPool;
    }
}