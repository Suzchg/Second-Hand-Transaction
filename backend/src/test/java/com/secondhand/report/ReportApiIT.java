package com.secondhand.report;

import com.secondhand.testutil.ApiIntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Map;
import java.util.stream.StreamSupport;
import static org.assertj.core.api.Assertions.assertThat;

/** 场景 7：举报审核落库与系统通知接收范围的集成测试。 */
class ReportApiIT extends ApiIntegrationTestBase {
    private Map<String, Object> reportBody() {
        // 构造有效的假冒商品举报请求。
        return Map.of("reasonType", "COUNTERFEIT", "description", "疑似假冒商品");
    }

    @ParameterizedTest
    @CsvSource({"handle,HANDLED,report_handled", "dismiss,DISMISSED,report_dismissed"})
    void reviewPersistsAuditAndNotifiesOnlyRelevantUsers(String action, String status, String messageType) throws Exception {
        // 7.1～7.2：分别验证办结、驳回的审核记录及通知，不向无关用户泄漏信息。
        Actor seller = user(), reporter = user(), other = user(), admin = admin(); long productId = product(seller, 1);
        long id = ok("POST", "/api/products/" + productId + "/report", reportBody(), reporter).path("id").asLong();
        assertThat(count("select count(*) from reports where id=? and status='PENDING' and reporter_id=? and product_id=?", id, reporter.id(), productId)).isEqualTo(1);
        assertThat(ok("GET", "/api/messages/system", null, reporter)).isEmpty();
        ok("PUT", "/api/admin/reports/" + id + "/" + action, Map.of("handleNote", "核查结论"), admin);
        assertThat(count("select count(*) from reports where id=? and status=? and handled_by=? and handled_at is not null and handle_note=?",
                id, status, admin.id(), "核查结论")).isEqualTo(1);
        JsonNode messages = ok("GET", "/api/messages/system", null, reporter);
        var matching = StreamSupport.stream(messages.spliterator(), false).filter(m -> m.path("id").asText().equals("report-" + id)).toList();
        assertThat(matching).hasSize(1);
        assertThat(matching.get(0).path("type").asText()).isEqualTo(messageType);
        assertThat(matching.get(0).path("relatedId").asText()).isEqualTo(String.valueOf(productId));
        assertThat(matching.get(0).path("content").asText()).contains("核查结论");
        assertThat(ok("GET", "/api/messages/system", null, other)).isEmpty();
        JsonNode sellerMessages = ok("GET", "/api/messages/system", null, seller);
        assertThat(sellerMessages.size()).isEqualTo(action.equals("handle") ? 1 : 0);
        if (action.equals("handle")) assertThat(sellerMessages.get(0).path("type").asText()).isEqualTo("report_product_handled");
    }

    @Test
    void ownMissingProductAndInvalidReasonDoNotCreateReport() throws Exception {
        // 7.3：自报、目标不存在、原因缺失或非法时，均不能新增举报。
        Actor seller = user(), reporter = user(); long productId = product(seller, 1);
        long reports = count("select count(*) from reports");
        error("POST", "/api/products/" + productId + "/report", reportBody(), seller, 403, "FORBIDDEN");
        error("POST", "/api/products/9223372036854775807/report", reportBody(), reporter, 404, "NOT_FOUND");
        error("POST", "/api/products/" + productId + "/report", Map.of("description", "missing reason"), reporter, 400, "VALIDATION_ERROR");
        error("POST", "/api/products/" + productId + "/report", Map.of("reasonType", "INVALID"), reporter, 400, "BAD_REQUEST");
        assertThat(count("select count(*) from reports")).isEqualTo(reports);
    }

    @ParameterizedTest
    @CsvSource({"handle", "dismiss"})
    void ordinaryUserCannotReviewOrTriggerNotification(String action) throws Exception {
        // 7.4～7.5：普通用户不能办结或驳回举报，也不能触发审核通知。
        Actor seller = user(), reporter = user(); long productId = product(seller, 1);
        long id = ok("POST", "/api/products/" + productId + "/report", reportBody(), reporter).path("id").asLong();
        assertThat(call("PUT", "/api/admin/reports/" + id + "/" + action, Map.of("handleNote", "unauthorized"), reporter)
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(count("select count(*) from reports where id=? and status='PENDING' and handled_by is null", id)).isEqualTo(1);
        assertThat(ok("GET", "/api/messages/system", null, reporter)).isEmpty();
    }

    @Test
    void missingReportCannotBeReviewed() throws Exception {
        // 7.6：管理员处理不存在的举报时返回未找到，举报表记录数不变。
        Actor admin = admin(); long reports = count("select count(*) from reports");
        error("PUT", "/api/admin/reports/9223372036854775807/handle", null, admin, 404, "NOT_FOUND");
        error("PUT", "/api/admin/reports/9223372036854775807/dismiss", null, admin, 404, "NOT_FOUND");
        assertThat(count("select count(*) from reports")).isEqualTo(reports);
    }
}
