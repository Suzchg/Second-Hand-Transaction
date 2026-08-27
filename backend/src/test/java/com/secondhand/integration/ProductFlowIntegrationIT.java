package com.secondhand.integration;

import com.secondhand.product.entity.ProductStatus;
import com.secondhand.product.repository.ProductRepository;
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
 * 集成测试 · 用例 2：商品发布与编辑
 *
 * 覆盖范围：
 * - 模块间调用：ProductController → ProductService → ProductRepository → FavoriteRepository（推荐查询）
 * - 数据库访问：商品、收藏、评论三张表的真实持久化
 * - 对外接口：
 *   - POST/PUT/GET /api/products
 *   - POST/DELETE/GET /api/products/{id}/favorite
 *   - POST/GET /api/products/{id}/comments
 *   - GET /api/categories
 *
 * 用例流程覆盖：
 * - 主成功流程：发布商品→编辑→上架下架→收藏→评论
 * - 备选流程：编辑字段局部更新、收藏重复幂等、分页查询
 * - 异常流程：购买自己商品、收藏自己商品、编辑他人商品、参数校验失败、商品不存在
 */
@Testcontainers
@DisplayName("用例2：商品发布与编辑（含收藏、评论）")
class ProductFlowIntegrationIT extends AbstractIntegrationIT {

    @Autowired ProductRepository productRepo;

    // ==================== 主成功流程 ====================

    @Test
    @DisplayName("主成功 · 发布商品→编辑→查询→下架→上架")
    void shouldCreateEditAndToggleShelfProduct() throws Exception {
        TestUser seller = createTestUser();
        long categoryId = createCategory("测试分类A");

        // 1. 发布商品
        long productId = extractId(mockMvc.perform(authPost(seller.token(), "/api/products",
                        Map.of(
                                "title", "iPhone 14 Pro 256GB",
                                "priceCent", 699900,
                                "coverImageUrl", "https://cdn.example.com/iphone.jpg",
                                "description", "99新国行带票，电池健康度98%",
                                "categoryId", categoryId,
                                "quantity", 3,
                                "condition", "NINE_TENTHS",
                                "freeShipping", true,
                                "shippingFeeCent", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id", greaterThan(0)))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.title").value("iPhone 14 Pro 256GB"))
                .andExpect(jsonPath("$.data.priceCent").value(699900))
                .andExpect(jsonPath("$.data.quantity").value(3))
                .andReturn());

        // 2. 公开 GET /api/products/{id} 查询（无需鉴权）
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.title").value("iPhone 14 Pro 256GB"));

        // 3. 卖家编辑商品（局部更新标题和价格）
        mockMvc.perform(authPut(seller.token(), "/api/products/" + productId,
                        Map.of(
                                "title", "iPhone 14 Pro Max 512GB",
                                "priceCent", 899900)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("iPhone 14 Pro Max 512GB"))
                .andExpect(jsonPath("$.data.priceCent").value(899900))
                // 未更新的字段保持不变
                .andExpect(jsonPath("$.data.quantity").value(3))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));

        // 4. 下架
        mockMvc.perform(authPut(seller.token(), "/api/products/" + productId,
                        Map.of("status", "OFF_SALE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));

        // 5. 重新上架
        mockMvc.perform(authPut(seller.token(), "/api/products/" + productId,
                        Map.of("status", "ON_SALE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    @Test
    @DisplayName("主成功 · 商品分页列表（公开端点）")
    void shouldListProductsPublicly() throws Exception {
        TestUser seller = createTestUser();
        for (int i = 0; i < 5; i++) {
            createProduct(seller.userId(), "Test商品" + i, 10000 + i * 100, 1);
        }

        mockMvc.perform(get("/api/products").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(5)));
    }

    // ==================== 备选流程 ====================

    @Test
    @DisplayName("备选 · 编辑商品局部更新（只改 freeShipping + shippingFee）")
    void shouldPartiallyUpdateProduct() throws Exception {
        TestUser seller = createTestUser();
        long pid = createProduct(seller.userId(), "鼠标 X1", 12900, 1);

        mockMvc.perform(authPut(seller.token(), "/api/products/" + pid,
                        Map.of(
                                "freeShipping", false,
                                "shippingFeeCent", 1500)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freeShipping").value(false))
                .andExpect(jsonPath("$.data.shippingFeeCent").value(1500))
                // 标题、价格不变
                .andExpect(jsonPath("$.data.title").value("鼠标 X1"))
                .andExpect(jsonPath("$.data.priceCent").value(12900));
    }

    @Test
    @DisplayName("备选 · 售罄商品无法上架（库存=0 时强制 ON_SALE 返回 409）")
    void shouldRejectOnSaleWhenSoldOut() throws Exception {
        TestUser seller = createTestUser();
        long pid = createProduct(seller.userId(), "库存为0测试", 1000, 0,
                ProductStatus.OFF_SALE);

        mockMvc.perform(authPut(seller.token(), "/api/products/" + pid,
                        Map.of("status", "ON_SALE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("备选 · 公开 GET /api/categories 返回分类树")
    void shouldListCategories() throws Exception {
        // 应用启动时 CategoryService.seedCategories 已写入 23 个一级分类
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", not(empty())))
                .andExpect(jsonPath("$.data[0].name", not(blankOrNullString())));
    }

    // ==================== 异常流程 ====================

    @Test
    @DisplayName("异常 · 编辑不存在的商品返回 404")
    void shouldRejectEditNonExistentProduct() throws Exception {
        TestUser seller = createTestUser();
        mockMvc.perform(authPut(seller.token(), "/api/products/99999999",
                        Map.of("title", "any")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("异常 · 非卖家编辑他人商品返回 403")
    void shouldRejectEditOthersProduct() throws Exception {
        TestUser seller1 = createTestUser();
        TestUser seller2 = createTestUser();
        long pid = createProduct(seller1.userId(), "他人商品", 5000, 1);

        mockMvc.perform(authPut(seller2.token(), "/api/products/" + pid,
                        Map.of("title", "hacked")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 发布商品缺少必填字段返回 400")
    void shouldRejectMissingProductFields() throws Exception {
        TestUser seller = createTestUser();
        // 缺少 title 字段
        mockMvc.perform(authPost(seller.token(), "/api/products",
                        Map.of(
                                "priceCent", 1000,
                                "description", "无标题")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("异常 · 发布商品价格非法（<=0）返回 400")
    void shouldRejectInvalidPrice() throws Exception {
        TestUser seller = createTestUser();
        mockMvc.perform(authPost(seller.token(), "/api/products",
                        Map.of(
                                "title", "test",
                                "priceCent", 0,
                                "description", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("异常 · 未登录发布商品返回 401")
    void shouldRejectCreateProductWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(toJson(Map.of(
                                "title", "x", "priceCent", 100,
                                "description", "x"))))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 收藏子用例 ====================

    @Nested
    @DisplayName("收藏")
    class FavoriteFlow {

        @Test
        @DisplayName("主成功 · 收藏→查询状态→列表→取消")
        void shouldFavoriteAndUnfavorite() throws Exception {
            TestUser buyer = createTestUser();
            TestUser seller = createTestUser();
            long pid = createProduct(seller.userId(), "可收藏商品", 3000, 1);

            // 收藏
            mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid + "/favorite"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 状态查询
            mockMvc.perform(authGet(buyer.token(), "/api/products/" + pid + "/favorite/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true));

            // 列表查询
            mockMvc.perform(authGet(buyer.token(), "/api/users/favorites"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].productId").value(pid));

            // 取消收藏
            mockMvc.perform(authDelete(buyer.token(), "/api/products/" + pid + "/favorite"))
                    .andExpect(status().isOk());

            // 状态查询应为 false
            mockMvc.perform(authGet(buyer.token(), "/api/products/" + pid + "/favorite/status"))
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("备选 · 重复收藏幂等（不报错）")
        void shouldIdempotentFavorite() throws Exception {
            TestUser buyer = createTestUser();
            TestUser seller = createTestUser();
            long pid = createProduct(seller.userId(), "幂等测试", 1000, 1);

            // 收藏两次
            mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid + "/favorite"))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid + "/favorite"))
                    .andExpect(status().isOk());

            // 列表里仍然只有 1 条
            mockMvc.perform(authGet(buyer.token(), "/api/users/favorites"))
                    .andExpect(jsonPath("$.data.content", hasSize(1)));
        }

        @Test
        @DisplayName("异常 · 卖家收藏自己的商品返回 403")
        void shouldRejectFavoriteOwnProduct() throws Exception {
            TestUser seller = createTestUser();
            long pid = createProduct(seller.userId(), "自己商品", 1000, 1);

            mockMvc.perform(authPost(seller.token(), "/api/products/" + pid + "/favorite"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("异常 · 未登录访问收藏状态返回 401")
        void shouldRejectFavoriteStatusWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/products/1/favorite/status"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== 评论子用例 ====================

    @Nested
    @DisplayName("评论")
    class CommentFlow {

        @Test
        @DisplayName("主成功 · 发表评论→公开列表查询")
        void shouldAddAndListComments() throws Exception {
            TestUser commenter = createTestUser();
            TestUser seller = createTestUser();
            long pid = createProduct(seller.userId(), "可评论商品", 5000, 1);

            // 发表评论1
            mockMvc.perform(authPost(commenter.token(), "/api/products/" + pid + "/comments",
                            Map.of("content", "还在吗？可以小刀吗？")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").value("还在吗？可以小刀吗？"))
                    .andExpect(jsonPath("$.data.userId").value(commenter.userId()));

            // 发表评论2
            mockMvc.perform(authPost(commenter.token(), "/api/products/" + pid + "/comments",
                            Map.of("content", "成色怎么样？")))
                    .andExpect(status().isOk());

            // 公开查询评论列表（无需鉴权）
            mockMvc.perform(get("/api/products/" + pid + "/comments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)));
        }

        @Test
        @DisplayName("异常 · 评论内容为空返回 400")
        void shouldRejectBlankComment() throws Exception {
            TestUser commenter = createTestUser();
            TestUser seller = createTestUser();
            long pid = createProduct(seller.userId(), "测试评论", 1000, 1);

            mockMvc.perform(authPost(commenter.token(), "/api/products/" + pid + "/comments",
                            Map.of("content", "")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("异常 · 未登录发表评论返回 401")
        void shouldRejectCommentWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/products/1/comments")
                            .contentType("application/json")
                            .content(toJson(Map.of("content", "x"))))
                    .andExpect(status().isUnauthorized());
        }
    }
}
