package com.e4s.cache.discovery;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServiceEventListener {
    private final List<ServiceLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private final List<ServiceHealthListener> healthListeners = new CopyOnWriteArrayList<>();
    
    public void addLifecycleListener(ServiceLifecycleListener listener) {
        lifecycleListeners.add(listener);
    }
    
    public void removeLifecycleListener(ServiceLifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }
    
    public void addHealthListener(ServiceHealthListener listener) {
        healthListeners.add(listener);
    }
    
    public void removeHealthListener(ServiceHealthListener listener) {
        healthListeners.remove(listener);
    }
    
    public void fireServiceRegistered(ServiceInstance service) {
        for (ServiceLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onServiceRegistered(service);
            } catch (Exception e) {
                System.err.println("Error in lifecycle listener: " + e.getMessage());
            }
        }
    }
    
    public void fireServiceUnregistered(ServiceInstance service) {
        for (ServiceLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onServiceUnregistered(service);
            } catch (Exception e) {
                System.err.println("Error in lifecycle listener: " + e.getMessage());
            }
        }
    }
    
    public void fireServiceHealthChanged(ServiceInstance service, boolean healthy, String reason) {
        for (ServiceHealthListener listener : healthListeners) {
            try {
                listener.onServiceHealthChanged(service, healthy, reason);
            } catch (Exception e) {
                System.err.println("Error in health listener: " + e.getMessage());
            }
        }
    }
    
    public interface ServiceLifecycleListener {
        void onServiceRegistered(ServiceInstance service);
        void onServiceUnregistered(ServiceInstance service);
    }
    
    public interface ServiceHealthListener {
        void onServiceHealthChanged(ServiceInstance service, boolean healthy, String reason);
    }
}
