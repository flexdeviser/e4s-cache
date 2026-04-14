package com.e4s.cache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    
    private static final String DEFAULT_SERVICE_CONFIG_FILE = "config/cache-service.yaml";
    private static final String DEFAULT_CLIENT_CONFIG_FILE = "config/cache-client.yaml";
    private static final String ENV_CONFIG_PATH = "E4S_CACHE_CONFIG";
    
    public static CacheServiceConfig loadServiceConfig() {
        String configPath = System.getenv(ENV_CONFIG_PATH);
        if (configPath == null || configPath.isEmpty()) {
            configPath = DEFAULT_SERVICE_CONFIG_FILE;
        }
        
        return loadConfig(configPath, CacheServiceConfig.class);
    }
    
    public static CacheServiceConfig loadServiceConfig(String configPath) {
        return loadConfig(configPath, CacheServiceConfig.class);
    }
    
    public static CacheClientConfig loadClientConfig() {
        return loadConfig(DEFAULT_CLIENT_CONFIG_FILE, CacheClientConfig.class);
    }
    
    public static CacheClientConfig loadClientConfig(String configPath) {
        return loadConfig(configPath, CacheClientConfig.class);
    }
    
    public static <T> T loadConfig(String configPath, Class<T> configClass) {
        Path path = Paths.get(configPath);
        
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Config file not found: " + path.toAbsolutePath());
        }
        
        logger.info("Loading configuration from: {}", path.toAbsolutePath());
        
        try (InputStream input = new FileInputStream(path.toFile())) {
            Yaml yaml = new Yaml();
            T config = yaml.loadAs(input, configClass);
            
            if (config instanceof CacheServiceConfig) {
                validateServiceConfig((CacheServiceConfig) config);
            } else if (config instanceof CacheClientConfig) {
                validateClientConfig((CacheClientConfig) config);
            }
            
            logger.info("Configuration loaded successfully");
            return config;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration from: " + configPath, e);
        }
    }
    
    private static void validateServiceConfig(CacheServiceConfig config) {
        if (config.getServiceId() == null || config.getServiceId().isEmpty()) {
            throw new IllegalArgumentException("serviceId is required");
        }
        
        if (config.getServiceGroup() == null || config.getServiceGroup().isEmpty()) {
            throw new IllegalArgumentException("serviceGroup is required");
        }
        
        if (config.getHost() == null || config.getHost().isEmpty()) {
            throw new IllegalArgumentException("host is required");
        }
        
        if (config.getPort() <= 0 || config.getPort() > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        
        if (config.getCache() == null) {
            config.setCache(new CacheServiceConfig.CacheConfig());
        }
        
        if (config.getRedis() == null) {
            config.setRedis(new CacheServiceConfig.RedisConfig());
        }
        
        if (config.getHealth() == null) {
            config.setHealth(new CacheServiceConfig.HealthConfig());
        }
        
        if (config.getPeers() == null) {
            config.setPeers(new java.util.ArrayList<>());
        }
        
        for (CacheServiceConfig.PeerConfig peer : config.getPeers()) {
            if (peer.getId() == null || peer.getId().isEmpty()) {
                throw new IllegalArgumentException("peer id is required");
            }
            if (peer.getHost() == null || peer.getHost().isEmpty()) {
                throw new IllegalArgumentException("peer host is required");
            }
            if (peer.getPort() <= 0 || peer.getPort() > 65535) {
                throw new IllegalArgumentException("peer port must be between 1 and 65535");
            }
        }
    }
    
    private static void validateClientConfig(CacheClientConfig config) {
        if (config.getClientId() == null || config.getClientId().isEmpty()) {
            throw new IllegalArgumentException("clientId is required");
        }
        
        if (config.getServices() == null || config.getServices().isEmpty()) {
            throw new IllegalArgumentException("at least one service is required");
        }
        
        for (CacheClientConfig.ServiceConfig service : config.getServices()) {
            if (service.getId() == null || service.getId().isEmpty()) {
                throw new IllegalArgumentException("service id is required");
            }
            if (service.getHost() == null || service.getHost().isEmpty()) {
                throw new IllegalArgumentException("service host is required");
            }
            if (service.getPort() <= 0 || service.getPort() > 65535) {
                throw new IllegalArgumentException("service port must be between 1 and 65535");
            }
        }
    }
}
