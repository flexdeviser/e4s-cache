package com.e4s.cache.discovery;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class ConsistentHashPartitionerTest {
    
    private ConsistentHashPartitioner partitioner;
    private ServiceInstance service1;
    private ServiceInstance service2;
    private ServiceInstance service3;
    
    @Before
    public void setUp() {
        service1 = new ServiceInstance("service-1", "group-1", "localhost", 9090);
        service2 = new ServiceInstance("service-2", "group-1", "localhost", 9091);
        service3 = new ServiceInstance("service-3", "group-1", "localhost", 9092);
        
        List<ServiceInstance> services = new ArrayList<>();
        services.add(service1);
        services.add(service2);
        services.add(service3);
        
        partitioner = new ConsistentHashPartitioner(services);
    }
    
    @Test
    public void testConstructor() {
        assertEquals(3, partitioner.getServiceCount());
        assertEquals(300, partitioner.getRingSize());
        assertEquals(100, partitioner.getVirtualNodesPerService());
    }
    
    @Test
    public void testGetResponsibleService() {
        ServiceInstance responsible = partitioner.getResponsibleService("sensor-123");
        
        assertNotNull(responsible);
        assertTrue(responsible.equals(service1) || responsible.equals(service2) || responsible.equals(service3));
    }
    
    @Test
    public void testGetResponsibleServiceConsistency() {
        ServiceInstance responsible1 = partitioner.getResponsibleService("sensor-123");
        ServiceInstance responsible2 = partitioner.getResponsibleService("sensor-123");
        
        assertEquals(responsible1, responsible2);
    }
    
    @Test
    public void testGetResponsibleServiceDifferentSensors() {
        ServiceInstance responsible1 = partitioner.getResponsibleService("sensor-1");
        ServiceInstance responsible2 = partitioner.getResponsibleService("sensor-2");
        ServiceInstance responsible3 = partitioner.getResponsibleService("sensor-3");
        
        assertNotNull(responsible1);
        assertNotNull(responsible2);
        assertNotNull(responsible3);
    }
    
    @Test
    public void testGetReplicaServices() {
        List<ServiceInstance> replicas = partitioner.getReplicaServices("sensor-123", 2);
        
        assertEquals(2, replicas.size());
        assertFalse(replicas.get(0).equals(replicas.get(1)));
    }
    
    @Test
    public void testGetReplicaServicesMoreThanAvailable() {
        List<ServiceInstance> replicas = partitioner.getReplicaServices("sensor-123", 5);
        
        assertEquals(3, replicas.size());
    }
    
    @Test
    public void testGetAllServices() {
        List<ServiceInstance> services = partitioner.getAllServices();
        
        assertEquals(3, services.size());
        assertTrue(services.contains(service1));
        assertTrue(services.contains(service2));
        assertTrue(services.contains(service3));
    }
    
    @Test
    public void testAddService() {
        ServiceInstance service4 = new ServiceInstance("service-4", "group-1", "localhost", 9093);
        
        partitioner.addService(service4);
        
        assertEquals(4, partitioner.getServiceCount());
        assertEquals(400, partitioner.getRingSize());
    }
    
    @Test
    public void testRemoveService() {
        partitioner.removeService(service2);
        
        assertEquals(2, partitioner.getServiceCount());
        assertEquals(200, partitioner.getRingSize());
    }
    
    @Test
    public void testRebuildRing() {
        partitioner.removeService(service2);
        
        assertEquals(2, partitioner.getServiceCount());
        
        partitioner.rebuildRing();
        
        assertEquals(2, partitioner.getServiceCount());
        assertEquals(200, partitioner.getRingSize());
    }
}
