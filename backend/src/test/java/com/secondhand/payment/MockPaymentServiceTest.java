package com.secondhand.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock 支付服务单元测试(用例3 支付)。
 * 覆盖创建支付单、查询、异步回调、退款与 mockPay 各分支。
 */
@DisplayName("MockPaymentService 单元测试")
class MockPaymentServiceTest {

    MockPaymentService paymentService;

    @BeforeEach
    void buildService() {
        paymentService = new MockPaymentService();
    }

    @Nested
    @DisplayName("创建与查询")
    class CreateAndQuery {

        @Test
        @DisplayName("UNIT-TC03-21 创建支付单:返回唯一支付号与待支付状态")
        void shouldCreatePayment() {
            PaymentService.PaymentResult result = paymentService.createPayment(
                    new PaymentService.CreatePaymentCommand(20L, 1L, 5000,
                            PaymentService.PaymentMethod.ALIPAY, "商品款"));

            assertNotNull(result.paymentNo());
            assertTrue(result.paymentNo().startsWith("PAY-"));
            assertEquals(PaymentService.PaymentStatus.WAIT_PAY, result.status());
            assertEquals(PaymentService.PaymentStatus.WAIT_PAY,
                    paymentService.queryPayment(result.paymentNo()));
        }

        @Test
        @DisplayName("UNIT-TC03-22 查询不存在的支付单:返回已关闭")
        void shouldReturnClosedForUnknownPayment() {
            assertEquals(PaymentService.PaymentStatus.CLOSED,
                    paymentService.queryPayment("PAY-NOT-EXIST"));
        }
    }

    @Nested
    @DisplayName("支付回调")
    class Callback {

        @Test
        @DisplayName("UNIT-TC03-23 支付成功回调后,支付单状态变为已支付")
        void shouldMarkPaidOnSuccessCallback() {
            String paymentNo = paymentService.createPayment(
                    new PaymentService.CreatePaymentCommand(20L, 1L, 5000,
                            PaymentService.PaymentMethod.WECHAT, "商品款")).paymentNo();

            Map<String, String> params = new HashMap<>();
            params.put("out_trade_no", paymentNo);
            params.put("trade_status", "TRADE_SUCCESS");
            paymentService.handleCallback("WECHAT", params);

            assertEquals(PaymentService.PaymentStatus.PAID,
                    paymentService.queryPayment(paymentNo));
        }

        @Test
        @DisplayName("UNIT-TC03-24 非成功回调不改变支付状态")
        void shouldIgnoreNonSuccessCallback() {
            String paymentNo = paymentService.createPayment(
                    new PaymentService.CreatePaymentCommand(20L, 1L, 5000,
                            PaymentService.PaymentMethod.ALIPAY, "商品款")).paymentNo();

            Map<String, String> params = new HashMap<>();
            params.put("out_trade_no", paymentNo);
            params.put("trade_status", "TRADE_CLOSED");
            paymentService.handleCallback("ALIPAY", params);

            assertEquals(PaymentService.PaymentStatus.WAIT_PAY,
                    paymentService.queryPayment(paymentNo));
        }
    }

    @Nested
    @DisplayName("退款")
    class Refund {

        @Test
        @DisplayName("UNIT-TC03-25 退款已存在的支付单:返回退款号与已退款状态")
        void shouldRefundExistingPayment() {
            String paymentNo = paymentService.createPayment(
                    new PaymentService.CreatePaymentCommand(20L, 1L, 5000,
                            PaymentService.PaymentMethod.ALIPAY, "商品款")).paymentNo();

            PaymentService.RefundResult result = paymentService.refund(paymentNo, 5000, "售后退款");

            assertNotNull(result.refundNo());
            assertTrue(result.refundNo().startsWith("REF-"));
            assertEquals(PaymentService.PaymentStatus.REFUNDED, result.status());
            assertEquals(PaymentService.PaymentStatus.REFUNDED,
                    paymentService.queryPayment(paymentNo));
        }

        @Test
        @DisplayName("UNIT-TC03-26 退款不存在的支付单:返回已关闭且无退款号")
        void shouldRejectRefundUnknownPayment() {
            PaymentService.RefundResult result = paymentService.refund("PAY-NOT-EXIST", 5000, "售后退款");

            assertNull(result.refundNo());
            assertEquals(PaymentService.PaymentStatus.CLOSED, result.status());
        }
    }

    @Test
    @DisplayName("UNIT-TC03-27 mockPay 将支付单直接标记为已支付")
    void shouldMockPay() {
        String paymentNo = paymentService.createPayment(
                new PaymentService.CreatePaymentCommand(20L, 1L, 5000,
                        PaymentService.PaymentMethod.ALIPAY, "商品款")).paymentNo();

        paymentService.mockPay(paymentNo);

        assertEquals(PaymentService.PaymentStatus.PAID,
                paymentService.queryPayment(paymentNo));
    }
}
