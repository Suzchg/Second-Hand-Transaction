package com.secondhand.payment;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock 支付服务实现。
 * 开发环境使用，模拟支付流程。生产环境替换为 AlipayPaymentService / WechatPaymentService。
 */
@Service
public class MockPaymentService implements PaymentService {

    private final Map<String, PaymentStatus> store = new ConcurrentHashMap<>();

    @Override
    public PaymentResult createPayment(CreatePaymentCommand cmd) {
        String paymentNo = "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        store.put(paymentNo, PaymentStatus.WAIT_PAY);
        return new PaymentResult(paymentNo, null, null, PaymentStatus.WAIT_PAY);
    }

    @Override
    public PaymentStatus queryPayment(String paymentNo) {
        return store.getOrDefault(paymentNo, PaymentStatus.CLOSED);
    }

    @Override
    public void handleCallback(String platform, Map<String, String> params) {
        String paymentNo = params.get("out_trade_no");
        String status = params.get("trade_status");
        if ("TRADE_SUCCESS".equals(status) && paymentNo != null) {
            store.put(paymentNo, PaymentStatus.PAID);
        }
    }

    @Override
    public RefundResult refund(String paymentNo, Integer amountCent, String reason) {
        if (!store.containsKey(paymentNo)) {
            return new RefundResult(null, PaymentStatus.CLOSED);
        }
        store.put(paymentNo, PaymentStatus.REFUNDED);
        String refundNo = "REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        return new RefundResult(refundNo, PaymentStatus.REFUNDED);
    }

    /** Mock：将支付单标记为已支付（供前端开发测试） */
    public void mockPay(String paymentNo) {
        store.put(paymentNo, PaymentStatus.PAID);
    }
}
