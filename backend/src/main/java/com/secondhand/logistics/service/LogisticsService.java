package com.secondhand.logistics.service;

import com.secondhand.common.AppException;
import com.secondhand.logistics.provider.LogisticsProvider;
import com.secondhand.order.entity.Shipment;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.order.repository.ShipmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LogisticsService {

    private final OrderRepository orderRepo;
    private final ShipmentRepository shipmentRepo;
    private final LogisticsProvider provider;

    public LogisticsService(OrderRepository orderRepo, ShipmentRepository shipmentRepo,
                            LogisticsProvider provider) {
        this.orderRepo = orderRepo;
        this.shipmentRepo = shipmentRepo;
        this.provider = provider;
    }

    public LogisticsProvider.TrackResult trackByOrderId(long orderId) {
        if (!orderRepo.existsById(orderId)) {
            throw new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND);
        }
        Shipment shipment = shipmentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "暂无物流信息", HttpStatus.NOT_FOUND));
        return provider.track(shipment.getCarrierCode(), shipment.getTrackingNo());
    }
}
