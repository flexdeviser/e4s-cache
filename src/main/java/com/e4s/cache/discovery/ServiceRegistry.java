package com.e4s.cache.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);
    
    private final Map<String, ServiceInstance> services = new ConcurrentHashMap<>();
    private final Map<String, List<ServiceInstance>> serviceGroups = new ConcurrentHashMap<>();
    private volatile HealthMonitor healthMonitor;
    private final ServiceEventListener eventListener;
    
    public ServiceRegistry() {
        this.eventListener = new ServiceEventListener();
        setupEventListeners();
    }
    
    private void setupEventListeners() {
        eventListener.addHealthListener((service, healthy, reason) -> {
            if (healthy) {
                if (!service.isHealthy()) {
                    service.setHealthy(true);
                    logger.info("Service {} health changed to healthy: {}", service.getId(), reason);
                }
            } else {
                if (service.isHealthy()) {
                    service.setHealthy(false);
                    logger.warn("Service {} health changed to unhealthy: {}", service.getId(), reason);
                }
            }
        });
    }
    
    public ServiceEventListener getEventListener() {
        return eventListener;
    }
    
    public void setHealthMonitor(HealthMonitor healthMonitor) {
        this.healthMonitor = healthMonitor;
    }
    
    public void registerService(ServiceInstance service) {
        services.put(service.getId(), service);
        
        serviceGroups.computeIfAbsent(service.getGroup(), k -> new CopyOnWriteArrayList<>()).add(service);
        
        logger.info("Registered service: {} in group: {}", service.getId(), service.getGroup());
        
        // Event listener will handle health detection automatically when connections are established
    }
    
    public void unregisterService(String serviceId) {
        ServiceInstance service = services.remove(serviceId);
        if (service != null) {
            List<ServiceInstance> group = serviceGroups.get(service.getGroup());
            if (group != null) {
                group.remove(service);
                if (group.isEmpty()) {
                    serviceGroups.remove(service.getGroup());
                }
            }
            logger.info("Unregistered service: {}", serviceId);
        }
    }
    
    public ServiceInstance getService(String serviceId) {
        return services.get(serviceId);
    }
    
    public List<ServiceInstance> getAllServices() {
        return new ArrayList<>(services.values());
    }
    
    public List<ServiceInstance> getServicesByGroup(String group) {
        return new ArrayList<>(serviceGroups.getOrDefault(group, new ArrayList<>()));
    }
    
    public List<ServiceInstance> getHealthyServices() {
        return services.values().stream()
            .filter(ServiceInstance::isHealthy)
            .collect(java.util.stream.Collectors.toList());
    }
    
    public void markServiceUnhealthy(String serviceId) {
        ServiceInstance service = services.get(serviceId);
        if (service != null) {
            service.setHealthy(false);
            logger.warn("Marked service as unhealthy: {}", serviceId);
        }
    }
    
    public void markServiceHealthy(String serviceId) {
        ServiceInstance service = services.get(serviceId);
        if (service != null) {
            service.setHealthy(true);
            logger.info("Marked service as healthy: {}", serviceId);
        }
    }
    
    public int getServiceCount() {
        return services.size();
    }
    
    public int getHealthyServiceCount() {
        return (int) services.values().stream()
            .filter(ServiceInstance::isHealthy)
            .count();
    }
    
    public int getHealthCheckedServiceCount() {
        return (int) services.values().stream()
            .filter(ServiceInstance::isHealthChecked)
            .count();
    }
    
    public int getUnknownHealthServiceCount() {
        return (int) services.values().stream()
            .filter(s -> !s.isHealthChecked())
            .count();
    }
}