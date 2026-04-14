package com.e4s.cache.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ConsistentHashPartitioner {
    private static final Logger logger = LoggerFactory.getLogger(ConsistentHashPartitioner.class);
    
    private static final int VIRTUAL_NODES_PER_SERVICE = 100;
    
    private final NavigableMap<Integer, VirtualNode> ring;
    private final List<ServiceInstance> services;
    
    public ConsistentHashPartitioner(List<ServiceInstance> services) {
        this.services = new ArrayList<>(services);
        this.ring = new TreeMap<>();
        
        for (ServiceInstance service : services) {
            for (int i = 0; i < VIRTUAL_NODES_PER_SERVICE; i++) {
                int virtualNodeId = service.hashCode() * VIRTUAL_NODES_PER_SERVICE + i;
                ring.put(virtualNodeId, new VirtualNode(service, i));
            }
        }
        
        logger.info("Created consistent hash ring with {} virtual nodes for {} services",
            ring.size(), services.size());
    }
    
    public ServiceInstance getResponsibleService(String sensorId) {
        int hash = Math.abs(sensorId.hashCode());
        
        java.util.Map.Entry<Integer, VirtualNode> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        
        return entry.getValue().getService();
    }
    
    public List<ServiceInstance> getReplicaServices(String sensorId, int replicaCount) {
        List<ServiceInstance> replicas = new ArrayList<>();
        
        int hash = Math.abs(sensorId.hashCode());
        
        java.util.Map.Entry<Integer, VirtualNode> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        
        java.util.Iterator<java.util.Map.Entry<Integer, VirtualNode>> iterator = 
            ring.tailMap(entry.getKey(), true).entrySet().iterator();
        
        while (replicas.size() < replicaCount && iterator.hasNext()) {
            VirtualNode virtualNode = iterator.next().getValue();
            ServiceInstance service = virtualNode.getService();
            
            if (!replicas.contains(service)) {
                replicas.add(service);
            }
        }
        
        if (replicas.size() < replicaCount) {
            iterator = ring.entrySet().iterator();
            while (replicas.size() < replicaCount && iterator.hasNext()) {
                VirtualNode virtualNode = iterator.next().getValue();
                ServiceInstance service = virtualNode.getService();
                
                if (!replicas.contains(service)) {
                    replicas.add(service);
                }
            }
        }
        
        return replicas;
    }
    
    public List<ServiceInstance> getAllServices() {
        return new ArrayList<>(services);
    }
    
    public int getServiceCount() {
        return services.size();
    }
    
    public int getRingSize() {
        return ring.size();
    }
    
    public void addService(ServiceInstance service) {
        if (!services.contains(service)) {
            services.add(service);
            
            for (int i = 0; i < VIRTUAL_NODES_PER_SERVICE; i++) {
                int virtualNodeId = service.hashCode() * VIRTUAL_NODES_PER_SERVICE + i;
                ring.put(virtualNodeId, new VirtualNode(service, i));
            }
            
            logger.info("Added service {} to hash ring", service.getId());
        }
    }
    
    public void removeService(ServiceInstance service) {
        if (services.remove(service)) {
            for (int i = 0; i < VIRTUAL_NODES_PER_SERVICE; i++) {
                int virtualNodeId = service.hashCode() * VIRTUAL_NODES_PER_SERVICE + i;
                ring.remove(virtualNodeId);
            }
            
            logger.info("Removed service {} from hash ring", service.getId());
        }
    }
    
    public void rebuildRing() {
        NavigableMap<Integer, VirtualNode> newRing = new TreeMap<>();
        
        for (ServiceInstance service : services) {
            for (int i = 0; i < VIRTUAL_NODES_PER_SERVICE; i++) {
                int virtualNodeId = service.hashCode() * VIRTUAL_NODES_PER_SERVICE + i;
                newRing.put(virtualNodeId, new VirtualNode(service, i));
            }
        }
        
        this.ring.clear();
        this.ring.putAll(newRing);
        
        logger.info("Rebuilt hash ring with {} virtual nodes", ring.size());
    }
    
    public int getVirtualNodesPerService() {
        return VIRTUAL_NODES_PER_SERVICE;
    }
    
    private static class VirtualNode {
        private final ServiceInstance service;
        private final int index;
        
        public VirtualNode(ServiceInstance service, int index) {
            this.service = service;
            this.index = index;
        }
        
        public ServiceInstance getService() {
            return service;
        }
        
        public int getIndex() {
            return index;
        }
    }
}