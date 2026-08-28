package com.secondhand.it;

import com.secondhand.it.support.AbstractIntegrationTest;
import com.secondhand.it.support.CompletedOrderFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用例 6：售后处理（申请 + 审批 + 仲裁）
 *
 * 覆盖流程：
 * - 主成功：退货退款全链路：申请(REQUESTED)→卖家同意(APPROVED)→买家寄回(RETURN_SHIPPED)
 *           →卖家确认收货(REFUNDED)，订单随全额退款作废
 * - 备选  ：仅退款直接退款完结；卖家拒绝→买家申请平台介入→管理员仲裁全额退款；
 *           仲裁驳回→售后关闭且订单恢复
 * - 异常  ：非买家申请（403）、确认收货前申请（403）、重复申请（409）、
 *           非卖家审批（403）、完结后审批（409）、未进入仲裁状态仲裁（404/409）、订单不存在（404）、
 *           非管理员仲裁（403，已修复：补充 ADMIN 角色校验）
 *
 * 验证层次：AfterSaleController → AfterSaleService（联动 OrderRepository）双模块落库
 */
@DisplayName("用例6：售后处理")
class Uc6AfterSaleIT extends AbstractIntegrationTest {

    /** 对已完成订单发起退货退款申请，返回售后单ID */
    private long requestReturnRefund(CompletedOrderFixture f) throws Exception {
        return data(doPost("/api/after-sale", f.buyer().token(), """
                {"orderId":%d,"type":"RETURN_REFUND","reason":"商品与描述不符",
                 "buyerEvidence":"开箱照片"}
                """.formatted(f.orderId()))).path("id").asLong();
    }

    @Nested
    @DisplayName("主成功流程")
    class MainFlow {

        @Test
        @DisplayName("退货退款全链路：申请→同意→寄回→确认收货→REFUNDED，订单作废")
        void returnRefundFullChain() throws Exception {
            CompletedOrderFixture f = completedOrder();

            // 1. 买家发起退货退款申请
            long requestId = data(doPost("/api/after-sale", f.buyer().token(), """
                    {"orderId":%d,"type":"RETURN_REFUND","reason":"商品有划痕",
                     "refundAmountCent":null,"buyerEvidence":"细节照片3张"}
                    """.formatted(f.orderId())))
                    .path("id").asLong();

            // 售后单落库，默认全额退款
            var afterSale = afterSaleRepo.findById(requestId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("REQUESTED", afterSale.getStatus().name());
            org.junit.jupiter.api.Assertions.assertEquals(88000, afterSale.getRefundAmountCent());
            // 订单被标记为售后中
            org.junit.jupiter.api.Assertions.assertEquals("AFTER_SALE",
                    orderRepo.findById(f.orderId()).orElseThrow().getStatus().name());

            // 2. 卖家同意（退货退款 → 待买家寄件）
            doPost("/api/after-sale/%d/approve".formatted(requestId), f.seller().token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"));

            // 3. 买家寄回退货
            doPost("/api/after-sale/%d/return-ship".formatted(requestId), f.buyer().token(), """
                    {"carrierCode":"ZTO","trackingNo":"ZTO9876543210"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RETURN_SHIPPED"));

            var shipped = afterSaleRepo.findById(requestId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("ZTO", shipped.getReturnCarrierCode());
            org.junit.jupiter.api.Assertions.assertEquals("ZTO9876543210", shipped.getReturnTrackingNo());

            // 4. 卖家确认收到退货 → 退款完结
            doPost("/api/after-sale/%d/confirm-return".formatted(requestId), f.seller().token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REFUNDED"));

            // 数据库断言：售后 REFUNDED，全额退款订单作废
            org.junit.jupiter.api.Assertions.assertEquals("REFUNDED",
                    afterSaleRepo.findById(requestId).orElseThrow().getStatus().name());
            var order = orderRepo.findById(f.orderId()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("CANCELLED", order.getStatus().name());
            org.junit.jupiter.api.Assertions.assertNotNull(
                    afterSaleRepo.findById(requestId).orElseThrow().getRefundedAt());
        }
    }

    @Nested
    @DisplayName("备选流程")
    class AlternateFlow {

        @Test
        @DisplayName("仅退款：卖家同意后直接REFUNDED（无需退货）")
        void refundOnlyApprovedDirectly() throws Exception {
            CompletedOrderFixture f = completedOrder();

            long requestId = data(doPost("/api/after-sale", f.buyer().token(), """
                    {"orderId":%d,"type":"REFUND_RECEIVED","reason":"已收货但配件缺失",
                     "refundAmountCent":5000,"buyerEvidence":"配件对比图"}
                    """.formatted(f.orderId()))).path("id").asLong();

            doPost("/api/after-sale/%d/approve".formatted(requestId), f.seller().token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REFUNDED"));

            var afterSale = afterSaleRepo.findById(requestId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("REFUNDED", afterSale.getStatus().name());
            // 部分退款（5000 < 88000）→ 订单保留 COMPLETED 而非作废
            org.junit.jupiter.api.Assertions.assertEquals("COMPLETED",
                    orderRepo.findById(f.orderId()).orElseThrow().getStatus().name());
        }

        @Test
        @DisplayName("平台介入仲裁：卖家拒绝→买家申请介入→管理员仲裁全额退款")
        void escalateAndArbitrateFullRefund() throws Exception {
            CompletedOrderFixture f = completedOrder();
            long requestId = requestReturnRefund(f);

            // 卖家拒绝
            doPost("/api/after-sale/%d/reject".formatted(requestId), f.seller().token(), """
                    {"note":"商品发出时无划痕"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));

            // 买家申请平台介入
            doPost("/api/after-sale/%d/escalate".formatted(requestId), f.buyer().token(), """
                    {"evidence":"补充物流开箱视频"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PLATFORM_ARBITRATION"));

            // 管理员仲裁：全额退款，责任方卖家
            doPost("/api/after-sale/%d/arbitrate".formatted(requestId), adminToken(), """
                    {"result":"FULL_REFUND","note":"卖家责任，全额退款"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REFUNDED"));

            var afterSale = afterSaleRepo.findById(requestId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("REFUNDED", afterSale.getStatus().name());
            org.junit.jupiter.api.Assertions.assertTrue(
                    afterSale.getArbitrationResult().contains("全额退款"));
            // 全额退款 → 订单作废
            org.junit.jupiter.api.Assertions.assertEquals("CANCELLED",
                    orderRepo.findById(f.orderId()).orElseThrow().getStatus().name());
        }

        @Test
        @DisplayName("仲裁驳回：售后CLOSED且订单恢复COMPLETED")
        void arbitrateDismissClosesAndRestoresOrder() throws Exception {
            CompletedOrderFixture f = completedOrder();
            long requestId = requestReturnRefund(f);

            doPost("/api/after-sale/%d/reject".formatted(requestId), f.seller().token(), null)
                    .andExpect(status().isOk());
            doPost("/api/after-sale/%d/escalate".formatted(requestId), f.buyer().token(), null)
                    .andExpect(status().isOk());

            // 管理员仲裁驳回
            doPost("/api/after-sale/%d/arbitrate".formatted(requestId), adminToken(), """
                    {"result":"DISMISS","note":"买家证据不足"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));

            org.junit.jupiter.api.Assertions.assertEquals("CLOSED",
                    afterSaleRepo.findById(requestId).orElseThrow().getStatus().name());
            // 订单恢复到售后前状态（已完成）
            org.junit.jupiter.api.Assertions.assertEquals("COMPLETED",
                    orderRepo.findById(f.orderId()).orElseThrow().getStatus().name());
        }

        @Test
        @DisplayName("买家取消售后：售后CLOSED且订单恢复")
        void buyerCancelsAfterSale() throws Exception {
            CompletedOrderFixture f = completedOrder();
            long requestId = requestReturnRefund(f);

            doPost("/api/after-sale/%d/cancel".formatted(requestId), f.buyer().token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));

            org.junit.jupiter.api.Assertions.assertEquals("COMPLETED",
                    orderRepo.findById(f.orderId()).orElseThrow().getStatus().name());
        }
    }

    @Nested
    @DisplayName("异常流程")
    class ExceptionFlow {

        @Test
        @DisplayName("非买家申请售后：返回403 FORBIDDEN")
        void requestByNonBuyerForbidden() throws Exception {
            CompletedOrderFixture f = completedOrder();

            doPost("/api/after-sale", f.seller().token(), """
                    {"orderId":%d,"type":"RETURN_REFUND","reason":"卖家不能申请售后"}
                    """.formatted(f.orderId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("确认收货前申请售后：返回403（收货前由超时自动触发）")
        void requestBeforeConfirmReceiptForbidden() throws Exception {
            var seller = registerUser();
            var buyer = registerUser();
            long productId = createProduct(seller.token(), "未收货售后商品", 10000);
            long orderId = placeOrder(buyer.token(), productId);
            payOrder(buyer.token(), orderId);
            shipOrder(seller.token(), orderId); // WAIT_RECEIVE：已发货未确认收货

            doPost("/api/after-sale", buyer.token(), """
                    {"orderId":%d,"type":"RETURN_REFUND","reason":"还没收货就想退"}
                    """.formatted(orderId))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

            // 未产生售后单
            org.junit.jupiter.api.Assertions.assertTrue(
                    afterSaleRepo.findAll().stream().noneMatch(a -> a.getOrderId() == orderId));
        }

        @Test
        @DisplayName("重复申请：已有进行中售后时再次申请返回409")
        void duplicateActiveRequestConflict() throws Exception {
            CompletedOrderFixture f = completedOrder();
            requestReturnRefund(f);

            doPost("/api/after-sale", f.buyer().token(), """
                    {"orderId":%d,"type":"RETURN_REFUND","reason":"再次申请"}
                    """.formatted(f.orderId()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("非卖家审批售后：返回403 FORBIDDEN")
        void approveByNonSellerForbidden() throws Exception {
            CompletedOrderFixture f = completedOrder();
            long requestId = requestReturnRefund(f);

            doPost("/api/after-sale/%d/approve".formatted(requestId), f.buyer().token(), null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

            org.junit.jupiter.api.Assertions.assertEquals("REQUESTED",
                    afterSaleRepo.findById(requestId).orElseThrow().getStatus().name());
        }

        @Test
        @DisplayName("完结后审批：对已关闭售后操作返回409")
        void approveClosedAfterSaleConflict() throws Exception {
            CompletedOrderFixture f = completedOrder();
            long requestId = requestReturnRefund(f);

            // 买家先取消 → CLOSED
            doPost("/api/after-sale/%d/cancel".formatted(requestId), f.buyer().token(), null)
                    .andExpect(status().isOk());

            // 卖家再审批 → 409
            doPost("/api/after-sale/%d/approve".formatted(requestId), f.seller().token(), null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("未进入仲裁状态直接仲裁：返回409 CONFLICT")
        void arbitrateNonArbitrationConflict() throws Exception {
            CompletedOrderFixture f = completedOrder();
            long requestId = requestReturnRefund(f); // REQUESTED 状态，未拒绝未介入

            doPost("/api/after-sale/%d/arbitrate".formatted(requestId), adminToken(), """
                    {"result":"FULL_REFUND","note":"越级仲裁"}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("非管理员仲裁：普通用户调用仲裁接口返回403（已修复：越权风险）")
        void arbitrateByNonAdminForbidden() throws Exception {
            // 与备选流程仲裁测试相同的前置链路：申请→拒绝→平台介入→PLATFORM_ARBITRATION
            CompletedOrderFixture f = completedOrder();
            long requestId = requestReturnRefund(f);

            doPost("/api/after-sale/%d/reject".formatted(requestId), f.seller().token(), null)
                    .andExpect(status().isOk());
            doPost("/api/after-sale/%d/escalate".formatted(requestId), f.buyer().token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PLATFORM_ARBITRATION"));

            // 普通用户（买家）携带有效 token 调用仲裁 → 403 FORBIDDEN
            // （修复前无 ADMIN 角色校验，任何登录用户都能裁决售后单）
            doPost("/api/after-sale/%d/arbitrate".formatted(requestId), f.buyer().token(), """
                    {"result":"FULL_REFUND","note":"普通用户越权仲裁尝试"}
                    """)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

            // 售后单仍停留在平台仲裁状态，未被普通用户裁决
            org.junit.jupiter.api.Assertions.assertEquals("PLATFORM_ARBITRATION",
                    afterSaleRepo.findById(requestId).orElseThrow().getStatus().name());
        }

        @Test
        @DisplayName("订单不存在申请售后：返回404 NOT_FOUND")
        void requestForNonexistentOrder404() throws Exception {
            var buyer = registerUser();
            doPost("/api/after-sale", buyer.token(), """
                    {"orderId":999999999,"type":"RETURN_REFUND","reason":"幽灵订单"}
                    """)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }
}
