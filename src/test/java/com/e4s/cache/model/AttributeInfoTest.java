package com.e4s.cache.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class AttributeInfoTest {
    
    @Test
    public void testAttributeInfoCreation() {
        String name = "voltage";
        double value = 220.5;
        
        AttributeInfo attributeInfo = new AttributeInfo(name, value);
        
        assertEquals(name, attributeInfo.getName());
        assertEquals(value, attributeInfo.getValue(), 0.001);
    }
    
    @Test
    public void testAttributeInfoWithNegativeValue() {
        String name = "current";
        double value = -10.5;
        
        AttributeInfo attributeInfo = new AttributeInfo(name, value);
        
        assertEquals(name, attributeInfo.getName());
        assertEquals(value, attributeInfo.getValue(), 0.001);
    }
    
    @Test
    public void testAttributeInfoWithZeroValue() {
        String name = "power_factor";
        double value = 0.0;
        
        AttributeInfo attributeInfo = new AttributeInfo(name, value);
        
        assertEquals(name, attributeInfo.getName());
        assertEquals(value, attributeInfo.getValue(), 0.001);
    }
    
    @Test
    public void testAttributeInfoWithLargeValue() {
        String name = "voltage";
        double value = 1000000.0;
        
        AttributeInfo attributeInfo = new AttributeInfo(name, value);
        
        assertEquals(name, attributeInfo.getName());
        assertEquals(value, attributeInfo.getValue(), 0.001);
    }
    
    @Test
    public void testAttributeInfoWithSmallValue() {
        String name = "current";
        double value = 0.0001;
        
        AttributeInfo attributeInfo = new AttributeInfo(name, value);
        
        assertEquals(name, attributeInfo.getName());
        assertEquals(value, attributeInfo.getValue(), 0.001);
    }
    
    @Test
    public void testAttributeInfoWithSpecialCharacters() {
        String name = "voltage_phase_a";
        double value = 220.5;
        
        AttributeInfo attributeInfo = new AttributeInfo(name, value);
        
        assertEquals(name, attributeInfo.getName());
        assertEquals(value, attributeInfo.getValue(), 0.001);
    }
    
    @Test
    public void testAttributeInfoWithLongName() {
        String name = "very_long_attribute_name_with_many_characters";
        double value = 123.456;
        
        AttributeInfo attributeInfo = new AttributeInfo(name, value);
        
        assertEquals(name, attributeInfo.getName());
        assertEquals(value, attributeInfo.getValue(), 0.001);
    }
    
    @Test
    public void testAttributeInfoImmutability() {
        String name = "voltage";
        double value = 220.5;
        
        AttributeInfo attributeInfo = new AttributeInfo(name, value);
        
        assertEquals(name, attributeInfo.getName());
        assertEquals(value, attributeInfo.getValue(), 0.001);
        
        String newName = "current";
        double newValue = 10.5;
        
        AttributeInfo newAttributeInfo = new AttributeInfo(newName, newValue);
        
        assertEquals(newName, newAttributeInfo.getName());
        assertEquals(newValue, newAttributeInfo.getValue(), 0.001);
        
        assertEquals(name, attributeInfo.getName());
        assertEquals(value, attributeInfo.getValue(), 0.001);
    }
}