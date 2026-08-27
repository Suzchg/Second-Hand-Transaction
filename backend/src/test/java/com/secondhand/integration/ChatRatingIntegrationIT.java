package com.secondhand.integration;

import com.secondhand.rating.repository.RatingRepository;
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
 * 集成测试 · 私聊 + 评分
 *
 * 覆盖范围：
 * - 模块间调用：ChatMessageController → ChatMessageService → ProductService（校验商品存在）
 *              MessageCenterController → ChatMessageService.getConversationList
 *              RatingController → RatingService → OrderRepository → RatingRepository
 * - 数据库访问：chat_messages、ratings 两张表
 * - 对外接口：
 *   - POST   /api/products/{productId}/chat     （发送消息）
 *   - GET    /api/products/{productId}/chat     （获取对话，需带 ?with=）
 *   - GET    /api/users/messages                （消息中心：对话摘要）
 *   - PUT    /api/messages/read                 （标记已读）
 *   - POST   /api/orders/{orderId}/rate         （买家评分）
 *   - GET    /api/orders/{orderId}/rating       （查看订单评分）
 *   - GET    /api/users/{userId}/rating         （卖家评分公开统计）
 *
 * 用例流程覆盖：
 * - 主成功流程：买家私聊卖家 → 卖家回复 → 标记已读 → 消息中心看到对话
 * - 主成功流程：完成订单后买家评分 → 卖家评分统计更新
 * - 备选流程：消息中心按商品聚合、双向评分查询
 * - 异常流程：给自己发消息、订单未完成就评分、重复评分、非买家评分、评分越界
 */
@Testcontainers
@DisplayName("私聊 + 评分")
class ChatRatingIntegrationIT extends AbstractIntegrationIT {

    @Autowired RatingRepository ratingRepo;

    // ==================== 私聊子用例 ====================

    @Nested
    @DisplayName("私聊")
    class ChatFlow {

        @Test
        @DisplayName("主成功 · 买家→卖家→买家 双向对话 + 消息中心 + 已读")
        void shouldHaveBidirectionalConversation() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "私聊商品", 5000, 1);

            // 1. 买家向卖家发消息
            mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid + "/chat",
                            Map.of("receiverId", seller.userId(), "content", "你好，还在吗？")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.senderId").value(buyer.userId()))
                    .andExpect(jsonPath("$.data.receiverId").value(seller.userId()))
                    .andExpect(jsonPath("$.data.isRead").value(false))
                    .andExpect(jsonPath("$.data.content").value("你好，还在吗？"));

            // 2. 卖家回复买家
            mockMvc.perform(authPost(seller.token(), "/api/products/" + pid + "/chat",
                            Map.of("receiverId", buyer.userId(), "content", "在的，可以小刀")))
                    .andExpect(status().isOk());

            // 3. 买家查询与卖家的对话
            mockMvc.perform(authGet(buyer.token(), "/api/products/" + pid + "/chat")
                            .param("with", String.valueOf(seller.userId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)));

            // 4. 卖家查看消息中心
            mockMvc.perform(authGet(seller.token(), "/api/users/messages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

            // 5. 卖家标记已读
            mockMvc.perform(authPut(seller.token(), "/api/messages/read",
                            Map.of("productId", pid, "otherUserId", buyer.userId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 6. 验证已读后，对话中之前的消息 isRead 应为 true
            mockMvc.perform(authGet(buyer.token(), "/api/products/" + pid + "/chat")
                            .param("with", String.valueOf(seller.userId())))
                    .andExpect(jsonPath("$.data[0].isRead").value(true));
        }

        @Test
        @DisplayName("异常 · 给自己发消息返回 403")
        void shouldRejectMessageToSelf() throws Exception {
            TestUser user = createTestUser();
            long pid = createProduct(user.userId(), "自聊", 1000, 1);
            mockMvc.perform(authPost(user.token(), "/api/products/" + pid + "/chat",
                            Map.of("receiverId", user.userId(), "content", "x")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("异常 · 消息内容为空返回 400")
        void shouldRejectBlankMessage() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "空消息", 1000, 1);
            mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid + "/chat",
                            Map.of("receiverId", seller.userId(), "content", "")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("异常 · 缺少 receiverId 返回 400")
        void shouldRejectMissingReceiverId() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "缺收件人", 1000, 1);
            mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid + "/chat",
                            Map.of("content", "x")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("异常 · 未登录发消息返回 401")
        void shouldRejectMessageWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/products/1/chat")
                            .contentType("application/json")
                            .content(toJson(Map.of("receiverId", 1, "content", "x"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== 评分子用例 ====================

    @Nested
    @DisplayName("评分")
    class RatingFlow {

        /** 完成订单的前置流程 */
        private long prepareCompletedOrder(long sellerId, long buyerId, long productId) throws Exception {
            String sellerToken = jwtService.createAccessToken(sellerId, "USER");
            String buyerToken = jwtService.createAccessToken(buyerId, "USER");
            long orderId = extractId(mockMvc.perform(authPost(buyerToken, "/api/orders",
                            Map.of("productId", productId,
                                    "receiverName", "x",
                                    "receiverPhone", "x",
                                    "receiverAddress", "x")))
                    .andExpect(status().isOk()).andReturn());
            mockMvc.perform(authPost(buyerToken, "/api/orders/" + orderId + "/pay", Map.of()))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(sellerToken, "/api/orders/" + orderId + "/ship",
                            Map.of("carrierCode", "SF", "trackingNo", "SF" + orderId)))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(buyerToken, "/api/orders/" + orderId + "/confirm", Map.of()))
                    .andExpect(status().isOk());
            return orderId;
        }

        @Test
        @DisplayName("主成功 · 完成订单后买家评分 → 卖家评分统计")
        void shouldRateOrderAndComputeAverage() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "评分商品", 5000, 1);
            long orderId = prepareCompletedOrder(seller.userId(), buyer.userId(), pid);

            // 1. 买家评分（5 星好评）
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                            Map.of("score", 5, "comment", "卖家很实在，发货快，成色比描述还好")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.score").value(5))
                    .andExpect(jsonPath("$.data.reviewerId").value(buyer.userId()))
                    .andExpect(jsonPath("$.data.sellerId").value(seller.userId()))
                    .andExpect(jsonPath("$.data.productId").value(pid))
                    .andExpect(jsonPath("$.data.comment", containsString("卖家很实在")));

            // 2. 查看订单评分
            mockMvc.perform(authGet(buyer.token(), "/api/orders/" + orderId + "/rating"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.score").value(5))
                    .andExpect(jsonPath("$.data.comment", containsString("卖家很实在")));

            // 3. 查看卖家评分统计（公开端点）
            mockMvc.perform(get("/api/users/" + seller.userId() + "/rating"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.averageScore").value(5.0))
                    .andExpect(jsonPath("$.data.totalCount").value(1));
        }

        @Test
        @DisplayName("备选 · 多个买家评分同一卖家，平均值正确")
        void shouldAverageMultipleRatings() throws Exception {
            TestUser seller = createTestUser();
            // 两个不同买家分别评分 5 和 4，平均应为 4.5
            for (int score : new int[]{5, 4}) {
                TestUser buyer = createTestUser();
                long pid = createProduct(seller.userId(), "多评分" + score, 5000, 1);
                long orderId = prepareCompletedOrder(seller.userId(), buyer.userId(), pid);
                mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                                Map.of("score", score, "comment", "评分" + score)))
                        .andExpect(status().isOk());
            }

            // 验证平均分 4.5（round 到 1 位小数）
            mockMvc.perform(get("/api/users/" + seller.userId() + "/rating"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.averageScore").value(4.5))
                    .andExpect(jsonPath("$.data.totalCount").value(2));
        }

        @Test
        @DisplayName("备选 · 评分最小 1 分、最大 5 分边界")
        void shouldAcceptBoundaryScores() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "边界评分", 5000, 1);
            long orderId = prepareCompletedOrder(seller.userId(), buyer.userId(), pid);

            // 1 分也合法
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                            Map.of("score", 1, "comment", "差评")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.score").value(1));
        }

        @Test
        @DisplayName("异常 · 订单未完成时评分返回 409")
        void shouldRejectRateBeforeCompleted() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "未完成评分", 5000, 1);

            // 只走到下单+支付，未确认收货
            long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                            Map.of("productId", pid,
                                    "receiverName", "x",
                                    "receiverPhone", "x",
                                    "receiverAddress", "x")))
                    .andExpect(status().isOk()).andReturn());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                    .andExpect(status().isOk());

            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                            Map.of("score", 5, "comment", "x")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("异常 · 重复评分同一订单返回 409")
        void shouldRejectDuplicateRating() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "重复评分", 5000, 1);
            long orderId = prepareCompletedOrder(seller.userId(), buyer.userId(), pid);

            // 第一次评分成功
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                            Map.of("score", 5, "comment", "第一次")))
                    .andExpect(status().isOk());

            // 第二次评分应失败
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                            Map.of("score", 3, "comment", "第二次")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("异常 · 非买家评分返回 403")
        void shouldRejectRatingByNonBuyer() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            TestUser other = createTestUser();
            long pid = createProduct(seller.userId(), "非买家评分", 5000, 1);
            long orderId = prepareCompletedOrder(seller.userId(), buyer.userId(), pid);

            mockMvc.perform(authPost(other.token(), "/api/orders/" + orderId + "/rate",
                            Map.of("score", 5, "comment", "x")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("异常 · 评分越界（>5 或 <1）返回 400")
        void shouldRejectScoreOutOfRange() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "越界评分", 5000, 1);
            long orderId = prepareCompletedOrder(seller.userId(), buyer.userId(), pid);

            // 6 分
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                            Map.of("score", 6, "comment", "x")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            // 0 分（虽然 VALIDATION_ERROR 在 GlobalExceptionHandler 触发，但 RatingService 也校验）
            // 注意：Min(1) 注解会在 Controller 层就触发校验
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                            Map.of("score", 0, "comment", "x")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("异常 · 未登录访问订单评分返回 401")
        void shouldRejectGetRatingWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/orders/1/rating"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
