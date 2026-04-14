package com.e4s.cache.discovery;

import java.util.Objects;

public class ServiceInstance {
    private final String id;
    private final String group;
    private final String host;
    private final int port;
    private volatile boolean healthy;
    private volatile boolean healthChecked;
    private volatile long lastHealthCheck;
    
    public ServiceInstance(String id, String group, String host, int port) {
        this.id = id;
        this.group = group;
        this.host = host;
        this.port = port;
        this.healthy = false;
        this.healthChecked = false;
        this.lastHealthCheck = 0;
    }
    
    public String getId() {
        return id;
    }
    
    public String getGroup() {
        return group;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    public boolean isHealthy() {
        return healthy;
    }
    
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
        this.healthChecked = true;
        this.lastHealthCheck = System.currentTimeMillis();
    }
    
    public boolean isHealthChecked() {
        return healthChecked;
    }
    
    public long getLastHealthCheck() {
        return lastHealthCheck;
    }
    
    public String getAddress() {
        return host + ":" + port;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInstance that = (ServiceInstance) o;
        return port == that.port && 
               Objects.equals(id, that.id) && 
               Objects.equals(group, that.group) && 
               Objects.equals(host, that.host);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, group, host, port);
    }
    
    @Override
    public String toString() {
        return "ServiceInstance{" +
               "id='" + id + '\'' +
               ", group='" + group + '\'' +
               ", host='" + host + '\'' +
               ", port=" + port +
               ", healthy=" + healthy +
               '}';
    }
}
