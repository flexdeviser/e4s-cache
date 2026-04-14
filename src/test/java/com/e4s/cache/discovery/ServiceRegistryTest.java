package com.e4s.cache.discovery;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class ServiceRegistryTest {
    
    private ServiceRegistry registry;
    private ServiceInstance service1;
    private ServiceInstance service2;
    private ServiceInstance service3;
    
    @Before
    public void setUp() {
        registry = new ServiceRegistry();
        service1 = new ServiceInstance("service-1", "group-1", "localhost", 9090);
        service2 = new ServiceInstance("service-2", "group-1", "localhost", 9091);
        service3 = new ServiceInstance("service-3", "group-2", "localhost", 9092);
    }
    
    @Test
    public void testRegisterService() {
        registry.registerService(service1);
        
        assertEquals(1, registry.getServiceCount());
        assertEquals(service1, registry.getService("service-1"));
    }
    
    @Test
    public void testRegisterMultipleServices() {
        registry.registerService(service1);
        registry.registerService(service2);
        registry.registerService(service3);
        
        assertEquals(3, registry.getServiceCount());
    }
    
    @Test
    public void testUnregisterService() {
        registry.registerService(service1);
        registry.registerService(service2);
        
        registry.unregisterService("service-1");
        
        assertEquals(1, registry.getServiceCount());
        assertNull(registry.getService("service-1"));
        assertEquals(service2, registry.getService("service-2"));
    }
    
    @Test
    public void testGetAllServices() {
        registry.registerService(service1);
        registry.registerService(service2);
        registry.registerService(service3);
        
        List<ServiceInstance> services = registry.getAllServices();
        
        assertEquals(3, services.size());
        assertTrue(services.contains(service1));
        assertTrue(services.contains(service2));
        assertTrue(services.contains(service3));
    }
    
    @Test
    public void testGetServicesByGroup() {
        registry.registerService(service1);
        registry.registerService(service2);
        registry.registerService(service3);
        
        List<ServiceInstance> group1Services = registry.getServicesByGroup("group-1");
        List<ServiceInstance> group2Services = registry.getServicesByGroup("group-2");
        
        assertEquals(2, group1Services.size());
        assertEquals(1, group2Services.size());
        assertTrue(group1Services.contains(service1));
        assertTrue(group1Services.contains(service2));
        assertTrue(group2Services.contains(service3));
    }
    
    @Test
    public void testGetHealthyServices() {
        registry.registerService(service1);
        registry.registerService(service2);
        registry.registerService(service3);
        
        service2.setHealthy(false);
        
        List<ServiceInstance> healthyServices = registry.getHealthyServices();
        
        assertEquals(2, healthyServices.size());
        assertTrue(healthyServices.contains(service1));
        assertFalse(healthyServices.contains(service2));
        assertTrue(healthyServices.contains(service3));
    }
    
    @Test
    public void testMarkServiceUnhealthy() {
        registry.registerService(service1);
        
        assertTrue(service1.isHealthy());
        
        registry.markServiceUnhealthy("service-1");
        
        assertFalse(service1.isHealthy());
    }
    
    @Test
    public void testMarkServiceHealthy() {
        registry.registerService(service1);
        service1.setHealthy(false);
        
        assertFalse(service1.isHealthy());
        
        registry.markServiceHealthy("service-1");
        
        assertTrue(service1.isHealthy());
    }
    
    @Test
    public void testGetHealthyServiceCount() {
        registry.registerService(service1);
        registry.registerService(service2);
        registry.registerService(service3);
        
        assertEquals(3, registry.getHealthyServiceCount());
        
        service2.setHealthy(false);
        
        assertEquals(2, registry.getHealthyServiceCount());
    }
}
