package com.e4s.cache.discovery;

import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionStateListener implements ClientInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionStateListener.class);
    
    private final ServiceInstance service;
    private final ServiceEventListener eventListener;
    private volatile boolean connected = false;
    
    public ConnectionStateListener(ServiceInstance service, ServiceEventListener eventListener) {
        this.service = service;
        this.eventListener = eventListener;
    }
    
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, 
            io.grpc.CallOptions callOptions,
            io.grpc.Channel next) {
        
        return new ForwardingClientCall<ReqT, RespT>() {
            @Override
            protected ClientCall<ReqT, RespT> delegate() {
                return next.newCall(method, callOptions);
            }
            
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener<RespT>() {
                    @Override
                    protected Listener<RespT> delegate() {
                        return responseListener;
                    }
                    
                    @Override
                    public void onReady() {
                        if (!connected) {
                            connected = true;
                            logger.info("Connection established to service: {}", service.getId());
                            eventListener.fireServiceHealthChanged(service, true, "connected");
                        }
                        super.onReady();
                    }
                    
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        if (connected && !status.isOk()) {
                            connected = false;
                            String reason = status.getCode().name();
                            if (status.getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                                reason = "disconnected";
                            }
                            logger.warn("Connection lost to service: {}, reason: {}", service.getId(), reason);
                            eventListener.fireServiceHealthChanged(service, false, reason);
                        }
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
    
    public boolean isConnected() {
        return connected;
    }
}
