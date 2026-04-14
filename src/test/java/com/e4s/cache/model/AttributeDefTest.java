package com.e4s.cache.model;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class AttributeDefTest {
    
    private AttributeDef attributeDef;
    private static final String TEST_ATTRIBUTE = "voltage";
    private static final int MAX_DATA_POINTS = 100;
    private static final int DATA_TYPE_SIZE = 8;
    
    @Before
    public void setUp() {
        attributeDef = new AttributeDef(TEST_ATTRIBUTE, MAX_DATA_POINTS, DATA_TYPE_SIZE);
    }
    
    @Test
    public void testAttributeDefCreation() {
        assertEquals(TEST_ATTRIBUTE, attributeDef.getName());
        assertEquals(MAX_DATA_POINTS, attributeDef.getMaxDataPoints());
        assertEquals(DATA_TYPE_SIZE, attributeDef.getDataTypeSize());
    }
    
    @Test
    public void testGetAttributeSize() {
        int expectedSize = AttributeDef.MAX_ATTRIBUTE_LENGTH + DATA_TYPE_SIZE;
        assertEquals(expectedSize, attributeDef.getAttributeSize());
    }
    
    @Test
    public void testEncodeAttribute() {
        double testValue = 220.5;
        byte[] data = new byte[8];
        ByteBuffer.wrap(data).putDouble(testValue);
        
        ByteBuffer encoded = attributeDef.encodeAttribute(data);
        
        assertNotNull(encoded);
        assertEquals(attributeDef.getAttributeSize(), encoded.capacity());
    }
    
    @Test
    public void testDecodeAttribute() {
        double testValue = 220.5;
        byte[] data = new byte[8];
        ByteBuffer.wrap(data).putDouble(testValue);
        
        ByteBuffer encoded = attributeDef.encodeAttribute(data);
        byte[] encodedBytes = new byte[encoded.capacity()];
        encoded.get(encodedBytes);
        
        AttributeInfo decoded = attributeDef.decodeAttribute(encodedBytes);
        
        assertNotNull(decoded);
        assertEquals(TEST_ATTRIBUTE, decoded.getName());
        assertEquals(testValue, decoded.getValue(), 0.001);
    }
    
    @Test
    public void testEncodeDecodeRoundTrip() {
        double testValue = 123.456;
        byte[] data = new byte[8];
        ByteBuffer.wrap(data).putDouble(testValue);
        
        ByteBuffer encoded = attributeDef.encodeAttribute(data);
        byte[] encodedBytes = new byte[encoded.capacity()];
        encoded.get(encodedBytes);
        
        AttributeInfo decoded = attributeDef.decodeAttribute(encodedBytes);
        
        assertEquals(testValue, decoded.getValue(), 0.001);
    }
    
    @Test
    public void testEncodeMultipleValues() {
        double[] testValues = {100.0, 200.0, 300.0, 400.0, 500.0};
        
        for (double testValue : testValues) {
            byte[] data = new byte[8];
            ByteBuffer.wrap(data).putDouble(testValue);
            
            ByteBuffer encoded = attributeDef.encodeAttribute(data);
            byte[] encodedBytes = new byte[encoded.capacity()];
            encoded.get(encodedBytes);
            
            AttributeInfo decoded = attributeDef.decodeAttribute(encodedBytes);
            
            assertEquals(testValue, decoded.getValue(), 0.001);
        }
    }
    
    @Test
    public void testEncodeNegativeValue() {
        double testValue = -123.456;
        byte[] data = new byte[8];
        ByteBuffer.wrap(data).putDouble(testValue);
        
        ByteBuffer encoded = attributeDef.encodeAttribute(data);
        byte[] encodedBytes = new byte[encoded.capacity()];
        encoded.get(encodedBytes);
        
        AttributeInfo decoded = attributeDef.decodeAttribute(encodedBytes);
        
        assertEquals(testValue, decoded.getValue(), 0.001);
    }
    
    @Test
    public void testEncodeZeroValue() {
        double testValue = 0.0;
        byte[] data = new byte[8];
        ByteBuffer.wrap(data).putDouble(testValue);
        
        ByteBuffer encoded = attributeDef.encodeAttribute(data);
        byte[] encodedBytes = new byte[encoded.capacity()];
        encoded.get(encodedBytes);
        
        AttributeInfo decoded = attributeDef.decodeAttribute(encodedBytes);
        
        assertEquals(testValue, decoded.getValue(), 0.001);
    }
    
    @Test
    public void testEncodeLargeValue() {
        double testValue = Double.MAX_VALUE;
        byte[] data = new byte[8];
        ByteBuffer.wrap(data).putDouble(testValue);
        
        ByteBuffer encoded = attributeDef.encodeAttribute(data);
        byte[] encodedBytes = new byte[encoded.capacity()];
        encoded.get(encodedBytes);
        
        AttributeInfo decoded = attributeDef.decodeAttribute(encodedBytes);
        
        assertEquals(testValue, decoded.getValue(), 0.001);
    }
    
    @Test
    public void testEncodeSmallValue() {
        double testValue = Double.MIN_VALUE;
        byte[] data = new byte[8];
        ByteBuffer.wrap(data).putDouble(testValue);
        
        ByteBuffer encoded = attributeDef.encodeAttribute(data);
        byte[] encodedBytes = new byte[encoded.capacity()];
        encoded.get(encodedBytes);
        
        AttributeInfo decoded = attributeDef.decodeAttribute(encodedBytes);
        
        assertEquals(testValue, decoded.getValue(), 0.001);
    }
}