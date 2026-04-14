package com.e4s.cache.lock;

import com.e4s.cache.model.AttributeDef;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class DistributedLockManager implements Lock {
    private static final Logger logger = LoggerFactory.getLogger(DistributedLockManager.class);
    private static final String LOCK_PREFIX = "cache_lock:";
    private static final long LOCK_WAIT_TIME = 10;
    private static final long LOCK_LEASE_TIME = 30;
    private static final long LOCK_RENEW_INTERVAL = 5;
    
    private final JedisPool jedisPool;
    private final String lockKey;
    private final String lockValue;
    private volatile boolean locked = false;
    private Thread ownerThread = null;
    private long startTime = -1;
    private Thread renewalThread = null;
    private final ReentrantLock internalLock = new ReentrantLock();
    
    public DistributedLockManager(JedisPool jedisPool, String sensorId) {
        this.jedisPool = jedisPool;
        this.lockKey = LOCK_PREFIX + sensorId;
        this.lockValue = Thread.currentThread().getName() + "_" + System.currentTimeMillis();
    }
    
    @Override
    public void lock() {
        boolean acquired = tryLock();
        if (!acquired) {
            logger.warn("Failed to acquire lock for key: {}", lockKey);
            throw new RuntimeException("Failed to acquire lock for key: " + lockKey);
        }
    }
    
    @Override
    public void lockInterruptibly() throws InterruptedException {
        boolean acquired = tryLock();
        if (!acquired) {
            logger.warn("Failed to acquire lock for key: {}", lockKey);
            throw new RuntimeException("Failed to acquire lock for key: " + lockKey);
        }
    }
    
    @Override
    public boolean tryLock() {
        long currentTime = System.currentTimeMillis();
        
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(lockKey, lockValue, 
                SetParams.setParams().nx().px(LOCK_LEASE_TIME * 1000));
            
            if ("OK".equals(result)) {
                locked = true;
                ownerThread = Thread.currentThread();
                startTime = currentTime;
                startRenewalThread();
                return true;
            }
            return false;
        }
    }
    
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        long waitTime = unit.toMillis(time);
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < waitTime) {
            boolean acquired = tryLock();
            if (acquired) {
                return true;
            }
            
            Thread.sleep(10);
        }
        
        return false;
    }
    
    @Override
    public void unlock() {
        if (!locked) {
            logger.warn("Attempted to unlock without holding lock for key: {}", lockKey);
            return;
        }
        
        if (Thread.currentThread() != ownerThread) {
            logger.error("Thread {} attempted to unlock lock owned by: {}", 
                Thread.currentThread().getName(), ownerThread.getName());
            throw new IllegalMonitorStateException(
                "Current thread " + Thread.currentThread().getName() + 
                " does not own lock owned by " + ownerThread.getName());
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                          "return redis.call('del', KEYS[1]) else " +
                          "return 0 end";
            
            Object result = jedis.eval(script, 1, lockKey, lockValue);
            long lockCount = result instanceof Long ? (Long) result : 0;
            
            if (result != null && (result instanceof Long) && (Long) result > 0) {
                locked = false;
                stopRenewalThread();
                logger.debug("Successfully released lock for key: {}", lockKey);
            } else {
                logger.warn("Failed to release lock for key: {}", lockKey);
            }
        }
    }
    
    private void startRenewalThread() {
        renewalThread = new Thread(() -> {
            while (locked && renewalThread == Thread.currentThread()) {
                try {
                    Thread.sleep(LOCK_RENEW_INTERVAL * 1000);
                    
                    if (locked && Thread.currentThread() == ownerThread) {
                        try (Jedis jedis = jedisPool.getResource()) {
                            jedis.expire(lockKey, LOCK_LEASE_TIME);
                            logger.trace("Extended lock lease for key: {}", lockKey);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error renewing lock for key: {}", lockKey, e);
                }
            }
        });
        
        renewalThread.setDaemon(true);
        renewalThread.setName("LockRenewal-" + lockKey);
        renewalThread.start();
    }
    
    private void stopRenewalThread() {
        if (renewalThread != null) {
            renewalThread.interrupt();
            try {
                renewalThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            renewalThread = null;
        }
    }
    
    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("Conditions are not supported for distributed locks");
    }
    
    public boolean isLocked() {
        return locked;
    }
    
    public long getLockTime() {
        return startTime > -1 ? System.currentTimeMillis() - startTime : 0;
    }
    
    public JedisPool getJedisPool() {
        return jedisPool;
    }
}