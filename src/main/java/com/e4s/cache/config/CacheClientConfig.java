package com.e4s.cache.config;

import java.util.ArrayList;
import java.util.List;

public class CacheClientConfig {
    private String clientId;
    private List<ServiceConfig> services;
    
    public CacheClientConfig() {
        this.services = new ArrayList<>();
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public List<ServiceConfig> getServices() {
        return services;
    }
    
    public void setServices(List<ServiceConfig> services) {
        this.services = services;
    }
    
    public static class ServiceConfig {
        private String id;
        private String group;
        private String host;
        private int port;
        
        public ServiceConfig() {
        }
        
        public ServiceConfig(String id, String group, String host, int port) {
            this.id = id;
            this.group = group;
            this.host = host;
            this.port = port;
        }
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getGroup() {
            return group;
        }
        
        public void setGroup(String group) {
            this.group = group;
        }
        
        public String getHost() {
            return host;
        }
        
        public void setHost(String host) {
            this.host = host;
        }
        
        public int getPort() {
            return port;
        }
        
        public void setPort(int port) {
            this.port = port;
        }
    }
}
