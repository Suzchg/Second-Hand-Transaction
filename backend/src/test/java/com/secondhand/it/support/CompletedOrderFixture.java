package com.secondhand.it.support;

/** 已完成订单链路（注册→发布→下单→支付→发货→确认收货）的句柄集合 */
public record CompletedOrderFixture(TestUser seller, TestUser buyer, long productId, long orderId) {
}
