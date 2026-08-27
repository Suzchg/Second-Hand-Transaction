package com.secondhand.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 场景 6：售后处理（申请 + 审批 + 仲裁）。
 */
@DisplayName("场景 6：售后处理（申请 + 审批 + 仲裁）")
class AfterSaleE2eTest extends E2eTestBase {

    @Test
    @DisplayName("退货退款：申请→卖家同意→寄回→确认收货→退款")
    void afterSaleReturnRefundApproved() {
        Ctx ctx = prepareCompletedOrder();
        String seller = ctx.sellerToken();
        String buyer = ctx.buyerToken();
        long oid = ctx.orderId();

        JsonNode req = data(post("/api/after-sale",
                Map.of("orderId", oid, "type", "RETURN_REFUND", "reason", "商品有质量问题",
                        "refundAmountCent", 0, "buyerEvidence", "开箱视频链接"), buyer));
        assertEquals("REQUESTED", req.get("status").asText(), "申请后应为待审核");
        long rid = req.get("id").asLong();

        assertEquals("APPROVED", data(post("/api/after-sale/" + rid + "/approve", null, seller))
                .get("status").asText(), "卖家同意后退货退款应进入待寄件");

        assertEquals("RETURN_SHIPPED", data(post("/api/after-sale/" + rid + "/return-ship",
                Map.of("carrierCode", "SF", "trackingNo", "RT123"), buyer))
                .get("status").asText(), "买家寄回后应进入待收货");

        assertEquals("REFUNDED", data(post("/api/after-sale/" + rid + "/confirm-return", null, seller))
                .get("status").asText(), "卖家确认收货后应退款完成");
    }

    @Test
    @DisplayName("售后被拒→买家申请平台介入→管理员仲裁全额退款")
    void afterSaleArbitration() {
        Ctx ctx = prepareCompletedOrder();
        String seller = ctx.sellerToken();
        String buyer = ctx.buyerToken();
        long oid = ctx.orderId();

        long rid = data(post("/api/after-sale",
                Map.of("orderId", oid, "type", "REFUND_RECEIVED", "reason", "商品与描述不符",
                        "refundAmountCent", 0, "buyerEvidence", "对比图"), buyer))
                .get("id").asLong();

        assertEquals("REJECTED", data(post("/api/after-sale/" + rid + "/reject",
                Map.of("note", "成色属实"), seller)).get("status").asText(), "卖家拒绝后应变为已拒绝");

        assertEquals("PLATFORM_ARBITRATION", data(post("/api/after-sale/" + rid + "/escalate",
                Map.of("evidence", "补充证据"), buyer)).get("status").asText(), "买家申请介入后应进入仲裁");

        JsonNode arbitrated = data(post("/api/admin/after-sale/" + rid + "/arbitrate",
                Map.of("result", "FULL_REFUND", "responsibility", "SELLER", "note", "支持买家"), adminToken()));
        assertEquals("REFUNDED", arbitrated.get("status").asText(), "仲裁全额退款后应退款完成");
    }
}
