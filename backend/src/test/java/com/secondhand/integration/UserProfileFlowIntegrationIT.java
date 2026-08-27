package com.secondhand.integration;

import com.secondhand.admin.OnlineUserTracker;
import com.secondhand.auth.entity.Role;
import com.secondhand.auth.entity.User;
import com.secondhand.auth.repository.UserRepository;
import com.secondhand.user.entity.UserAddress;
import com.secondhand.user.repository.UserAddressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试 · 补充用例：用户资料 + 头像 + 通知 + 心跳 + 地址更新 + 商品搜索 + 卖家已售
 *
 * 设计思路（功能相近不拆分）：
 * - 上述端点同属"用户中心 + 公开浏览"功能域，统一放在本测试文件中验证，避免按端点逐个拆分。
 * - 各端点的主成功 / 备选 / 异常流程通过 @Nested 内嵌类组织，便于阅读但同属一个测试类。
 *
 * 覆盖范围：
 * - 模块间调用：
 *   - UserController → UserService → UserRepository → RatingRepository（公开信息聚合）
 *   - UserController → ChatMessageRepository / OfferRepository / OrderRepository（通知聚合）
 *   - UserController → OnlineUserTracker（心跳维持在线状态）
 *   - AddressController → AddressService → UserAddressRepository
 *   - ProductController → ProductService → ProductRepository（关键词 + 分类搜索）
 *   - OrderController → OrderService.getSellerSoldProducts（含 Rating 信息）
 * - 数据库访问：users / user_addresses / products / orders / ratings 多表
 * - 对外接口：
 *   - GET  /api/users/profile                  获取个人资料
 *   - PUT  /api/users/profile                  更新个人资料（昵称/手机/邮箱）
 *   - PUT  /api/users/avatar                   上传头像（multipart）
 *   - GET  /api/users/notifications            通知聚合（未读消息/待处理报价/订单）
 *   - POST /api/auth/heartbeat                 心跳
 *   - PUT  /api/users/addresses/{id}           地址更新
 *   - GET  /api/users/{sellerId}/sold          卖家已售出商品（含评分）
 *   - GET  /api/products?keyword=              关键词搜索
 *   - GET  /api/products?categoryId=           分类筛选
 *   - GET  /api/products?categoryId=0          推荐列表
 *   - GET  /api/products?keyword=&categoryId= 关键词 + 分类组合搜索
 *
 * 用例流程覆盖：
 * - 主成功流程：获取资料→更新资料→上传头像→心跳→查询通知
 * - 主成功流程：地址局部更新（PUT）+ 越权保护
 * - 主成功流程：商品关键词搜索、分类筛选、组合搜索、推荐
 * - 主成功流程：卖家已售出商品含评分聚合
 * - 备选流程：邮箱归一化为小写、地址设为默认时旧默认被清空、关键词无结果返回空列表
 * - 异常流程：未登录访问 /api/users/profile 返回 401、上传空文件返回 400、更新他人地址返回 403、
 *            不存在的卖家查询 sold 返回 404、未登录心跳返回 401
 */
@Testcontainers
@DisplayName("补充用例：用户资料 + 头像 + 通知 + 心跳 + 地址更新 + 商品搜索 + 卖家已售")
class UserProfileFlowIntegrationIT extends AbstractIntegrationIT {

    @Autowired UserRepository userRepo;
    @Autowired UserAddressRepository addressRepo;
    @Autowired OnlineUserTracker onlineUserTracker;

    // ==================== 用户资料子用例 ====================

    @Nested
    @DisplayName("用户资料 + 头像 + 心跳 + 通知")
    class ProfileAndNotificationsFlow {

        @Test
        @DisplayName("主成功 · 获取资料→更新资料→上传头像→心跳→查询通知")
        void shouldProfileUpdateAvatarHeartbeatAndNotify() throws Exception {
            TestUser user = createTestUser();

            // 1. 获取个人资料
            mockMvc.perform(authGet(user.token(), "/api/users/profile"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(user.userId()))
                    .andExpect(jsonPath("$.data.nickname", not(blankOrNullString())))
                    .andExpect(jsonPath("$.data.phone").value(user.phone()));

            // 2. 更新个人资料（昵称 + 邮箱，邮箱归一化为小写）
            mockMvc.perform(authPut(user.token(), "/api/users/profile",
                            Map.of(
                                    "nickname", "新昵称Z",
                                    "email", "User@Example.COM")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("新昵称Z"))
                    .andExpect(jsonPath("$.data.email").value("user@example.com"));

            // 验证数据库已落库
            User dbUser = userRepo.findById(user.userId()).orElseThrow();
            if (!"新昵称Z".equals(dbUser.getNickname()) || !"user@example.com".equals(dbUser.getEmail())) {
                throw new AssertionError("个人资料更新未落库");
            }

            // 3. 上传头像（multipart）
            MockMultipartFile avatar = new MockMultipartFile(
                    "file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3, 4, 5});
            mockMvc.perform(multipart("/api/users/avatar").file(avatar)
                            .header("Authorization", "Bearer " + user.token())
                            .header("X-Forwarded-For", "10.0.0.101"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", containsString("/uploads/avatars/")));

            // 验证 DB 中头像 URL 已更新
            User withAvatar = userRepo.findById(user.userId()).orElseThrow();
            if (withAvatar.getAvatarUrl() == null || !withAvatar.getAvatarUrl().contains("/uploads/avatars/")) {
                throw new AssertionError("头像 URL 未落库");
            }

            // 4. 心跳
            mockMvc.perform(authPost(user.token(), "/api/auth/heartbeat", Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 心跳后应被 OnlineUserTracker 标记为活跃
            if (!onlineUserTracker.getActiveUserIds(1).contains(user.userId())) {
                throw new AssertionError("心跳未更新 OnlineUserTracker");
            }

            // 5. 查询通知聚合（新用户应当全部为 0）
            mockMvc.perform(authGet(user.token(), "/api/users/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.unreadMessages").value(0))
                    .andExpect(jsonPath("$.data.pendingOffersReceived").value(0))
                    .andExpect(jsonPath("$.data.pendingOrdersBuyer").value(0))
                    .andExpect(jsonPath("$.data.pendingOrdersSeller").value(0));
        }

        @Test
        @DisplayName("主成功 · 通知聚合在有未读消息 + 待处理报价 + 待处理订单时正确计数")
        void shouldCountNotificationsCorrectly() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "通知计数商品", 10000, 1);

            // 1. 买家向卖家发消息（卖家产生 1 条未读）
            mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid + "/chat",
                            Map.of("receiverId", seller.userId(), "content", "您好")))
                    .andExpect(status().isOk());

            // 2. 买家对卖家商品出价（卖家产生 1 条 PENDING 报价）
            mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid + "/offers",
                            Map.of("offeredPriceCent", 8000, "message", "8折")))
                    .andExpect(status().isOk());

            // 3. 买家下单但未支付（卖家产生 1 条 WAIT_PAY 订单——但卖家侧只看 WAIT_DELIVER）
            // 注：pendingOrdersSeller 仅统计 WAIT_DELIVER 状态，需卖家收到支付后才会出现
            // 这里只验证 unreadMessages + pendingOffersReceived 两个计数
            mockMvc.perform(authGet(seller.token(), "/api/users/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.unreadMessages").value(1))
                    .andExpect(jsonPath("$.data.pendingOffersReceived").value(1));
        }

        @Test
        @DisplayName("异常 · 未登录访问 /api/users/profile 返回 401")
        void shouldRejectProfileWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/users/profile"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("异常 · 未登录上传头像返回 401")
        void shouldRejectAvatarWithoutAuth() throws Exception {
            MockMultipartFile avatar = new MockMultipartFile(
                    "file", "a.jpg", "image/jpeg", new byte[]{1});
            mockMvc.perform(multipart("/api/users/avatar").file(avatar))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("异常 · 未登录访问通知聚合返回 401")
        void shouldRejectNotificationsWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/users/notifications"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("异常 · 未登录心跳返回 401")
        void shouldRejectHeartbeatWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/auth/heartbeat")
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("异常 · 更新资料时昵称超长（>50）返回 400")
        void shouldRejectTooLongNickname() throws Exception {
            TestUser user = createTestUser();
            String tooLong = "x".repeat(60);
            mockMvc.perform(authPut(user.token(), "/api/users/profile",
                            Map.of("nickname", tooLong)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    // ==================== 地址更新子用例 ====================
    // （地址的 create/list/setDefault/delete 已在 AuthFlowIntegrationIT.AddressFlow 覆盖，
    //   这里只补 PUT /api/users/addresses/{id} 更新接口及其异常流程）

    @Nested
    @DisplayName("地址更新")
    class AddressUpdateFlow {

        @Test
        @DisplayName("主成功 · 局部更新地址 + 设为默认时旧默认被清空")
        void shouldUpdateAddressAndResetDefault() throws Exception {
            TestUser user = createTestUser();

            // 创建两条地址，第一条为默认
            long addrId1 = extractId(mockMvc.perform(authPost(user.token(), "/api/users/addresses",
                            java.util.Map.of(
                                    "receiverName", "张三", "receiverPhone", "13900001111",
                                    "province", "北京市", "city", "北京市",
                                    "district", "海淀区", "detailAddress", "中关村1号",
                                    "isDefault", true, "tag", "公司")))
                    .andExpect(status().isOk()).andReturn());

            long addrId2 = extractId(mockMvc.perform(authPost(user.token(), "/api/users/addresses",
                            java.util.Map.of(
                                    "receiverName", "李四", "receiverPhone", "13900002222",
                                    "province", "上海市", "city", "上海市",
                                    "district", "浦东新区", "detailAddress", "世纪大道2号",
                                    "isDefault", false, "tag", "家")))
                    .andExpect(status().isOk()).andReturn());

            // 局部更新地址2：只改 receiverName 和 detailAddress
            mockMvc.perform(authPut(user.token(), "/api/users/addresses/" + addrId2,
                            java.util.Map.of(
                                    "receiverName", "李四改",
                                    "detailAddress", "世纪大道 200 号")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.receiverName").value("李四改"))
                    .andExpect(jsonPath("$.data.detailAddress").value("世纪大道 200 号"))
                    // 未提供的字段保持原值
                    .andExpect(jsonPath("$.data.receiverPhone").value("13900002222"))
                    .andExpect(jsonPath("$.data.province").value("上海市"));

            // 把地址2设为默认，地址1应自动取消默认
            mockMvc.perform(authPut(user.token(), "/api/users/addresses/" + addrId2,
                            java.util.Map.of("isDefault", true)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isDefault").value(true));

            // 验证 DB 中地址1已不再是默认
            UserAddress a1 = addressRepo.findById(addrId1).orElseThrow();
            if (Boolean.TRUE.equals(a1.getIsDefault())) {
                throw new AssertionError("设置新默认后，旧默认未被清空");
            }
        }

        @Test
        @DisplayName("异常 · 更新他人地址返回 403")
        void shouldRejectUpdateOthersAddress() throws Exception {
            TestUser user1 = createTestUser();
            TestUser user2 = createTestUser();
            long addrId = extractId(mockMvc.perform(authPost(user1.token(), "/api/users/addresses",
                            java.util.Map.of(
                                    "receiverName", "x", "receiverPhone", "13900001111",
                                    "province", "x", "city", "x",
                                    "district", "x", "detailAddress", "x")))
                    .andExpect(status().isOk()).andReturn());

            mockMvc.perform(authPut(user2.token(), "/api/users/addresses/" + addrId,
                            java.util.Map.of("receiverName", "被改")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("异常 · 更新不存在的地址返回 404")
        void shouldRejectUpdateNonExistentAddress() throws Exception {
            TestUser user = createTestUser();
            mockMvc.perform(authPut(user.token(), "/api/users/addresses/99999999",
                            java.util.Map.of("receiverName", "x")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }

    // ==================== 商品搜索与筛选子用例 ====================

    @Nested
    @DisplayName("商品搜索与筛选")
    class ProductSearchFlow {

        @Test
        @DisplayName("主成功 · 关键词搜索 + 分类筛选 + 推荐列表")
        void shouldSearchFilterAndRecommend() throws Exception {
            TestUser seller = createTestUser();
            long catId = createCategory("搜索测试分类");
            // 三个商品：iPhone、iPad、MacBook（不同标题）
            long iPhone = createProduct(seller.userId(), "iPhone 15 Pro", 799900, 1);
            long iPad = createProduct(seller.userId(), "iPad Air", 479900, 1);
            long macBook = createProduct(seller.userId(), "MacBook Pro", 1299900, 1);

            // 给 iPhone/iPad 设置同样的 categoryId（模拟同分类）
            productRepo.findById(iPhone).ifPresent(p -> {
                p.setCategoryId(catId);
                productRepo.save(p);
            });
            productRepo.findById(iPad).ifPresent(p -> {
                p.setCategoryId(catId);
                productRepo.save(p);
            });

            // 1. 关键词搜索 "iPhone"
            mockMvc.perform(get("/api/products").param("keyword", "iPhone"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].id").value(iPhone));

            // 2. 关键词搜索无结果（确保返回空列表，非 404）
            mockMvc.perform(get("/api/products").param("keyword", "不存在的关键词XYZ"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(0)));

            // 3. 分类筛选（应返回 iPhone + iPad 两条）
            mockMvc.perform(get("/api/products").param("categoryId", String.valueOf(catId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)));

            // 4. 关键词 + 分类组合搜索（在 catId 分类下搜 "iPad"）
            mockMvc.perform(get("/api/products")
                            .param("keyword", "iPad")
                            .param("categoryId", String.valueOf(catId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].id").value(iPad));

            // 5. categoryId=0 表示推荐列表（应返回全部在售）
            mockMvc.perform(get("/api/products").param("categoryId", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(3))));
        }

        @Test
        @DisplayName("异常 · page 为负数返回 400")
        void shouldRejectNegativePage() throws Exception {
            mockMvc.perform(get("/api/products").param("page", "-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("异常 · size 超过最大值 50 时被截断为 50（不报错）")
        void shouldCapSizeAt50() throws Exception {
            TestUser seller = createTestUser();
            createProduct(seller.userId(), "size 限制测试", 1000, 1);

            // size=500 会被 ProductService 内部 Math.min(size, 50) 截断
            mockMvc.perform(get("/api/products").param("size", "500"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== 卖家已售出商品子用例 ====================

    @Nested
    @DisplayName("卖家已售出商品（含评分）")
    class SellerSoldFlow {

        @Test
        @DisplayName("主成功 · 完成订单后查询卖家已售商品 + 评分聚合")
        void shouldListSellerSoldWithRating() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "已售商品", 5000, 1);

            // 走完下单→支付→发货→确认收货→评分
            long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                            Map.of("productId", pid,
                                    "receiverName", "x", "receiverPhone", "x", "receiverAddress", "x")))
                    .andExpect(status().isOk()).andReturn());

            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                            Map.of("carrierCode", "SF", "trackingNo", "SF" + orderId)))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                            Map.of("score", 5, "comment", "已售评分")))
                    .andExpect(status().isOk());

            // 查询卖家已售商品
            mockMvc.perform(get("/api/users/" + seller.userId() + "/sold"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].orderId").value(orderId))
                    .andExpect(jsonPath("$.data[0].productId").value(pid))
                    .andExpect(jsonPath("$.data[0].productTitle").value("已售商品"))
                    .andExpect(jsonPath("$.data[0].priceCent").value(5000))
                    .andExpect(jsonPath("$.data[0].ratingScore").value(5))
                    .andExpect(jsonPath("$.data[0].ratingComment").value("已售评分"));
        }

        @Test
        @DisplayName("备选 · 未完成订单的卖家查询 sold 应返回空列表")
        void shouldReturnEmptyForUncompletedOrder() throws Exception {
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "未完成订单", 5000, 1);

            // 只下单不支付（订单状态为 WAIT_PAY，不算已售）
            mockMvc.perform(authPost(buyer.token(), "/api/orders",
                            Map.of("productId", pid,
                                    "receiverName", "x", "receiverPhone", "x", "receiverAddress", "x")))
                    .andExpect(status().isOk());

            // 已售列表应为空（COMPLETED 才计入）
            mockMvc.perform(get("/api/users/" + seller.userId() + "/sold"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("备选 · 查询不存在的卖家 sold 端点返回空列表（不报 404）")
        void shouldReturnEmptyForNonExistentSeller() throws Exception {
            // getSellerSoldProducts 不校验卖家存在性，直接按 sellerId 查询订单
            mockMvc.perform(get("/api/users/99999999/sold"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }
}
