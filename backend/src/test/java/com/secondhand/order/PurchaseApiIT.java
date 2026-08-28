package com.secondhand.order;

import com.secondhand.testutil.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** 场景 3：购买、支付与取消订单的跨模块集成测试。 */
class PurchaseApiIT extends ApiIntegrationTestBase {
    @Test
    void purchaseAndPayCommitStockOrderAndEvents() throws Exception {
        // 3.1：核对下单扣库存、支付状态及事件记录在数据库中一致提交。
        Trade t = trade(); stock(t.productId(), 0, "OFF_SALE"); orderStatus(t.orderId(), "WAIT_PAY");
        assertThat(count("select count(*) from orders where id=? and buyer_id=? and seller_id=? and amount_cent=1000",
                t.orderId(), t.buyer().id(), t.seller().id())).isEqualTo(1);
        ok("POST", "/api/orders/" + t.orderId() + "/pay", null, t.buyer());
        orderStatus(t.orderId(), "WAIT_DELIVER");
        assertThat(count("select count(*) from orders where id=? and paid_at is not null", t.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForList("select to_status from order_events where order_id=? order by id", String.class, t.orderId()))
                .containsExactly("WAIT_PAY", "WAIT_DELIVER");
        assertThat(ok("GET", "/api/orders/" + t.orderId(), null, t.seller()).path("canShip").asBoolean()).isTrue();
    }

    @Test
    void cancelRestoresStockAndAllowsAnotherBuyerToOrder() throws Exception {
        // 3.2：取消只恢复一次库存，商品随后可被其他买家重新购买。
        Trade t = trade();
        ok("POST", "/api/orders/" + t.orderId() + "/cancel", null, t.buyer());
        orderStatus(t.orderId(), "CANCELLED"); stock(t.productId(), 1, "ON_SALE");
        error("POST", "/api/orders/" + t.orderId() + "/cancel", null, t.buyer(), 409, "CONFLICT");
        stock(t.productId(), 1, "ON_SALE");
        assertThat(count("select count(*) from order_events where order_id=?", t.orderId())).isEqualTo(2);
        long second = ok("POST", "/api/orders", orderBody(t.productId()), user()).path("id").asLong();
        orderStatus(second, "WAIT_PAY"); stock(t.productId(), 0, "OFF_SALE");
    }

    @Test
    void invalidReceiverAndOwnProductDoNotConsumeStock() throws Exception {
        // 3.3：收货信息缺失或购买自有商品时，不生成订单、不扣库存。
        Actor seller = user(), buyer = user(); long id = product(seller, 1);
        error("POST", "/api/orders", Map.of("productId", id), buyer, 400, "VALIDATION_ERROR");
        error("POST", "/api/orders", orderBody(id), seller, 403, "FORBIDDEN");
        stock(id, 1, "ON_SALE");
        assertThat(count("select count(*) from orders where product_id=?", id)).isZero();
    }

    @Test
    void soldOutAndMissingProductCannotCreateOrder() throws Exception {
        // 3.4：验证售罄和不存在商品的下单失败分支，不能留下新订单。
        Trade t = trade(); Actor other = user();
        error("POST", "/api/orders", orderBody(t.productId()), other, 409, "CONFLICT");
        error("POST", "/api/orders", orderBody(Long.MAX_VALUE), other, 404, "NOT_FOUND");
        assertThat(count("select count(*) from orders where buyer_id=?", other.id())).isZero(); stock(t.productId(), 0, "OFF_SALE");
    }

    @Test
    void otherBuyerCannotPayCancelOrReadOrder() throws Exception {
        // 3.5：其他买家不能支付、取消或查看该订单，原状态和事件不变。
        Trade t = trade(); Actor other = user();
        for (String action : List.of("pay", "cancel")) {
            error("POST", "/api/orders/" + t.orderId() + "/" + action, null, other, 404, "NOT_FOUND");
        }
        error("GET", "/api/orders/" + t.orderId(), null, other, 403, "FORBIDDEN");
        orderStatus(t.orderId(), "WAIT_PAY"); stock(t.productId(), 0, "OFF_SALE");
        assertThat(count("select count(*) from order_events where order_id=?", t.orderId())).isEqualTo(1);
    }

    @Test
    void repeatPaymentAndPaidCancellationDoNotWriteExtraEvents() throws Exception {
        // 3.6：已支付订单拒绝重复支付和取消，不能重复写事件或恢复库存。
        Trade t = paidTrade();
        error("POST", "/api/orders/" + t.orderId() + "/pay", null, t.buyer(), 409, "CONFLICT");
        error("POST", "/api/orders/" + t.orderId() + "/cancel", null, t.buyer(), 409, "CONFLICT");
        orderStatus(t.orderId(), "WAIT_DELIVER"); stock(t.productId(), 0, "OFF_SALE");
        assertThat(count("select count(*) from order_events where order_id=?", t.orderId())).isEqualTo(2);
    }

    @Test
    void multiQuantityPurchaseKeepsRemainingProductOnSale() throws Exception {
        // 3.7：库存为 2 时逐次购买，只有最后一件售出后才自动下架。
        Actor seller = user(); long id = product(seller, 2);
        ok("POST", "/api/orders", orderBody(id), user()); stock(id, 1, "ON_SALE");
        ok("POST", "/api/orders", orderBody(id), user()); stock(id, 0, "OFF_SALE");
        assertThat(count("select count(*) from orders where product_id=?", id)).isEqualTo(2);
    }
}
