package com.secondhand.it;

import com.secondhand.it.support.AbstractIntegrationTest;
import com.secondhand.it.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用例 2：商品发布与编辑
 *
 * 覆盖流程：
 * - 主成功：卖家发布商品→在售状态落库；编辑商品（改价/改名）生效
 * - 备选  ：商品下架/重新上架的状态流转；公开列表与关键词搜索只展示在售商品
 * - 异常  ：未登录发布（401）、标题为空/价格为0（400）、编辑他人商品（403）、
 *           商品不存在（404）、售罄商品重新上架（409）
 *
 * 验证层次：ProductController → ProductService → ProductRepository → 数据库
 */
@DisplayName("用例2：商品发布与编辑")
class Uc2ProductIT extends AbstractIntegrationTest {

    @Nested
    @DisplayName("主成功流程")
    class MainFlow {

        @Test
        @DisplayName("发布：商品进入ON_SALE并持久化（含卖家归属/价格分/默认库存）")
        void createProductPersistsOnSale() throws Exception {
            TestUser seller = registerUser();
            long productId = createProduct(seller.token(), "全新蓝牙机械键盘", 45600);

            // 数据库层断言
            var product = productRepo.findById(productId)
                    .orElseThrow(() -> new AssertionError("商品未持久化到数据库"));
            org.junit.jupiter.api.Assertions.assertEquals("ON_SALE", product.getStatus().name());
            org.junit.jupiter.api.Assertions.assertEquals(seller.userId(), product.getSellerId());
            org.junit.jupiter.api.Assertions.assertEquals(45600, product.getPriceCent());
            org.junit.jupiter.api.Assertions.assertEquals(1, product.getQuantity());
        }

        @Test
        @DisplayName("发布响应：200 + ON_SALE + freeShipping 时运费为0")
        void createProductResponseShape() throws Exception {
            TestUser seller = registerUser();
            doPost("/api/products", seller.token(), """
                    {"title":"包邮测试商品","priceCent":9900,"description":"描述",
                     "quantity":2,"condition":"NEW","freeShipping":true,"shippingFeeCent":0}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                    .andExpect(jsonPath("$.data.freeShipping").value(true))
                    .andExpect(jsonPath("$.data.shippingFeeCent").value(0));
        }

        @Test
        @DisplayName("编辑：修改价格与标题后数据库同步更新")
        void updateProductChangesPersisted() throws Exception {
            TestUser seller = registerUser();
            long productId = createProduct(seller.token(), "编辑前标题", 10000);

            doPut("/api/products/" + productId, seller.token(), """
                    {"title":"编辑后标题","priceCent":8888}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("编辑后标题"))
                    .andExpect(jsonPath("$.data.priceCent").value(8888));

            var product = productRepo.findById(productId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("编辑后标题", product.getTitle());
            org.junit.jupiter.api.Assertions.assertEquals(8888, product.getPriceCent());
            // 未提供的字段保持不变
            org.junit.jupiter.api.Assertions.assertEquals("ON_SALE", product.getStatus().name());
        }
    }

    @Nested
    @DisplayName("备选流程")
    class AlternateFlow {

        @Test
        @DisplayName("下架→重新上架：状态流转并影响公开搜索结果")
        void offSaleThenReListAffectsSearch() throws Exception {
            TestUser seller = registerUser();
            String keyword = "相机的唯一关键词" + System.nanoTime();
            long productId = createProduct(seller.token(), keyword + "相机", 30000);

            // 在售时能被关键词搜索到（公开接口，无需登录）
            doGet("/api/products?keyword=" + keyword, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1));

            // 卖家下架
            doPut("/api/products/" + productId, seller.token(), """
                    {"status":"OFF_SALE"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("OFF_SALE"));
            org.junit.jupiter.api.Assertions.assertEquals("OFF_SALE",
                    productRepo.findById(productId).orElseThrow().getStatus().name());

            // 下架后公开搜索不可见
            doGet("/api/products?keyword=" + keyword, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0));

            // 重新上架后恢复可见
            doPut("/api/products/" + productId, seller.token(), """
                    {"status":"ON_SALE"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ON_SALE"));
            doGet("/api/products?keyword=" + keyword, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("公开浏览：游客可查看商品详情")
        void guestCanViewProductDetail() throws Exception {
            TestUser seller = registerUser();
            long productId = createProduct(seller.token(), "游客可见商品", 15000);

            doGet("/api/products/" + productId, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(productId))
                    .andExpect(jsonPath("$.data.title").value("游客可见商品"));
        }
    }

    @Nested
    @DisplayName("异常流程")
    class ExceptionFlow {

        @Test
        @DisplayName("未登录发布商品：返回401（已修复：补充了AuthenticationEntryPoint）")
        void createWithoutTokenRejected() throws Exception {
            // 无 token 发布商品被安全链拒绝；已配置 RestAuthenticationEntryPoint，
            // 返回 401 + 统一 JSON 错误体（原为无 body 的 403）
            doPost("/api/products", null, """
                    {"title":"未登录商品","priceCent":1000,"description":"x"}
                    """)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("发布校验失败：标题为空返回400 VALIDATION_ERROR")
        void blankTitleReturnsValidation() throws Exception {
            TestUser seller = registerUser();
            doPost("/api/products", seller.token(), """
                    {"title":"","priceCent":1000,"description":"描述"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("发布校验失败：价格为0返回400")
        void zeroPriceReturnsValidation() throws Exception {
            TestUser seller = registerUser();
            doPost("/api/products", seller.token(), """
                    {"title":"零元商品","priceCent":0,"description":"描述"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("编辑他人商品：返回403 FORBIDDEN")
        void updateByNonOwnerForbidden() throws Exception {
            TestUser owner = registerUser();
            TestUser stranger = registerUser();
            long productId = createProduct(owner.token(), "别人的商品", 5000);

            doPut("/api/products/" + productId, stranger.token(), """
                    {"priceCent":1}
                    """)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

            // 数据库中价格未被篡改
            org.junit.jupiter.api.Assertions.assertEquals(5000,
                    productRepo.findById(productId).orElseThrow().getPriceCent());
        }

        @Test
        @DisplayName("编辑不存在商品：返回404 NOT_FOUND")
        void updateNonexistentReturns404() throws Exception {
            TestUser seller = registerUser();
            doPut("/api/products/999999999", seller.token(), """
                    {"priceCent":1}
                    """)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("售罄商品重新上架：返回409 CONFLICT")
        void reListSoldOutProductConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "唯一库存商品", 6600);

            // 买家下单使库存归零（自动下架）→ 售罄
            placeOrder(buyer.token(), productId);
            var product = productRepo.findById(productId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(0, product.getQuantity());
            org.junit.jupiter.api.Assertions.assertEquals("OFF_SALE", product.getStatus().name());

            // 尝试重新上架售罄商品 → 409
            doPut("/api/products/" + productId, seller.token(), """
                    {"status":"ON_SALE"}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }
    }
}
