package com.secondhand.it;

import com.secondhand.it.support.AbstractIntegrationTest;
import com.secondhand.it.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用例 7：举报处理（举报 + 审核）
 *
 * 覆盖流程：
 * - 主成功：用户举报违规商品→PENDING落库；管理员办结→HANDLED（处理人/时间/备注落库）
 * - 备选  ：管理员驳回→DISMISSED；管理端按状态筛选举报列表
 * - 异常  ：举报自己的商品（403）、未登录举报（401）、举报不存在商品（404）、
 *           举报类型缺失（400）、普通用户访问管理端接口（403）
 *
 * 验证层次：ReportController → ReportService（联动 ProductService）→ 管理端 AdminReportController
 */
@DisplayName("用例7：举报处理")
class Uc7ReportIT extends AbstractIntegrationTest {

    @Nested
    @DisplayName("主成功流程")
    class MainFlow {

        @Test
        @DisplayName("举报办结：用户举报PENDING落库→管理员办结HANDLED")
        void submitThenAdminHandles() throws Exception {
            TestUser seller = registerUser();
            TestUser reporter = registerUser();
            long productId = createProduct(seller.token(), "被举报的假货商品", 99900);

            // 1. 用户提交举报
            long reportId = data(doPost("/api/products/%d/report".formatted(productId),
                    reporter.token(), """
                    {"reasonType":"COUNTERFEIT","description":"疑似假货，包装粗糙"}
                    """))
                    .path("id").asLong();

            // 举报落库为待处理
            var report = reportRepo.findById(reportId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("PENDING", report.getStatus().name());
            org.junit.jupiter.api.Assertions.assertEquals(reporter.userId(), report.getReporterId());
            org.junit.jupiter.api.Assertions.assertEquals(productId, report.getProductId());
            org.junit.jupiter.api.Assertions.assertEquals("COUNTERFEIT",
                    report.getReasonType().name());

            // 2. 管理员办结举报（下架违规商品 + 办结记录）
            doPut("/api/admin/reports/%d/handle".formatted(reportId), adminToken(), """
                    {"handleNote":"核实为假货，已下架商品并封禁卖家"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("HANDLED"));

            // 数据库断言：办结信息完整落库
            var handled = reportRepo.findById(reportId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("HANDLED", handled.getStatus().name());
            org.junit.jupiter.api.Assertions.assertNotNull(handled.getHandledAt());
            org.junit.jupiter.api.Assertions.assertNotNull(handled.getHandledBy());
            org.junit.jupiter.api.Assertions.assertTrue(
                    handled.getHandleNote().contains("假货"));
        }
    }

    @Nested
    @DisplayName("备选流程")
    class AlternateFlow {

        @Test
        @DisplayName("举报驳回：管理员驳回后状态DISMISSED")
        void adminDismissesReport() throws Exception {
            TestUser seller = registerUser();
            TestUser reporter = registerUser();
            long productId = createProduct(seller.token(), "被误举报商品", 3000);

            long reportId = data(doPost("/api/products/%d/report".formatted(productId),
                    reporter.token(), """
                    {"reasonType":"OTHER","description":"看不顺眼"}
                    """)).path("id").asLong();

            doPut("/api/admin/reports/%d/dismiss".formatted(reportId), adminToken(), """
                    {"handleNote":"恶意举报，予以驳回"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DISMISSED"));

            org.junit.jupiter.api.Assertions.assertEquals("DISMISSED",
                    reportRepo.findById(reportId).orElseThrow().getStatus().name());
        }

        @Test
        @DisplayName("管理端列表：按状态筛选待处理举报")
        void adminListsPendingReports() throws Exception {
            TestUser seller = registerUser();
            TestUser reporter = registerUser();
            long productId = createProduct(seller.token(), "列表筛选商品", 4000);

            long reportId = data(doPost("/api/products/%d/report".formatted(productId),
                    reporter.token(), """
                    {"reasonType":"PRICE_FRAUD","description":"价格虚高"}
                    """)).path("id").asLong();

            doGet("/api/admin/reports?status=PENDING", adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());

            // 办结后不再出现在待处理列表
            doPut("/api/admin/reports/%d/handle".formatted(reportId), adminToken(),
                    "{\"handleNote\":\"ok\"}").andExpect(status().isOk());
            String body = content(doGet("/api/admin/reports?status=PENDING", adminToken()));
            org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"id\":" + reportId + ","),
                    "办结后的举报不应出现在待处理列表");
        }
    }

    @Nested
    @DisplayName("异常流程")
    class ExceptionFlow {

        @Test
        @DisplayName("举报自己的商品：返回403 FORBIDDEN")
        void reportOwnProductForbidden() throws Exception {
            TestUser seller = registerUser();
            long productId = createProduct(seller.token(), "自己的商品", 2000);

            doPost("/api/products/%d/report".formatted(productId), seller.token(), """
                    {"reasonType":"OTHER","description":"测试自举报"}
                    """)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("未登录举报：返回403（安全链拒绝）")
        void reportWithoutTokenRejected() throws Exception {
            TestUser seller = registerUser();
            long productId = createProduct(seller.token(), "未登录举报目标", 2500);

            doPost("/api/products/%d/report".formatted(productId), null, """
                    {"reasonType":"OTHER","description":"未登录举报"}
                    """)
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("举报不存在的商品：返回404 NOT_FOUND")
        void reportNonexistentProduct404() throws Exception {
            TestUser reporter = registerUser();
            doPost("/api/products/999999999/report", reporter.token(), """
                    {"reasonType":"OTHER","description":"幽灵商品"}
                    """)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("举报类型缺失：返回400 VALIDATION_ERROR")
        void reportMissingReasonTypeRejected() throws Exception {
            TestUser seller = registerUser();
            TestUser reporter = registerUser();
            long productId = createProduct(seller.token(), "类型缺失目标", 3500);

            doPost("/api/products/%d/report".formatted(productId), reporter.token(), """
                    {"description":"没有类型的举报"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("普通用户访问管理端：返回403（角色权限）")
        void regularUserCannotAccessAdminEndpoints() throws Exception {
            TestUser user = registerUser();
            doGet("/api/admin/reports", user.token())
                    .andExpect(status().isForbidden());
        }
    }
}
