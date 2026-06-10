package com.secondhand.aftersale.entity;

public enum AfterSaleStatus {
    /** 买家已提交售后申请，等待卖家审核 */
    REQUESTED,
    /** 卖家同意售后（进入待买家寄件/待退款） */
    APPROVED,
    /** 卖家拒绝售后 */
    REJECTED,
    /** 买家已寄回退货（仅退货退款） */
    RETURN_SHIPPED,
    /** 卖家已确认收到退货 */
    RETURN_CONFIRMED,
    /** 已退款，售后完结 */
    REFUNDED,
    /** 售后关闭（超时/买家取消/平台驳回） */
    CLOSED,
    /** 已申请平台介入（仲裁中） */
    PLATFORM_ARBITRATION
}
