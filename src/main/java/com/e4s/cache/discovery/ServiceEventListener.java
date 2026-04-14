package com.e4s.cache.discovery;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServiceEventListener {
    private final List<ServiceLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private final List<ServiceHealthListener> healthListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    
    public void start() {
        started.set(true);
    }
    
    public void stop() {
        started.set(false);
    }
    
    public boolean isStarted() {
        return started.get();
    }
    
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
        if (!started.get()) {
            return;
        }
        
        for (ServiceLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onServiceRegistered(service);
            } catch (Exception e) {
                System.err.println("Error in lifecycle listener: " + e.getMessage());
            }
        }
    }
    
    public void fireServiceUnregistered(ServiceInstance service) {
        if (!started.get()) {
            return;
        }
        
        for (ServiceLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onServiceUnregistered(service);
            } catch (Exception e) {
                System.err.println("Error in lifecycle listener: " + e.getMessage());
            }
        }
    }
    
    public void fireServiceHealthChanged(ServiceInstance service, boolean healthy, String reason) {
        if (!started.get()) {
            return;
        }
        
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
