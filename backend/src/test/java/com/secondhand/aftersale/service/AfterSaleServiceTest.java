package com.secondhand.aftersale.service;

import com.secondhand.aftersale.entity.AfterSaleRequest;
import com.secondhand.aftersale.entity.AfterSaleStatus;
import com.secondhand.aftersale.entity.AfterSaleType;
import com.secondhand.aftersale.repository.AfterSaleRepository;
import com.secondhand.common.AppException;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.testutil.TestId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 售后处理单元测试(用例6 售后处理:申请+审批+仲裁)。
 * 覆盖状态机、超时自动流转、订单状态联动(全额退款→作废/部分退款→保留/驳回→恢复)。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AfterSaleService 单元测试")
class AfterSaleServiceTest {

    @Mock
    AfterSaleRepository afterSaleRepo;
    @Mock
    OrderRepository orderRepo;

    AfterSaleService afterSaleService;

    private static final long BUYER = 1L;
    private static final long SELLER = 2L;
    private static final long ORDER_ID = 100L;
    private static final long REQ_ID = 200L;

    @BeforeEach
    void buildService() {
        afterSaleService = new AfterSaleService(afterSaleRepo, orderRepo);
        // 共享 stub 对异常路径测试不可见,用 lenient 避免 UnnecessaryStubbingException
        lenient().when(afterSaleRepo.save(any(AfterSaleRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** 已完成订单:确认收货 1 天前,金额 100 元 */
    private Order completedOrder() {
        Order o = new Order();
        o.setBuyerId(BUYER);
        o.setSellerId(SELLER);
        o.setProductId(10L);
        o.setAmountCent(10000);
        o.setStatus(OrderStatus.COMPLETED);
        o.setPaidAt(LocalDateTime.now().minusDays(3));
        o.setCompletedAt(LocalDateTime.now().minusDays(1));
        TestId.set(o, ORDER_ID);
        return o;
    }

    private AfterSaleRequest pendingRequest(AfterSaleStatus status, AfterSaleType type) {
        AfterSaleRequest req = new AfterSaleRequest();
        req.setOrderId(ORDER_ID);
        req.setBuyerId(BUYER);
        req.setSellerId(SELLER);
        req.setRequestType(type);
        req.setReason("商品有瑕疵");
        req.setStatus(status);
        req.setRefundAmountCent(10000);
        TestId.set(req, REQ_ID);
        return req;
    }

    private AfterSaleService.RequestCommand requestCmd() {
        return new AfterSaleService.RequestCommand(ORDER_ID, AfterSaleType.REFUND_NOT_SHIPPED,
                "商品有瑕疵", null, "图片证据");
    }

    @Nested
    @DisplayName("用例6 发起售后申请 request")
    class Request {

        @Test
        @DisplayName("UNIT-TC06-01 确认收货7天内发起售后成功:进入待卖家审核,72h 处理时效")
        void shouldRequestSuccessfully() {
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(completedOrder()));

            AfterSaleRequest result = afterSaleService.request(BUYER, requestCmd());

            assertEquals(AfterSaleStatus.REQUESTED, result.getStatus());
            assertEquals(AfterSaleType.REFUND_NOT_SHIPPED, result.getRequestType());
            assertEquals(BUYER, result.getBuyerId());
            assertEquals(SELLER, result.getSellerId());
            assertEquals(10000, result.getRefundAmountCent());
            assertNotNull(result.getDeadlineAt());
            assertTrue(result.getDeadlineAt().isAfter(LocalDateTime.now().plusDays(2))); // 72h
            assertNull(result.getRefundedAt());
        }

        @Test
        @DisplayName("UNIT-TC06-02 非买家不能发起售后,抛出 FORBIDDEN")
        void shouldRejectRequestByNonBuyer() {
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(completedOrder()));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.request(99L, requestCmd()));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
            verify(afterSaleRepo, never()).save(any(AfterSaleRequest.class));
        }

        @Test
        @DisplayName("UNIT-TC06-03 确认收货前不能手动发起售后,抛出 FORBIDDEN")
        void shouldRejectRequestBeforeComplete() {
            Order order = completedOrder();
            order.setCompletedAt(null);
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.request(BUYER, requestCmd()));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-04 超过7天售后窗口期发起售后被拒绝,抛出 GONE")
        void shouldRejectRequestAfterWindow() {
            Order order = completedOrder();
            order.setCompletedAt(LocalDateTime.now().minusDays(8));
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.request(BUYER, requestCmd()));
            assertEquals(HttpStatus.GONE, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-05 已有进行中售后时不能重复发起,抛出 CONFLICT")
        void shouldRejectDuplicateRequest() {
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(completedOrder()));
            when(afterSaleRepo.findByOrderIdAndStatusNotIn(eq(ORDER_ID), any()))
                    .thenReturn(List.of(pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.REFUND_NOT_SHIPPED)));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.request(BUYER, requestCmd()));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-06 退款金额超过订单金额时按订单金额封顶")
        void shouldCapRefundAmount() {
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(completedOrder()));

            AfterSaleRequest result = afterSaleService.request(BUYER, new AfterSaleService.RequestCommand(
                    ORDER_ID, AfterSaleType.REFUND_NOT_SHIPPED, "原因", 20000, null));

            assertEquals(10000, result.getRefundAmountCent());
        }

        @Test
        @DisplayName("UNIT-TC06-07 发起售后将订单标记为售后处理中")
        void shouldMarkOrderAfterSale() {
            Order order = completedOrder();
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            afterSaleService.request(BUYER, requestCmd());

            assertEquals(OrderStatus.AFTER_SALE, order.getStatus());
            verify(orderRepo).save(order);
        }
    }

    @Nested
    @DisplayName("用例6 卖家审批 approve/reject")
    class ApproveReject {

        @Test
        @DisplayName("UNIT-TC06-08 同意仅退款:直接退款,订单全额退款后作废")
        void shouldApproveRefundNotShipped() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.REFUND_NOT_SHIPPED);
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));
            Order order = completedOrder();
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            AfterSaleRequest result = afterSaleService.approve(SELLER, REQ_ID);

            assertEquals(AfterSaleStatus.REFUNDED, result.getStatus());
            assertNotNull(result.getRefundedAt());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-09 同意退货退款:进入待买家寄件,7天寄件时效")
        void shouldApproveReturnRefund() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.RETURN_REFUND);
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));

            AfterSaleRequest result = afterSaleService.approve(SELLER, REQ_ID);

            assertEquals(AfterSaleStatus.APPROVED, result.getStatus());
            assertTrue(result.getDeadlineAt().isAfter(LocalDateTime.now().plusDays(6))); // 7天
            verify(orderRepo, never()).findById(anyLong()); // 不触发关单
        }

        @Test
        @DisplayName("UNIT-TC06-10 非卖家不能审批售后,抛出 FORBIDDEN")
        void shouldRejectApproveByNonSeller() {
            when(afterSaleRepo.findById(REQ_ID))
                    .thenReturn(Optional.of(pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.REFUND_NOT_SHIPPED)));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.approve(99L, REQ_ID));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-11 已完结售后不能重复审批,抛出 CONFLICT")
        void shouldRejectApproveFinished() {
            when(afterSaleRepo.findById(REQ_ID))
                    .thenReturn(Optional.of(pendingRequest(AfterSaleStatus.REFUNDED, AfterSaleType.REFUND_NOT_SHIPPED)));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.approve(SELLER, REQ_ID));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-12 卖家拒绝售后:记录拒绝理由,买家3天内可申请平台介入")
        void shouldRejectWithNote() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.REFUND_NOT_SHIPPED);
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));

            AfterSaleRequest result = afterSaleService.reject(SELLER, REQ_ID, "商品与描述一致");

            assertEquals(AfterSaleStatus.REJECTED, result.getStatus());
            assertEquals("商品与描述一致", result.getSellerResponse());
            assertTrue(result.getDeadlineAt().isAfter(LocalDateTime.now().plusDays(2))); // 3天申诉
        }
    }

    @Nested
    @DisplayName("用例6 退货寄回与收货确认")
    class ReturnFlow {

        @Test
        @DisplayName("UNIT-TC06-13 买家寄回退货:进入待卖家收货,10天确认时效")
        void shouldReturnShip() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.APPROVED, AfterSaleType.RETURN_REFUND);
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));

            AfterSaleRequest result = afterSaleService.returnShip(BUYER, REQ_ID, "SF", "SF888");

            assertEquals(AfterSaleStatus.RETURN_SHIPPED, result.getStatus());
            assertEquals("SF", result.getReturnCarrierCode());
            assertEquals("SF888", result.getReturnTrackingNo());
            assertTrue(result.getDeadlineAt().isAfter(LocalDateTime.now().plusDays(9))); // 10天
        }

        @Test
        @DisplayName("UNIT-TC06-14 非待寄件状态不能寄回,抛出 CONFLICT")
        void shouldRejectReturnShipWrongStatus() {
            when(afterSaleRepo.findById(REQ_ID))
                    .thenReturn(Optional.of(pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.RETURN_REFUND)));

            AppException ex = assertThrows(AppException.class,
                    () -> afterSaleService.returnShip(BUYER, REQ_ID, "SF", "SF888"));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-15 仅退货退款类型需要寄回,抛出 CONFLICT")
        void shouldRejectReturnShipWrongType() {
            when(afterSaleRepo.findById(REQ_ID))
                    .thenReturn(Optional.of(pendingRequest(AfterSaleStatus.APPROVED, AfterSaleType.REFUND_NOT_SHIPPED)));

            AppException ex = assertThrows(AppException.class,
                    () -> afterSaleService.returnShip(BUYER, REQ_ID, "SF", "SF888"));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-16 卖家确认收货:退款并作废订单")
        void shouldConfirmReturn() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.RETURN_SHIPPED, AfterSaleType.RETURN_REFUND);
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));
            Order order = completedOrder();
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            AfterSaleRequest result = afterSaleService.confirmReturn(SELLER, REQ_ID);

            assertEquals(AfterSaleStatus.REFUNDED, result.getStatus());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-17 非待收货状态不能确认收货,抛出 CONFLICT")
        void shouldRejectConfirmReturnWrongStatus() {
            when(afterSaleRepo.findById(REQ_ID))
                    .thenReturn(Optional.of(pendingRequest(AfterSaleStatus.APPROVED, AfterSaleType.RETURN_REFUND)));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.confirmReturn(SELLER, REQ_ID));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-18 卖家拒绝收货:售后被拒绝,买家可申请平台介入")
        void shouldRejectReturn() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.RETURN_SHIPPED, AfterSaleType.RETURN_REFUND);
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));

            AfterSaleRequest result = afterSaleService.rejectReturn(SELLER, REQ_ID, "商品有损坏");

            assertEquals(AfterSaleStatus.REJECTED, result.getStatus());
            assertEquals("商品有损坏", result.getSellerResponse());
            assertTrue(result.getDeadlineAt().isAfter(LocalDateTime.now().plusDays(2))); // 3天申诉
        }
    }

    @Nested
    @DisplayName("用例6 平台仲裁")
    class Arbitration {

        private AfterSaleRequest arbitrationRequest() {
            return pendingRequest(AfterSaleStatus.PLATFORM_ARBITRATION, AfterSaleType.RETURN_REFUND);
        }

        @Test
        @DisplayName("UNIT-TC06-19 卖家拒绝后买家申请平台介入:5天仲裁时效")
        void shouldEscalateToPlatform() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.REJECTED, AfterSaleType.RETURN_REFUND);
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));

            AfterSaleRequest result = afterSaleService.escalateToPlatform(BUYER, REQ_ID, "补充证据");

            assertEquals(AfterSaleStatus.PLATFORM_ARBITRATION, result.getStatus());
            assertTrue(result.getDeadlineAt().isAfter(LocalDateTime.now().plusDays(4))); // 5天
        }

        @Test
        @DisplayName("UNIT-TC06-20 非拒绝状态不能申请平台介入,抛出 CONFLICT")
        void shouldRejectEscalateWrongStatus() {
            when(afterSaleRepo.findById(REQ_ID))
                    .thenReturn(Optional.of(pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.RETURN_REFUND)));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.escalateToPlatform(BUYER, REQ_ID, null));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-21 仲裁全额退款:退款并作废订单")
        void shouldArbitrateFullRefund() {
            AfterSaleRequest req = arbitrationRequest();
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));
            Order order = completedOrder();
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            AfterSaleRequest result = afterSaleService.adminArbitrate(9L, REQ_ID,
                    new AfterSaleService.AdminArbitrateCommand(
                            "FULL_REFUND", "SELLER", "SELLER", 1500, null, "商品与描述不符"));

            assertEquals(AfterSaleStatus.REFUNDED, result.getStatus());
            assertNotNull(result.getRefundedAt());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-22 仲裁部分退款:退款但订单保持已完成(买家留用)")
        void shouldArbitratePartialRefund() {
            AfterSaleRequest req = arbitrationRequest();
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));
            Order order = completedOrder();
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            AfterSaleRequest result = afterSaleService.adminArbitrate(9L, REQ_ID,
                    new AfterSaleService.AdminArbitrateCommand(
                            "PARTIAL_REFUND", "SELLER", null, null, 3000, null));

            assertEquals(AfterSaleStatus.REFUNDED, result.getStatus());
            assertEquals(3000, result.getRefundAmountCent());
            assertEquals(OrderStatus.COMPLETED, order.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-23 仲裁驳回:关闭售后并按进度恢复订单状态")
        void shouldArbitrateDismiss() {
            AfterSaleRequest req = arbitrationRequest();
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));
            Order order = completedOrder();
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            AfterSaleRequest result = afterSaleService.adminArbitrate(9L, REQ_ID,
                    new AfterSaleService.AdminArbitrateCommand("DISMISS", null, null, null, null, "证据不足"));

            assertEquals(AfterSaleStatus.CLOSED, result.getStatus());
            assertNotNull(result.getClosedAt());
            assertEquals(OrderStatus.COMPLETED, order.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-24 仲裁退货退款:进入待买家寄件")
        void shouldArbitrateReturnRefund() {
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(arbitrationRequest()));

            AfterSaleRequest result = afterSaleService.adminArbitrate(9L, REQ_ID,
                    new AfterSaleService.AdminArbitrateCommand("RETURN_REFUND", null, null, null, null, null));

            assertEquals(AfterSaleStatus.APPROVED, result.getStatus());
            assertTrue(result.getDeadlineAt().isAfter(LocalDateTime.now().plusDays(6))); // 7天寄件
        }

        @Test
        @DisplayName("UNIT-TC06-25 仲裁结果含责任判定与运费归属")
        void shouldRecordArbitrationDetail() {
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(arbitrationRequest()));

            AfterSaleRequest result = afterSaleService.adminArbitrate(9L, REQ_ID,
                    new AfterSaleService.AdminArbitrateCommand(
                            "DISMISS", "BUYER", "BUYER", 1500, null, "买家举证不足"));

            assertTrue(result.getArbitrationResult().contains("驳回售后申请"));
            assertTrue(result.getArbitrationResult().contains("责任方：买家"));
            assertTrue(result.getArbitrationResult().contains("运费承担：买家承担"));
            assertTrue(result.getArbitrationResult().contains("15.00"));
            assertEquals("BUYER", result.getResponsibility());
            assertEquals(1500, result.getShippingCostCent());
        }

        @Test
        @DisplayName("UNIT-TC06-26 无效仲裁结果被拒绝,抛出 BAD_REQUEST")
        void shouldRejectInvalidArbitrateResult() {
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(arbitrationRequest()));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.adminArbitrate(9L, REQ_ID,
                    new AfterSaleService.AdminArbitrateCommand("XXX", null, null, null, null, null)));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-27 未进入仲裁状态不能仲裁,抛出 CONFLICT")
        void shouldRejectArbitrateWrongStatus() {
            when(afterSaleRepo.findById(REQ_ID))
                    .thenReturn(Optional.of(pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.RETURN_REFUND)));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.adminArbitrate(9L, REQ_ID,
                    new AfterSaleService.AdminArbitrateCommand("FULL_REFUND", null, null, null, null, null)));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }
    }

    @Nested
    @DisplayName("用例6 超时自动处理 processTimeouts")
    class Timeouts {

        private void stubExpired(AfterSaleRequest... reqs) {
            when(afterSaleRepo.findByStatusNotInAndDeadlineAtBefore(anyList(), any(LocalDateTime.class)))
                    .thenReturn(List.of(reqs));
        }

        @Test
        @DisplayName("UNIT-TC06-28 卖家72h超时未处理仅退款:自动同意并退款")
        void shouldAutoRefundNotShipped() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.REFUND_NOT_SHIPPED);
            stubExpired(req);
            Order order = completedOrder();
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            afterSaleService.processTimeouts();

            assertEquals(AfterSaleStatus.REFUNDED, req.getStatus());
            assertNotNull(req.getRefundedAt());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-29 卖家72h超时未处理退货退款:自动同意进入待寄件")
        void shouldAutoApproveReturnRefund() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.RETURN_REFUND);
            stubExpired(req);

            afterSaleService.processTimeouts();

            assertEquals(AfterSaleStatus.APPROVED, req.getStatus());
            assertTrue(req.getDeadlineAt().isAfter(LocalDateTime.now().plusDays(6))); // 7天寄件
        }

        @Test
        @DisplayName("UNIT-TC06-30 买家3天未申请平台介入:售后关闭并恢复订单")
        void shouldCloseRejectedTimeout() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.REJECTED, AfterSaleType.RETURN_REFUND);
            stubExpired(req);
            Order order = completedOrder();
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            afterSaleService.processTimeouts();

            assertEquals(AfterSaleStatus.CLOSED, req.getStatus());
            assertNotNull(req.getClosedAt());
            assertEquals(OrderStatus.COMPLETED, order.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-31 买家7天未寄件:售后关闭并恢复订单")
        void shouldCloseApprovedTimeout() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.APPROVED, AfterSaleType.RETURN_REFUND);
            stubExpired(req);
            Order order = completedOrder();
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            afterSaleService.processTimeouts();

            assertEquals(AfterSaleStatus.CLOSED, req.getStatus());
            assertEquals(OrderStatus.COMPLETED, order.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-32 卖家10天未确认收货:自动退款并作废订单")
        void shouldAutoRefundReturnShipped() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.RETURN_SHIPPED, AfterSaleType.RETURN_REFUND);
            stubExpired(req);
            Order order = completedOrder();
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            afterSaleService.processTimeouts();

            assertEquals(AfterSaleStatus.REFUNDED, req.getStatus());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-33 待收货14天超时:系统自动发起仅退款售后")
        void shouldAutoTriggerTimeoutAfterSale() {
            Order order = completedOrder();
            order.setStatus(OrderStatus.WAIT_RECEIVE);
            order.setCompletedAt(null);
            order.setShippedAt(LocalDateTime.now().minusDays(15));
            when(orderRepo.findByStatusAndShippedAtBefore(eq(OrderStatus.WAIT_RECEIVE), any(LocalDateTime.class)))
                    .thenReturn(List.of(order));

            int count = afterSaleService.autoTriggerTimeoutAfterSale();

            assertEquals(1, count);
            assertEquals(OrderStatus.AFTER_SALE, order.getStatus());

            ArgumentCaptor<AfterSaleRequest> captor = ArgumentCaptor.forClass(AfterSaleRequest.class);
            verify(afterSaleRepo).save(captor.capture());
            AfterSaleRequest saved = captor.getValue();
            assertEquals(AfterSaleType.REFUND_NOT_SHIPPED, saved.getRequestType());
            assertEquals(AfterSaleStatus.REQUESTED, saved.getStatus());
            assertEquals(10000, saved.getRefundAmountCent());
        }

        @Test
        @DisplayName("UNIT-TC06-34 已有进行中售后的超时订单跳过,不重复发起")
        void shouldSkipExistingAfterSale() {
            Order order = completedOrder();
            order.setStatus(OrderStatus.WAIT_RECEIVE);
            order.setCompletedAt(null);
            order.setShippedAt(LocalDateTime.now().minusDays(15));
            when(orderRepo.findByStatusAndShippedAtBefore(eq(OrderStatus.WAIT_RECEIVE), any(LocalDateTime.class)))
                    .thenReturn(List.of(order));
            when(afterSaleRepo.findByOrderIdAndStatusNotIn(eq(ORDER_ID), any()))
                    .thenReturn(List.of(pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.REFUND_NOT_SHIPPED)));

            int count = afterSaleService.autoTriggerTimeoutAfterSale();

            assertEquals(0, count);
            verify(afterSaleRepo, never()).save(any(AfterSaleRequest.class));
            verify(orderRepo, never()).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("用例6 买家取消售后")
    class Cancel {

        @Test
        @DisplayName("UNIT-TC06-35 买家取消售后:关闭并按进度恢复订单状态(已发货→待收货)")
        void shouldCancelByBuyer() {
            AfterSaleRequest req = pendingRequest(AfterSaleStatus.REQUESTED, AfterSaleType.RETURN_REFUND);
            when(afterSaleRepo.findById(REQ_ID)).thenReturn(Optional.of(req));

            Order order = completedOrder();
            order.setCompletedAt(null);
            order.setShippedAt(LocalDateTime.now().minusDays(2)); // 已发货未收货
            when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(order));

            AfterSaleRequest result = afterSaleService.cancelByBuyer(BUYER, REQ_ID);

            assertEquals(AfterSaleStatus.CLOSED, result.getStatus());
            assertEquals(OrderStatus.WAIT_RECEIVE, order.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC06-36 已完结售后不能取消,抛出 CONFLICT")
        void shouldRejectCancelFinished() {
            when(afterSaleRepo.findById(REQ_ID))
                    .thenReturn(Optional.of(pendingRequest(AfterSaleStatus.REFUNDED, AfterSaleType.RETURN_REFUND)));

            AppException ex = assertThrows(AppException.class, () -> afterSaleService.cancelByBuyer(BUYER, REQ_ID));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }
    }
}
