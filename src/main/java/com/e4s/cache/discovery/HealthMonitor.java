package com.e4s.cache.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthMonitor {
    private static final Logger logger = LoggerFactory.getLogger(HealthMonitor.class);
    
    private final ServiceRegistry serviceRegistry;
    private final CacheServiceClientPool clientPool;
    private final ScheduledExecutorService scheduler;
    private final long checkIntervalMs;
    private volatile boolean running = false;
    
    public HealthMonitor(ServiceRegistry serviceRegistry, 
                        CacheServiceClientPool clientPool,
                        long checkIntervalMs) {
        this.serviceRegistry = serviceRegistry;
        this.clientPool = clientPool;
        this.checkIntervalMs = checkIntervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "health-monitor");
            thread.setDaemon(true);
            return thread;
        });
        
        logger.info("Created HealthMonitor with check interval: {}ms", checkIntervalMs);
    }
    
    public void start() {
        if (running) {
            logger.warn("HealthMonitor is already running");
            return;
        }
        
        running = true;
        scheduler.scheduleAtFixedRate(this::performHealthChecks, 
            checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        
        logger.info("Started HealthMonitor");
    }
    
    public void stop() {
        if (!running) {
            logger.warn("HealthMonitor is not running");
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
        
        logger.info("Stopped HealthMonitor");
    }
    
    private void performHealthChecks() {
        try {
            List<ServiceInstance> services = serviceRegistry.getAllServices();
            
            logger.debug("Performing health checks for {} services", services.size());
            
            for (ServiceInstance service : services) {
                checkServiceHealth(service);
            }
            
            int healthyCount = serviceRegistry.getHealthyServiceCount();
            logger.debug("Health check completed. Healthy services: {}/{}", 
                healthyCount, services.size());
            
        } catch (Exception e) {
            logger.error("Error during health checks", e);
        }
    }
    
    private void checkServiceHealth(ServiceInstance service) {
        try {
            var response = clientPool.healthCheck(service);
            
            if (response.getHealthy()) {
                if (!service.isHealthy()) {
                    serviceRegistry.markServiceHealthy(service.getId());
                    logger.info("Service {} recovered and is now healthy", service.getId());
                }
            } else {
                if (service.isHealthy()) {
                    serviceRegistry.markServiceUnhealthy(service.getId());
                    logger.warn("Service {} marked as unhealthy: {}", 
                        service.getId(), response.getStatus());
                }
            }
            
        } catch (Exception e) {
            if (service.isHealthy()) {
                serviceRegistry.markServiceUnhealthy(service.getId());
                logger.warn("Service {} marked as unhealthy due to exception: {}", 
                    service.getId(), e.getMessage());
            }
        }
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }
}
