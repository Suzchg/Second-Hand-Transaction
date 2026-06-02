package com.secondhand.aftersale.entity;

public enum AfterSaleStatus {
    REQUESTED,        // 买家已申请
    APPROVED,         // 卖家已同意（待买家寄回 或 直接退款）
    REJECTED,         // 卖家已拒绝
    RETURN_SHIPPED,   // 买家已寄回退货
    RETURN_RECEIVED,  // 卖家已收到退货
    REFUNDED,         // 已退款
    CLOSED            // 已关闭
}
