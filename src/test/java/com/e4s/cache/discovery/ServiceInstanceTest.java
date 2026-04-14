package com.e4s.cache.discovery;

import org.junit.Test;
import static org.junit.Assert.*;

public class ServiceInstanceTest {
    
    @Test
    public void testConstructor() {
        ServiceInstance instance = new ServiceInstance("service-1", "group-1", "localhost", 9090);
        
        assertEquals("service-1", instance.getId());
        assertEquals("group-1", instance.getGroup());
        assertEquals("localhost", instance.getHost());
        assertEquals(9090, instance.getPort());
        assertFalse(instance.isHealthy());
        assertFalse(instance.isHealthChecked());
    }
    
    @Test
    public void testSetHealthy() {
        ServiceInstance instance = new ServiceInstance("service-1", "group-1", "localhost", 9090);
        
        assertFalse(instance.isHealthy());
        assertFalse(instance.isHealthChecked());
        
        instance.setHealthy(false);
        assertFalse(instance.isHealthy());
        assertTrue(instance.isHealthChecked());
        
        instance.setHealthy(true);
        assertTrue(instance.isHealthy());
        assertTrue(instance.isHealthChecked());
    }
    
    @Test
    public void testGetAddress() {
        ServiceInstance instance = new ServiceInstance("service-1", "group-1", "localhost", 9090);
        
        assertEquals("localhost:9090", instance.getAddress());
    }
    
    @Test
    public void testEquals() {
        ServiceInstance instance1 = new ServiceInstance("service-1", "group-1", "localhost", 9090);
        ServiceInstance instance2 = new ServiceInstance("service-1", "group-1", "localhost", 9090);
        ServiceInstance instance3 = new ServiceInstance("service-2", "group-1", "localhost", 9090);
        
        assertEquals(instance1, instance2);
        assertNotEquals(instance1, instance3);
    }
    
    @Test
    public void testHashCode() {
        ServiceInstance instance1 = new ServiceInstance("service-1", "group-1", "localhost", 9090);
        ServiceInstance instance2 = new ServiceInstance("service-1", "group-1", "localhost", 9090);
        
        assertEquals(instance1.hashCode(), instance2.hashCode());
    }
    
    @Test
    public void testToString() {
        ServiceInstance instance = new ServiceInstance("service-1", "group-1", "localhost", 9090);
        
        String str = instance.toString();
        
        assertTrue(str.contains("service-1"));
        assertTrue(str.contains("group-1"));
        assertTrue(str.contains("localhost"));
        assertTrue(str.contains("9090"));
    }
}
