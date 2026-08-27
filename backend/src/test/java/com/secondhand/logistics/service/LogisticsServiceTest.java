package com.secondhand.logistics.service;

import com.secondhand.common.AppException;
import com.secondhand.logistics.provider.LogisticsProvider;
import com.secondhand.order.entity.Shipment;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.order.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 物流服务单元测试(用例4 卖家发货/物流查询)。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogisticsService 单元测试")
class LogisticsServiceTest {

    @Mock
    OrderRepository orderRepo;
    @Mock
    ShipmentRepository shipmentRepo;
    @Mock
    LogisticsProvider provider;

    LogisticsService logisticsService;

    @BeforeEach
    void buildService() {
        logisticsService = new LogisticsService(orderRepo, shipmentRepo, provider);
    }

    @Nested
    @DisplayName("用例4 物流查询 trackByOrderId")
    class Track {

        @Test
        @DisplayName("UNIT-TC04-05 订单不存在,抛出 NOT_FOUND")
        void shouldRejectUnknownOrder() {
            when(orderRepo.existsById(20L)).thenReturn(false);

            AppException ex = assertThrows(AppException.class, () -> logisticsService.trackByOrderId(20L));
            assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC04-06 订单无物流信息,抛出 NOT_FOUND")
        void shouldRejectOrderWithoutShipment() {
            when(orderRepo.existsById(20L)).thenReturn(true);
            when(shipmentRepo.findByOrderId(20L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () -> logisticsService.trackByOrderId(20L));
            assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
            verify(provider, never()).track(anyString(), anyString());
        }

        @Test
        @DisplayName("UNIT-TC04-07 正常返回物流轨迹:以运单号调用物流服务商")
        void shouldReturnTrackResult() {
            when(orderRepo.existsById(20L)).thenReturn(true);
            Shipment shipment = new Shipment();
            shipment.setOrderId(20L);
            shipment.setCarrierCode("SF");
            shipment.setTrackingNo("SF123456");
            when(shipmentRepo.findByOrderId(20L)).thenReturn(Optional.of(shipment));

            LogisticsProvider.TrackResult expected = new LogisticsProvider.TrackResult(
                    "SF", "SF123456", "IN_TRANSIT",
                    List.of(new LogisticsProvider.TrackPoint("2026-08-27T10:00:00", "快件在途中")));
            when(provider.track("SF", "SF123456")).thenReturn(expected);

            LogisticsProvider.TrackResult result = logisticsService.trackByOrderId(20L);

            assertSame(expected, result);
            assertEquals("IN_TRANSIT", result.status());
            verify(provider).track("SF", "SF123456");
        }
    }
}
