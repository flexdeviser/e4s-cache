package com.e4s.cache.discovery;

import java.util.List;

public interface IServiceRegistry {
    List<ServiceInstance> getAllServices();
    int getServiceCount();
    ServiceInstance getService(String serviceId);
    ServiceInstance getLocalService();
}