package com.secondhand.order.service;

import com.secondhand.common.AppException;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderEvent;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.entity.Shipment;
import com.secondhand.order.entity.ShipmentStatus;
import com.secondhand.order.repository.OrderEventRepository;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.order.repository.ShipmentRepository;
import com.secondhand.product.entity.Product;
import com.secondhand.product.entity.ProductStatus;
import com.secondhand.product.repository.ProductRepository;
import com.secondhand.product.service.ProductService;
import com.secondhand.rating.repository.RatingRepository;
import com.secondhand.testutil.TestId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 订单服务单元测试(用例3 商品购买/下单+支付,用例4 卖家发货)。
 * 纯 Mockito,不启动 Spring 上下文;数据库访问由集成测试覆盖。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 单元测试")
class OrderServiceTest {

    @Mock
    OrderRepository orderRepo;
    @Mock
    OrderEventRepository orderEventRepo;
    @Mock
    ShipmentRepository shipmentRepo;
    @Mock
    ProductService productService;
    @Mock
    ProductRepository productRepo;
    @Mock
    RatingRepository ratingRepo;

    OrderService orderService;

    private static final long BUYER = 1L;
    private static final long SELLER = 2L;

    @BeforeEach
    void buildService() {
        orderService = new OrderService(orderRepo, orderEventRepo, shipmentRepo,
                productService, productRepo, ratingRepo);
        // save 返回同一实例,便于后续断言(无 id setter);异常路径测试不会用到,故 lenient
        lenient().when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product onSaleProduct(int quantity) {
        Product p = new Product();
        p.setSellerId(SELLER);
        p.setPriceCent(5000);
        p.setQuantity(quantity);
        p.setStatus(ProductStatus.ON_SALE);
        TestId.set(p, 10L);
        return p;
    }

    private OrderService.CreateOrderCommand cmd() {
        return new OrderService.CreateOrderCommand(10L, "张三", "13800138000", "北京市海淀区", 1L);
    }

    @Nested
    @DisplayName("用例3 下单 createOrder")
    class CreateOrder {

        @Test
        @DisplayName("UNIT-TC03-01 以原价下单成功:库存减一、订单待支付、记录事件")
        void shouldCreateOrderSuccessfully() {
            Product product = onSaleProduct(3);
            when(productService.getById(10L)).thenReturn(product);

            Order result = orderService.createOrder(BUYER, cmd());

            assertEquals(OrderStatus.WAIT_PAY, result.getStatus());
            assertEquals(BUYER, result.getBuyerId());
            assertEquals(SELLER, result.getSellerId());
            assertEquals(5000, result.getAmountCent());
            assertEquals(2, product.getQuantity()); // 库存减一
            verify(productRepo).save(product);

            ArgumentCaptor<OrderEvent> evt = ArgumentCaptor.forClass(OrderEvent.class);
            verify(orderEventRepo).save(evt.capture());
            assertEquals("NONE", evt.getValue().getFromStatus());
            assertEquals("WAIT_PAY", evt.getValue().getToStatus());
        }

        @Test
        @DisplayName("UNIT-TC03-02 库存最后一件售出后商品自动下架")
        void shouldOffSaleWhenLastOneSold() {
            Product product = onSaleProduct(1);
            when(productService.getById(10L)).thenReturn(product);

            orderService.createOrder(BUYER, cmd());

            assertEquals(0, product.getQuantity());
            assertEquals(ProductStatus.OFF_SALE, product.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC03-03 不能购买自己的商品,抛出 FORBIDDEN")
        void shouldRejectSelfPurchase() {
            when(productService.getById(10L)).thenReturn(onSaleProduct(1));

            AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(SELLER, cmd()));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC03-04 非在售商品不能下单,抛出 CONFLICT")
        void shouldRejectOffSaleProduct() {
            Product product = onSaleProduct(1);
            product.setStatus(ProductStatus.OFF_SALE);
            when(productService.getById(10L)).thenReturn(product);

            AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(BUYER, cmd()));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
            verify(orderRepo, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("UNIT-TC03-05 已售罄商品不能下单,抛出 CONFLICT")
        void shouldRejectSoldOutProduct() {
            Product product = onSaleProduct(0);
            when(productService.getById(10L)).thenReturn(product);

            AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(BUYER, cmd()));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }
    }

    @Nested
    @DisplayName("用例3 支付 pay")
    class Pay {

        private Order waitPayOrder() {
            Order o = new Order();
            o.setBuyerId(BUYER);
            o.setSellerId(SELLER);
            o.setProductId(10L);
            o.setAmountCent(5000);
            o.setStatus(OrderStatus.WAIT_PAY);
            o.setReceiverName("张三");
            o.setReceiverPhone("13800138000");
            o.setReceiverAddress("北京市海淀区");
            TestId.set(o, 20L);
            return o;
        }

        @Test
        @DisplayName("UNIT-TC03-06 支付成功:订单进入待发货,记录支付事件")
        void shouldPaySuccessfully() {
            Order order = waitPayOrder();
            when(orderRepo.findByIdAndBuyerId(20L, BUYER)).thenReturn(Optional.of(order));

            Order result = orderService.pay(BUYER, 20L);

            assertEquals(OrderStatus.WAIT_DELIVER, result.getStatus());
            assertNotNull(result.getPaidAt());

            ArgumentCaptor<OrderEvent> evt = ArgumentCaptor.forClass(OrderEvent.class);
            verify(orderEventRepo).save(evt.capture());
            assertEquals("WAIT_PAY", evt.getValue().getFromStatus());
            assertEquals("WAIT_DELIVER", evt.getValue().getToStatus());
        }

        @Test
        @DisplayName("UNIT-TC03-07 收货信息不完整时支付被拒绝,抛出 BAD_REQUEST")
        void shouldRejectPayWithoutReceiverInfo() {
            Order order = waitPayOrder();
            order.setReceiverName(null);
            when(orderRepo.findByIdAndBuyerId(20L, BUYER)).thenReturn(Optional.of(order));

            AppException ex = assertThrows(AppException.class, () -> orderService.pay(BUYER, 20L));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC03-08 已支付订单重复支付被拒绝,抛出 CONFLICT")
        void shouldRejectDuplicatePay() {
            Order order = waitPayOrder();
            order.setStatus(OrderStatus.WAIT_DELIVER);
            when(orderRepo.findByIdAndBuyerId(20L, BUYER)).thenReturn(Optional.of(order));

            AppException ex = assertThrows(AppException.class, () -> orderService.pay(BUYER, 20L));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC03-09 非买家支付订单被拒绝,抛出 NOT_FOUND")
        void shouldRejectPayByNonBuyer() {
            when(orderRepo.findByIdAndBuyerId(20L, 99L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () -> orderService.pay(99L, 20L));
            assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        }
    }

    @Nested
    @DisplayName("用例3 取消 cancel")
    class Cancel {

        private Order waitPayOrder() {
            Order o = new Order();
            o.setBuyerId(BUYER);
            o.setSellerId(SELLER);
            o.setProductId(10L);
            o.setAmountCent(5000);
            o.setStatus(OrderStatus.WAIT_PAY);
            TestId.set(o, 20L);
            return o;
        }

        @Test
        @DisplayName("UNIT-TC03-10 取消待支付订单:库存恢复并自动重新上架")
        void shouldCancelAndRestoreStock() {
            Order order = waitPayOrder();
            when(orderRepo.findByIdAndBuyerId(20L, BUYER)).thenReturn(Optional.of(order));

            Product product = onSaleProduct(1);
            product.setQuantity(0);
            product.setStatus(ProductStatus.OFF_SALE);
            when(productRepo.findById(10L)).thenReturn(Optional.of(product));

            Order result = orderService.cancel(BUYER, 20L);

            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            assertEquals(1, product.getQuantity());
            assertEquals(ProductStatus.ON_SALE, product.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC03-11 非待支付状态订单不能取消,抛出 CONFLICT")
        void shouldRejectCancelWrongStatus() {
            Order order = waitPayOrder();
            order.setStatus(OrderStatus.WAIT_DELIVER);
            when(orderRepo.findByIdAndBuyerId(20L, BUYER)).thenReturn(Optional.of(order));

            AppException ex = assertThrows(AppException.class, () -> orderService.cancel(BUYER, 20L));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }
    }

    @Nested
    @DisplayName("用例4 发货 ship")
    class Ship {

        @Test
        @DisplayName("UNIT-TC04-01 卖家对已支付订单发货:运单创建、订单进入待收货")
        void shouldShipSuccessfully() {
            Order order = new Order();
            order.setBuyerId(BUYER);
            order.setSellerId(SELLER);
            order.setProductId(10L);
            order.setStatus(OrderStatus.WAIT_DELIVER);
            order.setPaidAt(LocalDateTime.now());
            TestId.set(order, 20L);
            when(orderRepo.findByIdAndSellerId(20L, SELLER)).thenReturn(Optional.of(order));
            when(shipmentRepo.findByOrderId(20L)).thenReturn(Optional.empty());
            when(shipmentRepo.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

            Shipment result = orderService.ship(SELLER, 20L,
                    new OrderService.ShipCommand("SF", "SF123456"));

            assertEquals(20L, result.getOrderId());
            assertEquals("SF", result.getCarrierCode());
            assertEquals("SF123456", result.getTrackingNo());
            assertEquals(ShipmentStatus.CREATED, result.getStatus());
            assertEquals(OrderStatus.WAIT_RECEIVE, order.getStatus());
            assertNotNull(order.getShippedAt());
        }

        @Test
        @DisplayName("UNIT-TC04-02 非待发货状态订单不能发货,抛出 CONFLICT")
        void shouldRejectShipWrongStatus() {
            Order order = new Order();
            order.setSellerId(SELLER);
            order.setStatus(OrderStatus.WAIT_PAY);
            TestId.set(order, 20L);
            when(orderRepo.findByIdAndSellerId(20L, SELLER)).thenReturn(Optional.of(order));

            AppException ex = assertThrows(AppException.class, () -> orderService.ship(SELLER, 20L,
                    new OrderService.ShipCommand("SF", "SF123456")));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC04-03 非卖家不能发货,抛出 NOT_FOUND")
        void shouldRejectShipByNonSeller() {
            when(orderRepo.findByIdAndSellerId(20L, 99L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () -> orderService.ship(99L, 20L,
                    new OrderService.ShipCommand("SF", "SF123456")));
            assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC04-04 重复发货时更新已有运单")
        void shouldUpdateExistingShipment() {
            Order order = new Order();
            order.setBuyerId(BUYER);
            order.setSellerId(SELLER);
            order.setStatus(OrderStatus.WAIT_DELIVER);
            TestId.set(order, 20L);
            when(orderRepo.findByIdAndSellerId(20L, SELLER)).thenReturn(Optional.of(order));

            Shipment existing = new Shipment();
            existing.setOrderId(20L);
            existing.setCarrierCode("SF");
            existing.setTrackingNo("OLD");
            when(shipmentRepo.findByOrderId(20L)).thenReturn(Optional.of(existing));
            when(shipmentRepo.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

            Shipment result = orderService.ship(SELLER, 20L,
                    new OrderService.ShipCommand("YT", "YT999"));

            assertSame(existing, result);
            assertEquals("YT", result.getCarrierCode());
            assertEquals("YT999", result.getTrackingNo());
        }
    }

    @Nested
    @DisplayName("用例3 确认收货与结算")
    class ConfirmAndSettle {

        @Test
        @DisplayName("UNIT-TC03-12 确认收货成功:订单完成,资金进入平台托管")
        void shouldConfirmReceived() {
            Order order = new Order();
            order.setBuyerId(BUYER);
            order.setSellerId(SELLER);
            order.setStatus(OrderStatus.WAIT_RECEIVE);
            order.setPaidAt(LocalDateTime.now());
            TestId.set(order, 20L);
            when(orderRepo.findByIdAndBuyerId(20L, BUYER)).thenReturn(Optional.of(order));

            Order result = orderService.confirmReceived(BUYER, 20L);

            assertEquals(OrderStatus.COMPLETED, result.getStatus());
            assertNotNull(result.getCompletedAt());
        }

        @Test
        @DisplayName("UNIT-TC03-13 非待收货状态不能确认收货,抛出 CONFLICT")
        void shouldRejectConfirmWrongStatus() {
            Order order = new Order();
            order.setBuyerId(BUYER);
            order.setStatus(OrderStatus.WAIT_PAY);
            TestId.set(order, 20L);
            when(orderRepo.findByIdAndBuyerId(20L, BUYER)).thenReturn(Optional.of(order));

            AppException ex = assertThrows(AppException.class, () -> orderService.confirmReceived(BUYER, 20L));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC03-14 售后期满自动结算:COMPLETED → SETTLED")
        void shouldSettleFunds() {
            Order order = new Order();
            order.setBuyerId(BUYER);
            order.setSellerId(SELLER);
            order.setStatus(OrderStatus.COMPLETED);
            order.setCompletedAt(LocalDateTime.now().minusDays(8));
            TestId.set(order, 20L);
            when(orderRepo.findById(20L)).thenReturn(Optional.of(order));

            Order result = orderService.settleFunds(20L);

            assertEquals(OrderStatus.SETTLED, result.getStatus());
            assertNotNull(result.getSettledAt());
        }

        @Test
        @DisplayName("UNIT-TC03-15 非完成状态订单不能结算,抛出 CONFLICT")
        void shouldRejectSettleWrongStatus() {
            Order order = new Order();
            order.setStatus(OrderStatus.WAIT_RECEIVE);
            TestId.set(order, 20L);
            when(orderRepo.findById(20L)).thenReturn(Optional.of(order));

            AppException ex = assertThrows(AppException.class, () -> orderService.settleFunds(20L));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }
    }

    @Nested
    @DisplayName("收货信息与订单详情")
    class ReceiverAndDetail {

        @Test
        @DisplayName("UNIT-TC03-16 非买家不能修改收货信息,抛出 FORBIDDEN")
        void shouldRejectUpdateReceiverByNonBuyer() {
            Order order = new Order();
            order.setBuyerId(BUYER);
            order.setStatus(OrderStatus.WAIT_PAY);
            TestId.set(order, 20L);
            when(orderRepo.findById(20L)).thenReturn(Optional.of(order));

            AppException ex = assertThrows(AppException.class,
                    () -> orderService.updateReceiver(99L, 20L, "李四", "139", "上海"));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC03-17 无关用户查看订单详情被拒绝,抛出 FORBIDDEN")
        void shouldRejectDetailByOtherUser() {
            Order order = new Order();
            order.setBuyerId(BUYER);
            order.setSellerId(SELLER);
            TestId.set(order, 20L);
            when(orderRepo.findById(20L)).thenReturn(Optional.of(order));

            AppException ex = assertThrows(AppException.class, () -> orderService.getOrderDetail(99L, 20L));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC03-18 待支付订单详情:买家可支付、可取消,资金未托管")
        void shouldComputeDetailFlagsForWaitPay() {
            Order order = new Order();
            order.setBuyerId(BUYER);
            order.setSellerId(SELLER);
            order.setStatus(OrderStatus.WAIT_PAY);
            TestId.set(order, 20L);
            when(orderRepo.findById(20L)).thenReturn(Optional.of(order));
            when(shipmentRepo.findByOrderId(20L)).thenReturn(Optional.empty());
            when(orderEventRepo.findByOrderIdOrderByIdAsc(20L)).thenReturn(List.of());

            OrderService.OrderDetail detail = orderService.getOrderDetail(BUYER, 20L);

            assertTrue(detail.canPay());
            assertTrue(detail.canCancel());
            assertFalse(detail.canShip());
            assertFalse(detail.canConfirm());
            assertFalse(detail.canApplyAfterSale());
            assertFalse(detail.fundsInEscrow());
        }

        @Test
        @DisplayName("UNIT-TC03-19 完成7天内的订单详情:买家可发起售后,资金托管中")
        void shouldComputeDetailFlagsForCompleted() {
            Order order = new Order();
            order.setBuyerId(BUYER);
            order.setSellerId(SELLER);
            order.setStatus(OrderStatus.COMPLETED);
            order.setCompletedAt(LocalDateTime.now().minusDays(1));
            TestId.set(order, 20L);
            when(orderRepo.findById(20L)).thenReturn(Optional.of(order));
            when(shipmentRepo.findByOrderId(20L)).thenReturn(Optional.empty());
            when(orderEventRepo.findByOrderIdOrderByIdAsc(20L)).thenReturn(List.of());

            OrderService.OrderDetail detail = orderService.getOrderDetail(BUYER, 20L);

            assertTrue(detail.canApplyAfterSale());
            assertTrue(detail.fundsInEscrow());
            assertNotNull(detail.settlementDueAt());
        }
    }

    @Nested
    @DisplayName("卖家已售商品")
    class SellerSold {

        @Test
        @DisplayName("UNIT-TC03-20 卖家已售商品列表:含商品信息,无评分时评分为空")
        void shouldListSellerSoldProducts() {
            Order order = new Order();
            order.setBuyerId(BUYER);
            order.setSellerId(SELLER);
            order.setProductId(10L);
            order.setAmountCent(5000);
            order.setStatus(OrderStatus.COMPLETED);
            order.setCompletedAt(LocalDateTime.now());
            TestId.set(order, 20L);
            when(orderRepo.findBySellerIdAndStatusOrderByCreatedAtDesc(eq(SELLER), eq(OrderStatus.COMPLETED), any()))
                    .thenReturn(new PageImpl<>(List.of(order)));

            Product product = onSaleProduct(0);
            product.setTitle("iPhone 13");
            product.setCoverImageUrl("/uploads/a.jpg");
            when(productService.getById(10L)).thenReturn(product);
            when(ratingRepo.findByOrderId(20L)).thenReturn(Optional.empty());

            List<OrderService.SoldProductDto> result = orderService.getSellerSoldProducts(SELLER);

            assertEquals(1, result.size());
            assertEquals("iPhone 13", result.get(0).productTitle());
            assertEquals(5000, result.get(0).priceCent());
            assertNull(result.get(0).ratingScore());
        }
    }
}
