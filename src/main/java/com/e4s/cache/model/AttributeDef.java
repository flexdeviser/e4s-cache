package com.e4s.cache.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class AttributeDef {
    public static final String VOLTAGE = "voltage";
    public static final String CURRENT = "current";
    public static final String POWER_FACTOR = "power_factor";
    public static final String ACTIVE_POWER = "active_power";
    public static final String REACTIVE_POWER = "reactive_power";
    
    public static final int MAX_ATTRIBUTE_LENGTH = 64;
    private final String name;
    private final int maxDataPoints;
    private final int dataTypeSize;
    
    public AttributeDef(String name, int maxDataPoints, int dataTypeSize) {
        this.name = name;
        this.maxDataPoints = maxDataPoints;
        this.dataTypeSize = dataTypeSize;
    }
    
    public String getName() {
        return name;
    }
    
    public int getMaxDataPoints() {
        return maxDataPoints;
    }
    
    public int getDataTypeSize() {
        return dataTypeSize;
    }
    
    public int getAttributeSize() {
        return MAX_ATTRIBUTE_LENGTH + dataTypeSize;
    }
    
    public ByteBuffer encodeAttribute(byte[] data) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[getAttributeSize()];
        
        int offset = 0;
        System.arraycopy(nameBytes, 0, result, offset, Math.min(nameBytes.length, MAX_ATTRIBUTE_LENGTH));
        offset += MAX_ATTRIBUTE_LENGTH;
        System.arraycopy(data, 0, result, offset, data.length);
        
        return ByteBuffer.wrap(result);
    }
    
    public AttributeInfo decodeAttribute(byte[] encoded) {
        String decodedName = new String(encoded, 0, MAX_ATTRIBUTE_LENGTH, StandardCharsets.UTF_8).trim();
        
        byte[] data = new byte[encoded.length - MAX_ATTRIBUTE_LENGTH];
        System.arraycopy(encoded, MAX_ATTRIBUTE_LENGTH, data, 0, data.length);
        
        double value = ByteBuffer.wrap(data).getDouble();
        return new AttributeInfo(decodedName, value);
    }
}