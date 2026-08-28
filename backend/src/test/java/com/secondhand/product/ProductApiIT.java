package com.secondhand.product;

import com.secondhand.testutil.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** 场景 2：商品发布、编辑及上下架的集成测试。 */
class ProductApiIT extends ApiIntegrationTestBase {
    @Test
    void publishAndEditPersistsAndPublicApiReturnsNewValues() throws Exception {
        // 2.1：核对商品发布后的库存、归属及免邮设置，再验证编辑结果可被公开接口读取。
        Actor seller = user(); long id = product(seller, 2);
        stock(id, 2, "ON_SALE");
        assertThat(count("select count(*) from products where id=? and seller_id=? and shipping_fee_cent=0", id, seller.id())).isEqualTo(1);
        ok("PUT", "/api/products/" + id, Map.of("title", "更新商品", "priceCent", 1500), seller);
        assertThat(jdbc.queryForObject("select price_cent from products where id=?", Integer.class, id)).isEqualTo(1500);
        assertThat(ok("GET", "/api/products/" + id, null, null).path("title").asText()).isEqualTo("更新商品");
    }

    @Test
    void offSaleAndRelistChangesSearchVisibility() throws Exception {
        // 2.2：验证上下架状态会影响搜索结果，使用唯一标题隔离其他测试数据。
        Actor seller = user(); String title = "unique" + nextPhone(); long id = product(seller, 1);
        ok("PUT", "/api/products/" + id, Map.of("title", title, "status", "OFF_SALE"), seller);
        stock(id, 1, "OFF_SALE");
        assertThat(ok("GET", "/api/products?keyword=" + title, null, null).path("totalElements").asLong()).isZero();
        ok("PUT", "/api/products/" + id, Map.of("status", "ON_SALE"), seller);
        assertThat(ok("GET", "/api/products?keyword=" + title, null, null).at("/content/0/id").asLong()).isEqualTo(id);
    }

    @ParameterizedTest
    @CsvSource({"priceCent,0", "quantity,0", "shippingFeeCent,-1"})
    void invalidNumericFieldDoesNotPublish(String field, int value) throws Exception {
        // 2.3～2.5：分别用价格 0、库存 0、运费 -1 验证发布参数边界。
        Actor seller = user(); var body = new HashMap<>(productBody(1)); body.put(field, value);
        error("POST", "/api/products", body, seller, 400, "VALIDATION_ERROR");
        assertThat(count("select count(*) from products where seller_id=?", seller.id())).isZero();
    }

    @Test
    void missingDescriptionDoesNotPublish() throws Exception {
        // 2.6：缺少描述时发布失败，卖家不能留下无效商品记录。
        Actor seller = user(); var body = new HashMap<>(productBody(1)); body.remove("description");
        error("POST", "/api/products", body, seller, 400, "VALIDATION_ERROR");
        assertThat(count("select count(*) from products where seller_id=?", seller.id())).isZero();
    }

    @Test
    void foreignSellerAndInvalidEditLeaveOriginalData() throws Exception {
        // 2.7：越权或非法价格编辑均失败，原商品价格与库存保持不变。
        Actor seller = user(); long id = product(seller, 1);
        error("PUT", "/api/products/" + id, Map.of("priceCent", 2000), user(), 403, "FORBIDDEN");
        error("PUT", "/api/products/" + id, Map.of("priceCent", 0), seller, 400, "VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("select price_cent from products where id=?", Integer.class, id)).isEqualTo(1000);
        stock(id, 1, "ON_SALE");
    }

    @Test
    void missingProductReturnsNotFound() throws Exception {
        // 2.8：查询、编辑不存在的商品都应返回明确的未找到错误。
        error("GET", "/api/products/9223372036854775807", null, null, 404, "NOT_FOUND");
        error("PUT", "/api/products/9223372036854775807", Map.of("title", "missing"), user(), 404, "NOT_FOUND");
    }
}
