package com.e4s.cache.model;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class TimeChunkTest {
    
    private TimeChunk timeChunk;
    private static final int CHUNK_ID = 1;
    private static final int MAX_DATA_POINTS = 100;
    private static final long START_TIME_EPOCH = System.currentTimeMillis();
    
    @Before
    public void setUp() {
        timeChunk = new TimeChunk(CHUNK_ID, MAX_DATA_POINTS, START_TIME_EPOCH);
    }
    
    @Test
    public void testTimeChunkCreation() {
        assertEquals(CHUNK_ID, timeChunk.getChunkId());
        assertEquals(MAX_DATA_POINTS, timeChunk.getMaxDataPoints());
        assertEquals(0, timeChunk.getDataPointCount());
    }
    
    @Test
    public void testTimeChunkTimeRange() {
        Instant startTime = timeChunk.getStartTime();
        Instant endTime = timeChunk.getEndTime();
        
        assertNotNull(startTime);
        assertNotNull(endTime);
        
        long hoursBetween = ChronoUnit.HOURS.between(startTime, endTime);
        assertEquals(24, hoursBetween);
    }
    
    @Test
    public void testCanStoreMoreData() {
        assertTrue(timeChunk.canStoreMoreData());
    }
    
    @Test
    public void testCanStoreMoreDataWhenFull() {
        for (int i = 0; i < MAX_DATA_POINTS; i++) {
            byte[] data = new byte[AttributeDef.MAX_ATTRIBUTE_LENGTH];
            timeChunk.storeData(data);
        }
        
        assertFalse(timeChunk.canStoreMoreData());
    }
    
    @Test
    public void testStoreData() {
        byte[] data = new byte[AttributeDef.MAX_ATTRIBUTE_LENGTH];
        timeChunk.storeData(data);
        
        assertEquals(1, timeChunk.getDataPointCount());
    }
    
    @Test
    public void testStoreMultipleDataPoints() {
        int numPoints = 10;
        for (int i = 0; i < numPoints; i++) {
            byte[] data = new byte[AttributeDef.MAX_ATTRIBUTE_LENGTH];
            timeChunk.storeData(data);
        }
        
        assertEquals(numPoints, timeChunk.getDataPointCount());
    }
    
    @Test
    public void testStoreDataBeyondCapacity() {
        for (int i = 0; i < MAX_DATA_POINTS + 10; i++) {
            byte[] data = new byte[AttributeDef.MAX_ATTRIBUTE_LENGTH];
            timeChunk.storeData(data);
        }
        
        assertEquals(MAX_DATA_POINTS, timeChunk.getDataPointCount());
    }
    
    @Test
    public void testGetDataBuffer() {
        byte[] data = new byte[AttributeDef.MAX_ATTRIBUTE_LENGTH];
        timeChunk.storeData(data);
        
        ByteBuffer buffer = timeChunk.getDataBuffer();
        
        assertNotNull(buffer);
        assertEquals(AttributeDef.MAX_ATTRIBUTE_LENGTH, buffer.limit());
    }
    
    @Test
    public void testGetDataBufferWithMultiplePoints() {
        int numPoints = 5;
        for (int i = 0; i < numPoints; i++) {
            byte[] data = new byte[AttributeDef.MAX_ATTRIBUTE_LENGTH];
            timeChunk.storeData(data);
        }
        
        ByteBuffer buffer = timeChunk.getDataBuffer();
        
        assertNotNull(buffer);
        assertEquals(numPoints * AttributeDef.MAX_ATTRIBUTE_LENGTH, buffer.limit());
    }
    
    @Test
    public void testGetLastUpdateTimestamp() {
        long initialTimestamp = timeChunk.getLastUpdateTimestamp();
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        byte[] data = new byte[AttributeDef.MAX_ATTRIBUTE_LENGTH];
        timeChunk.storeData(data);
        
        long updatedTimestamp = timeChunk.getLastUpdateTimestamp();
        
        assertTrue(updatedTimestamp > initialTimestamp);
    }
    
    @Test
    public void testIsExpired() {
        long currentTime = System.currentTimeMillis();
        long futureTime = currentTime + 30L * 24 * 60 * 60 * 1000; // 30 days in future
        
        assertFalse(timeChunk.isExpired(currentTime));
        assertTrue(timeChunk.isExpired(futureTime));
    }
    
    @Test
    public void testGetDataForAttribute() {
        String attributeName = "voltage";
        double testValue = 220.5;
        
        AttributeDef attrDef = new AttributeDef(attributeName, 100, 8);
        byte[] data = new byte[8];
        ByteBuffer.wrap(data).putDouble(testValue);
        ByteBuffer encoded = attrDef.encodeAttribute(data);
        byte[] attributeData = new byte[encoded.capacity()];
        encoded.get(attributeData);
        
        timeChunk.storeData(attributeData);
        
        byte[] retrievedData = timeChunk.getDataForAttribute(attributeName);
        
        assertNotNull(retrievedData);
    }
    
    @Test
    public void testGetDataForNonExistentAttribute() {
        String attributeName = "non_existent";
        
        byte[] retrievedData = timeChunk.getDataForAttribute(attributeName);
        
        assertNull(retrievedData);
    }
    
    @Test
    public void testGetDataForAttributeWithMultipleAttributes() {
        String attribute1 = "voltage";
        String attribute2 = "current";
        
        AttributeDef attrDef1 = new AttributeDef(attribute1, 100, 8);
        byte[] data1 = new byte[8];
        ByteBuffer.wrap(data1).putDouble(220.5);
        ByteBuffer encoded1 = attrDef1.encodeAttribute(data1);
        byte[] attributeData1 = new byte[encoded1.capacity()];
        encoded1.get(attributeData1);
        
        AttributeDef attrDef2 = new AttributeDef(attribute2, 100, 8);
        byte[] data2 = new byte[8];
        ByteBuffer.wrap(data2).putDouble(10.5);
        ByteBuffer encoded2 = attrDef2.encodeAttribute(data2);
        byte[] attributeData2 = new byte[encoded2.capacity()];
        encoded2.get(attributeData2);
        
        timeChunk.storeData(attributeData1);
        timeChunk.storeData(attributeData2);
        
        byte[] retrievedData1 = timeChunk.getDataForAttribute(attribute1);
        byte[] retrievedData2 = timeChunk.getDataForAttribute(attribute2);
        
        byte[] nonExistentData = timeChunk.getDataForAttribute("non_existent");
        assertNull(nonExistentData);
        
        assertEquals(2, timeChunk.getDataPointCount());
    }
    
    @Test
    public void testChunkIdUniqueness() {
        TimeChunk chunk1 = new TimeChunk(1, MAX_DATA_POINTS, START_TIME_EPOCH);
        TimeChunk chunk2 = new TimeChunk(2, MAX_DATA_POINTS, START_TIME_EPOCH);
        
        assertNotEquals(chunk1.getChunkId(), chunk2.getChunkId());
    }
    
    @Test
    public void testEmptyChunkDataPointCount() {
        assertEquals(0, timeChunk.getDataPointCount());
    }
    
    @Test
    public void testChunkStartTimeAccuracy() {
        Instant chunkStartTime = timeChunk.getStartTime();
        Instant expectedStartTime = Instant.ofEpochMilli(START_TIME_EPOCH);
        
        long difference = Math.abs(chunkStartTime.toEpochMilli() - expectedStartTime.toEpochMilli());
        assertTrue(difference < 1000); // Less than 1 second difference
    }
}