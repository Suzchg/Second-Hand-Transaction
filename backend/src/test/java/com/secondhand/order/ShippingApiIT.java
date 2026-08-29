package com.secondhand.order;

import com.secondhand.testutil.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** 场景 4：发货、物流查询与收货状态的集成测试。 */
class ShippingApiIT extends ApiIntegrationTestBase {
    @Test
    void shipPersistsWaybillAndTrackingUsesStoredCarrierAndNumber() throws Exception {
        // 4.1：验证运单落库、物流参数传递和确认收货，串联订单与物流模块。
        Trade t = paidTrade();
        var shipment = ok("POST", "/api/orders/" + t.orderId() + "/ship", shipping(t.orderId()), t.seller());
        assertThat(shipment.path("trackingNo").asText()).isEqualTo("IT-SF-" + t.orderId());
        assertThat(count("select count(*) from shipments where order_id=? and carrier_code='SF' and tracking_no=?",
                t.orderId(), "IT-SF-" + t.orderId())).isEqualTo(1);
        orderStatus(t.orderId(), "WAIT_RECEIVE");
        assertThat(jdbc.queryForList("select to_status from order_events where order_id=? order by id", String.class, t.orderId()))
                .containsExactly("WAIT_PAY", "WAIT_DELIVER", "WAIT_RECEIVE");
        var track = ok("GET", "/api/shipments/" + t.orderId() + "/track", null, null);
        assertThat(track.path("carrierCode").asText()).isEqualTo("SF");
        assertThat(track.path("trackingNo").asText()).isEqualTo(shipment.path("trackingNo").asText());
        assertThat(track.path("points").size()).isEqualTo(4);
        ok("POST", "/api/orders/" + t.orderId() + "/confirm", null, t.buyer());
        orderStatus(t.orderId(), "COMPLETED");
    }

    @Test
    void repeatShippingCannotOverwriteWaybill() throws Exception {
        // 4.2：重复发货不能覆盖原运单，也不能重复追加发货事件。
        Trade t = paidTrade();
        ok("POST", "/api/orders/" + t.orderId() + "/ship", shipping(t.orderId()), t.seller());
        error("POST", "/api/orders/" + t.orderId() + "/ship", Map.of("carrierCode", "YTO", "trackingNo", "WRONG"), t.seller(), 409, "CONFLICT");
        assertThat(count("select count(*) from shipments where order_id=?", t.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select tracking_no from shipments where order_id=?", String.class, t.orderId())).isEqualTo("IT-SF-" + t.orderId());
        assertThat(count("select count(*) from order_events where order_id=?", t.orderId())).isEqualTo(3);
    }

    @Test
    void unpaidOrderHasNoWaybillAndCannotShip() throws Exception {
        // 4.3：未付款订单不能发货，物流查询应提示尚无运单。
        Trade t = trade();
        error("POST", "/api/orders/" + t.orderId() + "/ship", shipping(t.orderId()), t.seller(), 409, "CONFLICT");
        error("GET", "/api/shipments/" + t.orderId() + "/track", null, null, 404, "NOT_FOUND");
        assertThat(count("select count(*) from shipments where order_id=?", t.orderId())).isZero();
        orderStatus(t.orderId(), "WAIT_PAY");
    }

    @Test
    void otherSellerAndInvalidWaybillCannotShip() throws Exception {
        // 4.4：非卖家操作或空运单号均不得生成物流记录或推进订单状态。
        Trade t = paidTrade();
        error("POST", "/api/orders/" + t.orderId() + "/ship", shipping(t.orderId()), user(), 404, "NOT_FOUND");
        error("POST", "/api/orders/" + t.orderId() + "/ship", Map.of("carrierCode", "SF", "trackingNo", ""), t.seller(), 400, "VALIDATION_ERROR");
        assertThat(count("select count(*) from shipments where order_id=?", t.orderId())).isZero();
        orderStatus(t.orderId(), "WAIT_DELIVER");
    }

    @Test
    void unshippedOrderCannotBeConfirmedAndMissingTrackingIsNotFound() throws Exception {
        // 4.5：未发货不能确认收货，不存在订单的物流查询返回未找到。
        Trade t = paidTrade();
        error("POST", "/api/orders/" + t.orderId() + "/confirm", null, t.buyer(), 409, "CONFLICT");
        error("GET", "/api/shipments/9223372036854775807/track", null, null, 404, "NOT_FOUND");
        orderStatus(t.orderId(), "WAIT_DELIVER");
    }
}
