package com.secondhand.order.entity;

/**
 * 订单状态机（含平台担保交易）。
 *
 * 流转:
 *   WAIT_PAY → WAIT_DELIVER → WAIT_RECEIVE → COMPLETED → SETTLED
 *   任意非终态 → CANCELLED
 *   WAIT_RECEIVE/COMPLETED → AFTER_SALE
 *
 * 资金流:
 *   买家支付 → 平台托管（WAIT_DELIVER ~ COMPLETED）
 *   买家确认收货 + 15天售后期过 → 平台结算给卖家（SETTLED）
 */
public enum OrderStatus {
    /** 待支付（订单已创建，等待买家付款） */
    WAIT_PAY,
    /** 待发货（买家已付款，资金由平台托管，等待卖家发货） */
    WAIT_DELIVER,
    /** 待收货（卖家已发货，等待买家确认收货） */
    WAIT_RECEIVE,
    /** 已完成（买家确认收货，资金仍在平台托管，15天售后期中） */
    COMPLETED,
    /** 已结算（售后期满，资金已划转卖家，订单彻底完结） */
    SETTLED,
    /** 已取消 */
    CANCELLED,
    /** 售后处理中 */
    AFTER_SALE
}
