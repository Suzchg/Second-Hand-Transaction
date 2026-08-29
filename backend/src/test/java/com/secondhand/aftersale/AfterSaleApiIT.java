package com.secondhand.aftersale;

import com.secondhand.testutil.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** 场景 6：售后审批、仲裁、超时与订单联动的集成测试。 */
class AfterSaleApiIT extends ApiIntegrationTestBase {
    private Map<String, Object> application(Trade t, String type, int amount) {
        // 统一售后申请参数，金额单位为分。
        return Map.of("orderId", t.orderId(), "type", type, "reason", "商品存在问题",
                "refundAmountCent", amount, "buyerEvidence", "买家凭证");
    }

    private long apply(Trade t, String type, int amount) throws Exception {
        // 通过 API 发起售后，同时核对售后单、订单状态及买卖双方关联。
        long id = ok("POST", "/api/after-sale", application(t, type, amount), t.buyer()).path("id").asLong();
        state(id, "REQUESTED"); orderStatus(t.orderId(), "AFTER_SALE");
        assertThat(count("select count(*) from after_sale_requests where id=? and order_id=? and buyer_id=? and seller_id=?",
                id, t.orderId(), t.buyer().id(), t.seller().id())).isEqualTo(1);
        return id;
    }

    private void state(long id, String status) {
        // 直接查询数据库确认售后状态，避免仅依赖接口返回值。
        assertThat(jdbc.queryForObject("select status from after_sale_requests where id=?", String.class, id)).isEqualTo(status);
    }

    private void escalate(Trade t, long id) throws Exception {
        // 构造卖家拒绝、买家申请平台介入的仲裁前置状态。
        ok("POST", "/api/after-sale/" + id + "/reject", Map.of("note", "卖家拒绝"), t.seller()); state(id, "REJECTED");
        ok("POST", "/api/after-sale/" + id + "/escalate", Map.of("evidence", "补充凭证"), t.buyer()); state(id, "PLATFORM_ARBITRATION");
    }

    @Test
    void returnRefundPersistsEveryStageAndCancelsOrder() throws Exception {
        // 6.1：核对退货退款每个阶段的落库状态，全额退款后订单作废。
        Trade t = completedTrade(); long id = apply(t, "RETURN_REFUND", 1000);
        ok("POST", "/api/after-sale/" + id + "/approve", null, t.seller()); state(id, "APPROVED");
        ok("POST", "/api/after-sale/" + id + "/return-ship", Map.of("carrierCode", "SF", "trackingNo", "RETURN-" + id), t.buyer());
        state(id, "RETURN_SHIPPED");
        assertThat(jdbc.queryForObject("select return_tracking_no from after_sale_requests where id=?", String.class, id)).isEqualTo("RETURN-" + id);
        ok("POST", "/api/after-sale/" + id + "/confirm-return", null, t.seller());
        state(id, "REFUNDED"); orderStatus(t.orderId(), "CANCELLED");
        assertThat(count("select count(*) from after_sale_requests where id=? and refunded_at is not null", id)).isEqualTo(1);
        assertThat(ok("GET", "/api/after-sale/" + id, null, t.buyer()).path("refundAmountCent").asInt()).isEqualTo(1000);
    }

    @ParameterizedTest
    @CsvSource({"REFUND_RECEIVED,1000,CANCELLED", "PARTIAL_REFUND,300,COMPLETED"})
    void directRefundAlternativeUpdatesOrderAccordingToAmount(String type, int amount, String orderState) throws Exception {
        // 6.2～6.3：全额退款关闭订单，部分退款则保留订单完成状态。
        Trade t = completedTrade(); long id = apply(t, type, amount);
        ok("POST", "/api/after-sale/" + id + "/approve", null, t.seller());
        state(id, "REFUNDED"); orderStatus(t.orderId(), orderState);
        assertThat(jdbc.queryForObject("select refund_amount_cent from after_sale_requests where id=?", Integer.class, id)).isEqualTo(amount);
    }

    @ParameterizedTest
    @CsvSource({"FULL_REFUND,REFUNDED,CANCELLED,1000", "PARTIAL_REFUND,REFUNDED,COMPLETED,300",
            "DISMISS,CLOSED,COMPLETED,1000", "RETURN_REFUND,APPROVED,AFTER_SALE,1000"})
    void adminArbitrationAlternativesPersistDecisionAndRelatedOrder(String result, String requestState, String orderState, int refund) throws Exception {
        // 6.4～6.7：分别验证四种仲裁结果、责任及运费落库，并完成裁定退货后的退款流程。
        Trade t = completedTrade(); long id = apply(t, "RETURN_REFUND", 1000); escalate(t, id);
        ok("POST", "/api/admin/after-sale/" + id + "/arbitrate", Map.of("result", result,
                "partialRefundCent", 300, "responsibility", "SELLER", "shippingPaidBy", "SELLER",
                "shippingCostCent", 100, "note", "管理员裁决"), admin());
        state(id, requestState); orderStatus(t.orderId(), orderState);
        assertThat(count("select count(*) from after_sale_requests where id=? and refund_amount_cent=? and responsibility='SELLER' and shipping_paid_by='SELLER' and shipping_cost_cent=100", id, refund)).isEqualTo(1);
        if (result.equals("RETURN_REFUND")) {
            ok("POST", "/api/after-sale/" + id + "/return-ship", shipping(t.orderId()), t.buyer());
            ok("POST", "/api/after-sale/" + id + "/confirm-return", null, t.seller());
            state(id, "REFUNDED"); orderStatus(t.orderId(), "CANCELLED");
        }
    }

    @Test
    void buyerCancellationClosesRequestAndRestoresOrder() throws Exception {
        // 6.8：买家取消售后后恢复原订单，卖家不能继续审批已关闭的申请。
        Trade t = completedTrade(); long id = apply(t, "REFUND_RECEIVED", 1000);
        ok("POST", "/api/after-sale/" + id + "/cancel", null, t.buyer()); state(id, "CLOSED"); orderStatus(t.orderId(), "COMPLETED");
        error("POST", "/api/after-sale/" + id + "/approve", null, t.seller(), 409, "CONFLICT");
        orderStatus(t.orderId(), "COMPLETED");
    }

    @Test
    void duplicateApplicationDoesNotCreateSecondRequest() throws Exception {
        // 6.9：重复申请不能生成第二张售后单，也不能改写原申请状态。
        Trade t = completedTrade(); long id = apply(t, "REFUND_RECEIVED", 1000);
        error("POST", "/api/after-sale", application(t, "RETURN_REFUND", 1000), t.buyer(), 409, "CONFLICT");
        assertThat(count("select count(*) from after_sale_requests where order_id=?", t.orderId())).isEqualTo(1);
        state(id, "REQUESTED"); orderStatus(t.orderId(), "AFTER_SALE");
    }

    @Test
    void unconfirmedAndExpiredOrdersRejectAfterSaleWithoutMutation() throws Exception {
        // 6.10：分别验证未确认收货和超过 7 天窗口的申请失败，订单不变。
        Trade unpaid = trade();
        error("POST", "/api/after-sale", application(unpaid, "REFUND_RECEIVED", 1000), unpaid.buyer(), 403, "FORBIDDEN");
        orderStatus(unpaid.orderId(), "WAIT_PAY");
        assertThat(count("select count(*) from after_sale_requests where order_id=?", unpaid.orderId())).isZero();
        Trade expired = completedTrade();
        jdbc.update("update orders set completed_at=DATE_SUB(NOW(), INTERVAL 8 DAY) where id=?", expired.orderId());
        error("POST", "/api/after-sale", application(expired, "REFUND_RECEIVED", 1000), expired.buyer(), 410, "CLOSED");
        orderStatus(expired.orderId(), "COMPLETED");
        assertThat(count("select count(*) from after_sale_requests where order_id=?", expired.orderId())).isZero();
    }

    @Test
    void unrelatedUserCannotApplyReadOrApprove() throws Exception {
        // 6.11：无关用户不能申请、查看或审批他人的售后。
        Trade t = completedTrade(); Actor other = user();
        error("POST", "/api/after-sale", application(t, "REFUND_RECEIVED", 1000), other, 403, "FORBIDDEN");
        long id = apply(t, "REFUND_RECEIVED", 1000);
        error("GET", "/api/after-sale/" + id, null, other, 403, "FORBIDDEN");
        error("POST", "/api/after-sale/" + id + "/approve", null, other, 403, "FORBIDDEN");
        state(id, "REQUESTED"); orderStatus(t.orderId(), "AFTER_SALE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"escalate", "return-ship", "confirm-return"})
    void illegalStageDoesNotChangeRequestOrOrder(String action) throws Exception {
        // 6.12～6.14：在未审批阶段尝试介入、寄回或确认退货，均不得推进状态。
        Trade t = completedTrade(); long id = apply(t, "RETURN_REFUND", 1000);
        error("POST", "/api/after-sale/" + id + "/" + action, shipping(t.orderId()),
                action.equals("confirm-return") ? t.seller() : t.buyer(), 409, "CONFLICT");
        state(id, "REQUESTED"); orderStatus(t.orderId(), "AFTER_SALE");
    }

    @Test
    void nonAdminCannotArbitrateAndInvalidDecisionRollsBack() throws Exception {
        // 6.15：验证管理员权限，并检查无效裁决是否回滚已修改的责任和裁决字段。
        Trade t = completedTrade(); long id = apply(t, "RETURN_REFUND", 1000); escalate(t, id);
        assertThat(call("POST", "/api/admin/after-sale/" + id + "/arbitrate", Map.of("result", "FULL_REFUND"), t.buyer())
                .getResponse().getStatus()).isEqualTo(403);
        error("POST", "/api/admin/after-sale/" + id + "/arbitrate", Map.of("result", "INVALID", "responsibility", "BUYER"), admin(), 400, "BAD_REQUEST");
        state(id, "PLATFORM_ARBITRATION"); orderStatus(t.orderId(), "AFTER_SALE");
        assertThat(count("select count(*) from after_sale_requests where id=? and responsibility is null and arbitration_result is null", id)).isEqualTo(1);
    }

    @Test
    void sellerTimeoutAutomaticallyApprovesReturn() throws Exception {
        // 6.16：将处理截止时间设为过去，验证超时任务自动同意退货而非立即退款。
        Trade t = completedTrade(); long id = apply(t, "RETURN_REFUND", 1000);
        jdbc.update("update after_sale_requests set deadline_at=DATE_SUB(NOW(), INTERVAL 1 HOUR) where id=?", id);
        ok("POST", "/api/admin/after-sale/process-timeouts", null, admin());
        state(id, "APPROVED"); orderStatus(t.orderId(), "AFTER_SALE");
    }
}
