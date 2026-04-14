package com.e4s.cache.model;

public class AttributeInfo {
    private final String name;
    private final double value;
    
    public AttributeInfo(String name, double value) {
        this.name = name;
        this.value = value;
    }
    
    public String getName() {
        return name;
    }
    
    public double getValue() {
        return value;
    }
}