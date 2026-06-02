package com.secondhand.payment;

/**
 * 支付服务抽象接口。
 * 当前使用 Mock 实现，接入真实支付宝/微信支付时只需实现此接口。
 *
 * 真实支付流程：
 * 1. createPayment → 返回支付链接/二维码 → 用户跳转支付
 * 2. 支付平台异步回调 → handleCallback → 更新订单状态
 * 3. 若未收到回调 → queryPayment 主动查询
 * 4. 退款 → refund
 */
public interface PaymentService {

    /** 创建支付单，返回支付信息 */
    PaymentResult createPayment(CreatePaymentCommand cmd);

    /** 查询支付状态 */
    PaymentStatus queryPayment(String paymentNo);

    /** 处理支付平台异步回调 */
    void handleCallback(String platform, java.util.Map<String, String> params);

    /** 退款 */
    RefundResult refund(String paymentNo, Integer amountCent, String reason);

    // ---- Types ----

    record CreatePaymentCommand(Long orderId, Long userId, Integer amountCent,
                                 PaymentMethod method, String subject) {}

    record PaymentResult(String paymentNo, String qrCodeUrl, String payUrl, PaymentStatus status) {}

    record RefundResult(String refundNo, PaymentStatus status) {}

    enum PaymentStatus { WAIT_PAY, PAID, REFUNDING, REFUNDED, CLOSED }

    enum PaymentMethod { ALIPAY, WECHAT }
}
