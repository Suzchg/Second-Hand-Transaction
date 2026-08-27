package com.secondhand.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 场景 7：举报处理（举报 + 审核）。
 */
@DisplayName("场景 7：举报处理（举报 + 审核）")
class ReportE2eTest extends E2eTestBase {

    @Test
    @DisplayName("举报违规商品→管理员办结")
    void reportHandled() {
        String seller = registerUser();
        String reporter = registerUser();
        long pid = data(post("/api/products", productBody("违规商品"), seller)).get("id").asLong();

        JsonNode report = data(post("/api/products/" + pid + "/report",
                Map.of("reasonType", "COUNTERFEIT", "description", "疑似假货"), reporter));
        assertEquals("PENDING", report.get("status").asText(), "提交后举报应为待处理");
        long rid = report.get("id").asLong();

        JsonNode handled = data(put("/api/admin/reports/" + rid + "/handle",
                Map.of("handleNote", "已下架处理"), adminToken()));
        assertEquals("HANDLED", handled.get("status").asText(), "管理员办结后应为已处理");
    }

    @Test
    @DisplayName("举报违规商品→管理员驳回")
    void reportDismissed() {
        String seller = registerUser();
        String reporter = registerUser();
        long pid = data(post("/api/products", productBody("争议商品"), seller)).get("id").asLong();

        long rid = data(post("/api/products/" + pid + "/report",
                Map.of("reasonType", "FALSE_DESC", "description", "描述不符"), reporter))
                .get("id").asLong();

        JsonNode dismissed = data(put("/api/admin/reports/" + rid + "/dismiss",
                Map.of("handleNote", "描述属实"), adminToken()));
        assertEquals("DISMISSED", dismissed.get("status").asText(), "管理员驳回后应为已驳回");
    }

    @Test
    @DisplayName("举报自己的商品应返回 403")
    void reportOwnProductForbidden() {
        String seller = registerUser();
        long pid = data(post("/api/products", productBody("自有商品"), seller)).get("id").asLong();

        ResponseEntity<String> resp = post("/api/products/" + pid + "/report",
                Map.of("reasonType", "OTHER", "description", "x"), seller);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals("FORBIDDEN", errorCode(resp));
    }
}
