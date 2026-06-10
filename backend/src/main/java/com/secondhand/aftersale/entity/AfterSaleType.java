package com.secondhand.aftersale.entity;

public enum AfterSaleType {
    /** 仅退款（未发货/未签收，不用退货） */
    REFUND_NOT_SHIPPED,
    /** 仅退款（已收到货、不退回商品） */
    REFUND_RECEIVED,
    /** 退货退款 */
    RETURN_REFUND,
    /** 部分退款（留用折价） */
    PARTIAL_REFUND
}
