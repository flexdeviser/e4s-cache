package com.e4s.cache.config;

import java.util.ArrayList;
import java.util.List;

public class CacheServiceConfig {
    private String serviceId;
    private String serviceGroup;
    private String host;
    private int port;
    
    private CacheConfig cache;
    private RedisConfig redis;
    private HealthConfig health;
    private List<PeerConfig> peers;
    
    public CacheServiceConfig() {
        this.peers = new ArrayList<>();
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }
    
    public String getServiceGroup() {
        return serviceGroup;
    }
    
    public void setServiceGroup(String serviceGroup) {
        this.serviceGroup = serviceGroup;
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
    
    public CacheConfig getCache() {
        return cache;
    }
    
    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }
    
    public RedisConfig getRedis() {
        return redis;
    }
    
    public void setRedis(RedisConfig redis) {
        this.redis = redis;
    }
    
    public HealthConfig getHealth() {
        return health;
    }
    
    public void setHealth(HealthConfig health) {
        this.health = health;
    }
    
    public List<PeerConfig> getPeers() {
        return peers;
    }
    
    public void setPeers(List<PeerConfig> peers) {
        this.peers = peers;
    }
    
    public static class CacheConfig {
        private int maxChunks = 2_000_000;
        private int chunkIntervalHours = 24;
        private long maxMemoryBytes = 100L * 1024 * 1024 * 1024;
        
        public int getMaxChunks() {
            return maxChunks;
        }
        
        public void setMaxChunks(int maxChunks) {
            this.maxChunks = maxChunks;
        }
        
        public int getChunkIntervalHours() {
            return chunkIntervalHours;
        }
        
        public void setChunkIntervalHours(int chunkIntervalHours) {
            this.chunkIntervalHours = chunkIntervalHours;
        }
        
        public long getMaxMemoryBytes() {
            return maxMemoryBytes;
        }
        
        public void setMaxMemoryBytes(long maxMemoryBytes) {
            this.maxMemoryBytes = maxMemoryBytes;
        }
    }
    
    public static class RedisConfig {
        private String host = "localhost";
        private int port = 6379;
        
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
    
    public static class HealthConfig {
        private long checkIntervalMs = 5000;
        
        public long getCheckIntervalMs() {
            return checkIntervalMs;
        }
        
        public void setCheckIntervalMs(long checkIntervalMs) {
            this.checkIntervalMs = checkIntervalMs;
        }
    }
    
    public static class PeerConfig {
        private String id;
        private String host;
        private int port;
        
        public PeerConfig() {
        }
        
        public PeerConfig(String id, String host, int port) {
            this.id = id;
            this.host = host;
            this.port = port;
        }
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
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
