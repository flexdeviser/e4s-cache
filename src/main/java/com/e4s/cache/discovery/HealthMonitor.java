package com.e4s.cache.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthMonitor {
    private static final Logger logger = LoggerFactory.getLogger(HealthMonitor.class);
    
    private static final int MAX_RETRY_COUNT = 3;
    
    private final ServiceRegistry serviceRegistry;
    private final CacheServiceClientPool clientPool;
    private final ScheduledExecutorService scheduler;
    private final long checkIntervalMs;
    private volatile boolean running = false;
    private volatile boolean eventDrivenEnabled = true;
    
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
        
        logger.info("Created HealthMonitor with check interval: {}ms, max retries: {}, event-driven: {}", 
            checkIntervalMs, MAX_RETRY_COUNT, eventDrivenEnabled);
    }
    
    public void setEventDrivenEnabled(boolean enabled) {
        this.eventDrivenEnabled = enabled;
        logger.info("Event-driven health monitoring: {}", enabled);
    }
    
    public void start() {
        if (running) {
            logger.warn("HealthMonitor is already running");
            return;
        }
        
        running = true;
        
        if (!eventDrivenEnabled) {
            scheduler.scheduleAtFixedRate(this::performHealthChecks, 
                checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
            logger.info("Started periodic health monitoring with interval: {}ms", checkIntervalMs);
        } else {
            logger.info("Event-driven health monitoring enabled, periodic checks disabled");
        }
        
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
                } else {
                    service.resetConsecutiveFailures();
                }
            } else {
                int failures = service.incrementConsecutiveFailures();
                String status = response.getStatus();
                String reason = status;
                if (status.equals("offline")) {
                    reason = "disconnected";
                } else if (status.startsWith("error:")) {
                    reason = "error: " + status.substring(7);
                }
                
                if (failures >= MAX_RETRY_COUNT) {
                    if (service.isHealthy()) {
                        serviceRegistry.markServiceUnhealthy(service.getId());
                        logger.warn("Service {} marked as unhealthy after {} failures: {}", 
                            service.getId(), failures, reason);
                    } else {
                        logger.debug("Service {} still unhealthy ({} failures): {}", 
                            service.getId(), failures, reason);
                    }
                } else {
                    logger.debug("Service {} health check failed ({}/{}): {}", 
                        service.getId(), failures, MAX_RETRY_COUNT, reason);
                }
            }
            
        } catch (java.lang.IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Not started")) {
                logger.debug("Service {} gRPC channel not ready yet, will retry on next check", service.getId());
                return;
            }
            handleHealthCheckException(service, e);
        } catch (Exception e) {
            handleHealthCheckException(service, e);
        }
    }
    
    private void handleHealthCheckException(ServiceInstance service, Exception e) {
        int failures = service.incrementConsecutiveFailures();
        
        if (failures >= MAX_RETRY_COUNT) {
            if (service.isHealthy()) {
                serviceRegistry.markServiceUnhealthy(service.getId());
                logger.warn("Service {} marked as unhealthy after {} failures: disconnected", 
                    service.getId(), failures);
            } else {
                logger.debug("Service {} still unhealthy ({} failures): disconnected", 
                    service.getId(), failures);
            }
        } else {
            logger.debug("Service {} health check failed ({}/{}): disconnected", 
                service.getId(), failures, MAX_RETRY_COUNT);
        }
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }
    
    public static int getMaxRetryCount() {
        return MAX_RETRY_COUNT;
    }
}
