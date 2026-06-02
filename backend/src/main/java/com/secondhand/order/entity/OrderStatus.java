package com.secondhand.order.entity;

/**
 * 订单状态机。
 *
 * 流转: WAIT_PAY → WAIT_DELIVER → WAIT_RECEIVE → COMPLETED
 *       任意非终态 → CANCELLED（取消/售后批准）
 *       WAIT_RECEIVE/COMPLETED → AFTER_SALE（售后申请中）
 */
public enum OrderStatus {
    WAIT_PAY,       // 待支付（订单已创建，等待付款）
    WAIT_DELIVER,   // 待发货（已支付，等待卖家发货）
    WAIT_RECEIVE,   // 待收货（已发货，等待买家确认）
    COMPLETED,      // 已完成（买家确认收货）
    CANCELLED,      // 已取消（超时取消/买家取消/售后批准取消）
    AFTER_SALE      // 售后处理中（买家申请售后，订单暂停）
}
