package com.secondhand.offer;

import com.secondhand.testutil.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** 场景 5：议价、权限校验与报价订单事务回滚的集成测试。 */
class OfferApiIT extends ApiIntegrationTestBase {
    private long offer(Actor buyer, long productId) throws Exception {
        // 通过真实 API 创建 800 分报价，供接受、拒绝和撤回用例复用。
        return ok("POST", "/api/products/" + productId + "/offers", Map.of("offeredPriceCent", 800, "message", "议价"), buyer).path("id").asLong();
    }

    @Test
    void acceptedOfferLinksDiscountedOrderAndRequiresReceiverBeforePayment() throws Exception {
        // 5.1：接受报价按议价生成订单；补齐地址才能付款，重复接受不能重复下单。
        Actor seller = user(), buyer = user(); long productId = product(seller, 1), offerId = offer(buyer, productId);
        assertThat(count("select count(*) from offers where id=? and status='PENDING' and buyer_id=?", offerId, buyer.id())).isEqualTo(1);
        long orderId = ok("POST", "/api/offers/" + offerId + "/accept", null, seller).path("id").asLong();
        assertThat(count("select count(*) from offers where id=? and status='ACCEPTED' and order_id=?", offerId, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select amount_cent from orders where id=?", Integer.class, orderId)).isEqualTo(800);
        stock(productId, 0, "OFF_SALE");
        error("POST", "/api/orders/" + orderId + "/pay", null, buyer, 400, "BAD_REQUEST");
        orderStatus(orderId, "WAIT_PAY");
        assertThat(count("select count(*) from order_events where order_id=?", orderId)).isEqualTo(1);
        ok("PUT", "/api/orders/" + orderId + "/receiver", receiver(), buyer);
        ok("POST", "/api/orders/" + orderId + "/pay", null, buyer); orderStatus(orderId, "WAIT_DELIVER");
        error("POST", "/api/offers/" + offerId + "/accept", null, seller, 409, "CONFLICT");
        assertThat(count("select count(*) from orders where product_id=?", productId)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"reject,REJECTED", "cancel,CANCELLED"})
    void rejectOrWithdrawDoesNotCreateOrderOrConsumeStock(String action, String status) throws Exception {
        // 5.2～5.3：分别验证卖家拒绝、买家撤回，不生成订单或占用库存。
        Actor seller = user(), buyer = user(); long productId = product(seller, 1), offerId = offer(buyer, productId);
        ok("POST", "/api/offers/" + offerId + "/" + action, null, action.equals("reject") ? seller : buyer);
        assertThat(jdbc.queryForObject("select status from offers where id=?", String.class, offerId)).isEqualTo(status);
        error("POST", "/api/offers/" + offerId + "/accept", null, seller, 409, "CONFLICT");
        stock(productId, 1, "ON_SALE");
        assertThat(count("select count(*) from orders where product_id=?", productId)).isZero();
    }

    @Test
    void soldOutAcceptanceRollsBackEarlierOfferMutation() throws Exception {
        // 5.4：商品被其他买家买走后，接受报价失败必须回滚此前的报价状态修改。
        Actor seller = user(), buyer = user(); long productId = product(seller, 1), offerId = offer(buyer, productId);
        ok("POST", "/api/orders", orderBody(productId), user());
        error("POST", "/api/offers/" + offerId + "/accept", null, seller, 409, "CONFLICT");
        // 接受动作先修改报价再创建订单，必须查询 MySQL 确认这两个操作一起回滚。
        assertThat(count("select count(*) from offers where id=? and status='PENDING' and order_id is null", offerId)).isEqualTo(1);
        assertThat(count("select count(*) from orders where product_id=?", productId)).isEqualTo(1);
        stock(productId, 0, "OFF_SALE");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidPriceDoesNotPersistOffer(int price) throws Exception {
        // 5.5～5.6：零价和负价报价均被拒绝，数据库不能产生报价记录。
        Actor seller = user(), buyer = user(); long productId = product(seller, 1);
        error("POST", "/api/products/" + productId + "/offers", Map.of("offeredPriceCent", price), buyer, 400, "BAD_REQUEST");
        assertThat(count("select count(*) from offers where product_id=?", productId)).isZero(); stock(productId, 1, "ON_SALE");
    }

    @Test
    void ownOrSoldOutProductCannotReceiveOffer() throws Exception {
        // 5.7：自有商品及售罄商品不能收到新的报价。
        Actor seller = user(); long productId = product(seller, 1);
        error("POST", "/api/products/" + productId + "/offers", Map.of("offeredPriceCent", 800), seller, 403, "FORBIDDEN");
        ok("POST", "/api/orders", orderBody(productId), user());
        error("POST", "/api/products/" + productId + "/offers", Map.of("offeredPriceCent", 800), user(), 409, "CONFLICT");
        assertThat(count("select count(*) from offers where product_id=?", productId)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject", "cancel"})
    void unrelatedUserCannotMutateOffer(String action) throws Exception {
        // 5.8～5.10：分别验证无关用户不能接受、拒绝或撤回报价。
        Actor seller = user(), buyer = user(); long productId = product(seller, 1), offerId = offer(buyer, productId);
        error("POST", "/api/offers/" + offerId + "/" + action, null, user(), 403, "FORBIDDEN");
        assertThat(jdbc.queryForObject("select status from offers where id=?", String.class, offerId)).isEqualTo("PENDING");
        assertThat(count("select count(*) from orders where product_id=?", productId)).isZero();
    }
}
