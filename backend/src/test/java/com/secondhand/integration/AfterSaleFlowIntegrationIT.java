package com.secondhand.integration;

import com.secondhand.aftersale.entity.AfterSaleRequest;
import com.secondhand.aftersale.entity.AfterSaleStatus;
import com.secondhand.aftersale.entity.AfterSaleType;
import com.secondhand.aftersale.repository.AfterSaleRepository;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试 · 用例 6：售后处理（申请 + 审批 + 退货 + 仲裁）
 *
 * 覆盖范围：
 * - 模块间调用：AfterSaleController → AfterSaleService → OrderRepository → AfterSaleRepository
 *              → AdminAfterSaleController → AfterSaleService.adminArbitrate
 * - 数据库访问：after_sale_requests、orders 两张表
 * - 对外接口：
 *   - POST /api/after-sale                  买家发起售后
 *   - POST /api/after-sale/{id}/approve    卖家同意
 *   - POST /api/after-sale/{id}/reject     卖家拒绝
 *   - POST /api/after-sale/{id}/return-ship 买家寄回退货
 *   - POST /api/after-sale/{id}/confirm-return 卖家确认收到退货
 *   - POST /api/after-sale/{id}/reject-return  卖家拒绝退货
 *   - POST /api/after-sale/{id}/seller-evidence
 *   - POST /api/after-sale/{id}/buyer-evidence
 *   - POST /api/after-sale/{id}/escalate   买家申请平台介入
 *   - POST /api/after-sale/{id}/cancel     买家取消
 *   - POST /api/admin/after-sale/{id}/arbitrate 管理员仲裁
 *   - GET  /api/after-sale/my-requests、my-received、by-order
 *
 * 售后状态机：
 *   REQUESTED → APPROVED（同意退货）→ RETURN_SHIPPED → REFUNDED
 *   REQUESTED → REJECTED → PLATFORM_ARBITRATION → REFUNDED | CLOSED
 *   任何未完结状态 → CLOSED（买家取消或超时）
 *
 * 用例流程覆盖：
 * - 主成功流程 1：仅退款（未发货 REFUND_NOT_SHIPPED）→ 卖家同意 → REFUNDED
 * - 主成功流程 2：退货退款（RETURN_REFUND）→ 同意 → 寄回 → 确认收货 → REFUNDED
 * - 备选流程 1：卖家拒绝 → 买家申诉 → 平台仲裁 FULL_REFUND
 * - 备选流程 2：买家取消售后
 * - 异常流程 1：未确认收货发起售后返回 403
 * - 异常流程 2：超时窗口期外发起售后返回 410
 * - 异常流程 3：重复发起售后返回 409
 * - 异常流程 4：非买家/卖家越权操作返回 403
 * - 异常流程 5：状态错误（如 RETURN_SHIPPED 时再次 approve）返回 409
 */
@Testcontainers
@DisplayName("用例6：售后处理")
class AfterSaleFlowIntegrationIT extends AbstractIntegrationIT {

    @Autowired AfterSaleRepository afterSaleRepo;
    @Autowired OrderRepository orderRepo;

    private static final String RECEIVER = "x";
    private static final String RECEIVER_PHONE = "13900000000";
    private static final String RECEIVER_ADDR = "x";

    /** 走完 下单→支付→发货→确认收货 的完整前置流程，返回 COMPLETED 订单 ID */
    private long prepareCompletedOrder(TestUser seller, TestUser buyer, long productId) throws Exception {
        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of("productId", productId,
                                "receiverName", RECEIVER,
                                "receiverPhone", RECEIVER_PHONE,
                                "receiverAddress", RECEIVER_ADDR)))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                .andExpect(status().isOk());

        mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                        Map.of("carrierCode", "SF", "trackingNo", "SF" + orderId)))
                .andExpect(status().isOk());

        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                .andExpect(status().isOk());

        return orderId;
    }

    // ==================== 主成功流程 1：仅退款 ====================

    @Test
    @DisplayName("主成功1 · 仅退款(未发货场景：发起→卖家同意→REFUNDED→订单CANCELLED)")
    void shouldRefundNotShippedHappyPath() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "仅退款商品", 50000, 1);

        long orderId = prepareCompletedOrder(seller, buyer, pid);

        // 1. 买家发起售后：REFUND_RECEIVED 类型
        long afterSaleId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                        Map.of(
                                "orderId", orderId,
                                "type", "REFUND_RECEIVED",
                                "reason", "商品与描述不符",
                                "buyerEvidence", "图片证据")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.buyerId").value(buyer.userId()))
                .andExpect(jsonPath("$.data.sellerId").value(seller.userId()))
                .andExpect(jsonPath("$.data.requestType").value("REFUND_RECEIVED"))
                .andReturn());

        // 2. 验证订单状态被标记为 AFTER_SALE
        mockMvc.perform(authGet(buyer.token(), "/api/orders/" + orderId))
                .andExpect(jsonPath("$.data.order.status").value("AFTER_SALE"));

        // 3. 卖家同意 → 直接 REFUNDED（无需退货）
        mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + afterSaleId + "/approve", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundedAt", notNullValue()));

        // 4. 验证订单变为 CANCELLED（全额退款，订单作废）
        mockMvc.perform(authGet(buyer.token(), "/api/orders/" + orderId))
                .andExpect(jsonPath("$.data.order.status").value("CANCELLED"));
    }

    // ==================== 主成功流程 2：退货退款 ====================

    @Test
    @DisplayName("主成功2 · 退货退款(发起→同意→寄回→确认收货→REFUNDED)")
    void shouldReturnRefundHappyPath() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "退货退款商品", 80000, 1);

        long orderId = prepareCompletedOrder(seller, buyer, pid);

        // 1. 买家发起退货退款
        long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                        Map.of(
                                "orderId", orderId,
                                "type", "RETURN_REFUND",
                                "reason", "商品质量问题",
                                "buyerEvidence", "图片+视频")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andReturn());

        // 2. 卖家同意 → APPROVED（等待买家寄件）
        mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/approve", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.deadlineAt", notNullValue())); // 7 天寄件时效

        // 3. 买家寄回退货
        mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/return-ship",
                        Map.of("carrierCode", "YTO", "trackingNo", "YTO" + asId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURN_SHIPPED"))
                .andExpect(jsonPath("$.data.returnCarrierCode").value("YTO"))
                .andExpect(jsonPath("$.data.returnTrackingNo", startsWith("YTO")));

        // 4. 卖家确认收到退货 → REFUNDED
        mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/confirm-return", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundedAt", notNullValue()));

        // 5. 验证订单变为 CANCELLED（全额退款）
        mockMvc.perform(authGet(buyer.token(), "/api/orders/" + orderId))
                .andExpect(jsonPath("$.data.order.status").value("CANCELLED"));
    }

    // ==================== 备选流程 ====================

    @Nested
    @DisplayName("备选流程")
    class AlternativeFlows {

        @Test
        @DisplayName("备选 · 卖家拒绝 → 买家申诉 → 平台仲裁 FULL_REFUND")
        void shouldSellerRejectAndArbitrateFullRefund() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            TestUser admin = createTestUser(com.secondhand.auth.entity.Role.ADMIN);
            long pid = createProduct(seller.userId(), "仲裁商品", 60000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "RETURN_REFUND",
                                    "reason", "质量", "buyerEvidence", "ev1")))
                    .andExpect(status().isOk()).andReturn());

            // 卖家拒绝
            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/reject",
                            Map.of("note", "买家使用不当")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"))
                    .andExpect(jsonPath("$.data.sellerResponse").value("买家使用不当"));

            // 买家申诉
            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/escalate",
                            Map.of("evidence", "补充证据")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PLATFORM_ARBITRATION"));

            // 平台仲裁：全额退款，责任方卖家
            mockMvc.perform(authPost(admin.token(), "/api/admin/after-sale/" + asId + "/arbitrate",
                            Map.of(
                                    "result", "FULL_REFUND",
                                    "responsibility", "SELLER",
                                    "shippingPaidBy", "SELLER",
                                    "shippingCostCent", 1000,
                                    "note", "卖家应承担退货运费")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                    .andExpect(jsonPath("$.data.responsibility").value("SELLER"))
                    .andExpect(jsonPath("$.data.shippingPaidBy").value("SELLER"))
                    .andExpect(jsonPath("$.data.shippingCostCent").value(1000))
                    .andExpect(jsonPath("$.data.arbitrationResult", containsString("全额退款")));
        }

        @Test
        @DisplayName("备选 · 平台仲裁 PARTIAL_REFUND（订单保留 COMPLETED）")
        void shouldArbitratePartialRefund() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            TestUser admin = createTestUser(com.secondhand.auth.entity.Role.ADMIN);
            long pid = createProduct(seller.userId(), "部分退款", 50000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "PARTIAL_REFUND",
                                    "reason", "外观有轻微划痕",
                                    "refundAmountCent", 15000))) // 部分退款 150 元
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.refundAmountCent").value(15000))
                    .andReturn());

            // 卖家拒绝
            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/reject",
                            Map.of("note", "不同意")))
                    .andExpect(status().isOk());

            // 买家申诉
            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/escalate",
                            Map.of("evidence", "ev")))
                    .andExpect(status().isOk());

            // 平台仲裁：部分退款
            mockMvc.perform(authPost(admin.token(), "/api/admin/after-sale/" + asId + "/arbitrate",
                            Map.of(
                                    "result", "PARTIAL_REFUND",
                                    "responsibility", "SELLER",
                                    "shippingPaidBy", "PLATFORM",
                                    "shippingCostCent", 0,
                                    "partialRefundCent", 20000, // 平台裁定 200 元
                                    "note", "折价补偿")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                    .andExpect(jsonPath("$.data.refundAmountCent").value(20000));

            // 部分退款：订单保留为 COMPLETED（买家留用商品）
            mockMvc.perform(authGet(buyer.token(), "/api/orders/" + orderId))
                    .andExpect(jsonPath("$.data.order.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("备选 · 平台仲裁 DISMISS（驳回售后，订单恢复 COMPLETED）")
        void shouldArbitrateDismiss() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            TestUser admin = createTestUser(com.secondhand.auth.entity.Role.ADMIN);
            long pid = createProduct(seller.userId(), "驳回售后", 40000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED",
                                    "reason", "想退")))
                    .andExpect(status().isOk()).andReturn());

            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/reject",
                            Map.of("note", "不同意")))
                    .andExpect(status().isOk());

            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/escalate",
                            Map.of("evidence", "ev")))
                    .andExpect(status().isOk());

            // 仲裁驳回
            mockMvc.perform(authPost(admin.token(), "/api/admin/after-sale/" + asId + "/arbitrate",
                            Map.of("result", "DISMISS", "note", "买家证据不足")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"))
                    .andExpect(jsonPath("$.data.arbitrationResult", containsString("驳回")));

            // 订单恢复为 COMPLETED
            mockMvc.perform(authGet(buyer.token(), "/api/orders/" + orderId))
                    .andExpect(jsonPath("$.data.order.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("备选 · 买家主动取消售后")
        void shouldBuyerCancelAfterSale() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "取消售后", 30000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED",
                                    "reason", "想退")))
                    .andExpect(status().isOk()).andReturn());

            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/cancel", Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));

            // 订单应恢复为 COMPLETED
            mockMvc.perform(authGet(buyer.token(), "/api/orders/" + orderId))
                    .andExpect(jsonPath("$.data.order.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("备选 · 卖家拒绝退货（RETURN_SHIPPED → REJECTED）→ 买家可申诉")
        void shouldSellerRejectReturn() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "拒退货", 50000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "RETURN_REFUND", "reason", "x")))
                    .andExpect(status().isOk()).andReturn());

            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/approve", Map.of()))
                    .andExpect(status().isOk());

            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/return-ship",
                            Map.of("carrierCode", "ZTO", "trackingNo", "Z" + asId)))
                    .andExpect(status().isOk());

            // 卖家拒绝收货
            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/reject-return",
                            Map.of("note", "退货损坏")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"))
                    .andExpect(jsonPath("$.data.sellerResponse").value("退货损坏"));

            // 买家可继续申诉到平台
            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/escalate",
                            Map.of("evidence", "二次证据")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PLATFORM_ARBITRATION"));
        }

        @Test
        @DisplayName("备选 · 卖家上传举证材料，买家补充举证")
        void shouldUploadEvidence() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "举证", 50000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "RETURN_REFUND",
                                    "reason", "x", "buyerEvidence", "原始证据")))
                    .andExpect(status().isOk()).andReturn());

            // 卖家上传举证
            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/seller-evidence",
                            Map.of("evidence", "卖家发货视频")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sellerEvidence").value("卖家发货视频"));

            // 买家补充举证（追加到原证据后）
            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/buyer-evidence",
                            Map.of("evidence", "收到商品损坏照片")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.buyerEvidence", containsString("原始证据")))
                    .andExpect(jsonPath("$.data.buyerEvidence", containsString("收到商品损坏照片")));
        }

        @Test
        @DisplayName("备选 · 售后列表查询：买家发起的、卖家收到的、按订单查")
        void shouldListAfterSaleRecords() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "列表查询", 40000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED", "reason", "x")))
                    .andExpect(status().isOk()).andReturn());

            // 买家发起的售后列表
            mockMvc.perform(authGet(buyer.token(), "/api/after-sale/my-requests"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

            // 卖家收到的售后列表
            mockMvc.perform(authGet(seller.token(), "/api/after-sale/my-received"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

            // 按订单查售后
            mockMvc.perform(authGet(buyer.token(), "/api/after-sale/by-order/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id").value(asId));
        }
    }

    // ==================== 异常流程 ====================

    @Nested
    @DisplayName("异常流程")
    class ExceptionFlows {

        @Test
        @DisplayName("异常 · 未确认收货就发起售后返回 403")
        void shouldRejectBeforeCompleted() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "未完成订单", 40000, 1);

            // 只走到下单+支付，未确认收货
            long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                            Map.of("productId", pid,
                                    "receiverName", RECEIVER,
                                    "receiverPhone", RECEIVER_PHONE,
                                    "receiverAddress", RECEIVER_ADDR)))
                    .andExpect(status().isOk()).andReturn());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                    .andExpect(status().isOk());

            mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED", "reason", "x")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("异常 · 超过7天售后窗口期发起售后返回 410")
        void shouldRejectAfterWindow() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "超期售后", 40000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            // 直接 DB 修改订单的 completedAt 为 8 天前
            Order order = orderRepo.findById(orderId).orElseThrow();
            order.setCompletedAt(java.time.LocalDateTime.now().minusDays(8));
            orderRepo.save(order);

            mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED", "reason", "x")))
                    .andExpect(status().isGone()) // 410
                    .andExpect(jsonPath("$.error.code").value("CLOSED"));
        }

        @Test
        @DisplayName("异常 · 重复发起售后返回 409")
        void shouldRejectDuplicateRequest() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "重复售后", 40000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            // 第一次发起成功
            mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "RETURN_REFUND", "reason", "x")))
                    .andExpect(status().isOk());

            // 第二次发起应失败
            mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED", "reason", "y")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("异常 · 非买家对他人订单发起售后返回 403")
        void shouldRejectNonBuyerRequest() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            TestUser other = createTestUser();
            long pid = createProduct(seller.userId(), "他人订单", 40000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            mockMvc.perform(authPost(other.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED", "reason", "x")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("异常 · 卖家越权操作买家发起的售后（approve 时检查 sellerId）返回 403")
        void shouldRejectSellerNotOwner() throws Exception {
            TestUser seller1 = createTestUser();
            TestUser seller2 = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller1.userId(), "越权卖家", 40000, 1);
            long orderId = prepareCompletedOrder(seller1, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED", "reason", "x")))
                    .andExpect(status().isOk()).andReturn());

            // seller2 不是该售后的 sellerId
            mockMvc.perform(authPost(seller2.token(), "/api/after-sale/" + asId + "/approve", Map.of()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("异常 · 在 REQUESTED 之外的状态再次 approve 返回 409")
        void shouldRejectApproveInWrongState() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "状态错误", 40000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "RETURN_REFUND", "reason", "x")))
                    .andExpect(status().isOk()).andReturn());

            // 卖家同意
            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/approve", Map.of()))
                    .andExpect(status().isOk());

            // 再次 approve（状态已是 APPROVED）应失败
            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/approve", Map.of()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("异常 · 在 APPROVED 状态下寄回（非 RETURN_REFUND 类型）返回 409")
        void shouldRejectReturnShipForNonReturnRefund() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "非退货类型", 40000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED", "reason", "x")))
                    .andExpect(status().isOk()).andReturn());

            // 卖家同意（REFUND_RECEIVED 类型无需退货，直接 REFUNDED）
            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/approve", Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REFUNDED")); // 已 REFUNDED

            // 此时再调用 return-ship 状态不允许 → 应失败
            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/return-ship",
                            Map.of("carrierCode", "SF", "trackingNo", "x")))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("异常 · 售后完结后无法上传证据返回 409")
        void shouldRejectEvidenceOnClosed() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "完结证据", 40000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED", "reason", "x")))
                    .andExpect(status().isOk()).andReturn());

            // 卖家同意 → REFUNDED
            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/approve", Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REFUNDED"));

            // 此时上传证据应失败
            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/seller-evidence",
                            Map.of("evidence", "x")))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("异常 · 非管理员仲裁返回 403")
        void shouldRejectArbitrateByNonAdmin() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "非管理员仲裁", 40000, 1);
            long orderId = prepareCompletedOrder(seller, buyer, pid);

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "RETURN_REFUND", "reason", "x")))
                    .andExpect(status().isOk()).andReturn());

            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/reject",
                            Map.of("note", "x")))
                    .andExpect(status().isOk());

            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/escalate",
                            Map.of("evidence", "x")))
                    .andExpect(status().isOk());

            // 普通用户调用 /api/admin/after-sale/{id}/arbitrate 应返回 403
            mockMvc.perform(authPost(buyer.token(), "/api/admin/after-sale/" + asId + "/arbitrate",
                            Map.of("result", "FULL_REFUND", "note", "x")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("异常 · 售后不存在返回 404")
        void shouldRejectAfterSaleNotFound() throws Exception {
            TestUser buyer = createTestUser();
            mockMvc.perform(authGet(buyer.token(), "/api/after-sale/99999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }
}
