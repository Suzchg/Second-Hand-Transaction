package com.secondhand.integration;

import com.secondhand.report.entity.ReportReason;
import com.secondhand.report.entity.ReportStatus;
import com.secondhand.report.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试 · 用例 7：举报处理（举报 + 审核驳回/办结）
 *
 * 覆盖范围：
 * - 模块间调用：ReportController → ReportService → ProductService（校验商品存在）
 *              AdminReportController → ReportService
 * - 数据库访问：reports 表
 * - 对外接口：
 *   - POST /api/products/{productId}/report
 *   - GET  /api/admin/reports（分页）
 *   - PUT  /api/admin/reports/{id}/handle   （管理员办结）
 *   - PUT  /api/admin/reports/{id}/dismiss  （管理员驳回）
 *
 * 状态机：
 *   PENDING → HANDLED（已办结）
 *   PENDING → DISMISSED（已驳回）
 *
 * 用例流程覆盖：
 * - 主成功流程：用户举报 → 管理员办结
 * - 备选流程：管理员驳回举报
 * - 异常流程：举报自己商品、商品不存在、未登录、普通用户访问 /api/admin/reports
 */
@Testcontainers
@DisplayName("用例7：举报处理")
class ReportFlowIntegrationIT extends AbstractIntegrationIT {

    @Autowired ReportRepository reportRepo;

    // ==================== 主成功流程 ====================

    @Test
    @DisplayName("主成功 · 用户举报 → 管理员办结")
    void shouldSubmitAndHandleReport() throws Exception {
        TestUser seller = createTestUser();
        TestUser reporter = createTestUser();
        TestUser admin = createTestUser(com.secondhand.auth.entity.Role.ADMIN);
        long pid = createProduct(seller.userId(), "被举报商品", 10000, 1);

        // 1. 用户举报商品（虚假描述）
        long reportId = extractId(mockMvc.perform(authPost(reporter.token(),
                        "/api/products/" + pid + "/report",
                        Map.of(
                                "reasonType", "FALSE_DESC",
                                "description", "商品描述与实物严重不符")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reasonType").value("FALSE_DESC"))
                .andExpect(jsonPath("$.data.reporterId").value(reporter.userId()))
                .andExpect(jsonPath("$.data.productId").value(pid))
                .andReturn());

        // 2. 管理员查看待处理举报列表
        mockMvc.perform(authGet(admin.token(), "/api/admin/reports").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

        // 3. 管理员办结举报
        mockMvc.perform(authPut(admin.token(), "/api/admin/reports/" + reportId + "/handle",
                        Map.of("handleNote", "已下架违规商品")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HANDLED"))
                .andExpect(jsonPath("$.data.handledBy").value(admin.userId()))
                .andExpect(jsonPath("$.data.handleNote").value("已下架违规商品"))
                .andExpect(jsonPath("$.data.handledAt", notNullValue()));
    }

    // ==================== 备选流程 ====================

    @Test
    @DisplayName("备选 · 管理员驳回举报（无违规）")
    void shouldDismissReport() throws Exception {
        TestUser seller = createTestUser();
        TestUser reporter = createTestUser();
        TestUser admin = createTestUser(com.secondhand.auth.entity.Role.ADMIN);
        long pid = createProduct(seller.userId(), "合法商品", 10000, 1);

        long reportId = extractId(mockMvc.perform(authPost(reporter.token(),
                        "/api/products/" + pid + "/report",
                        Map.of("reasonType", "PRIVACY", "description", "侵犯隐私")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPut(admin.token(), "/api/admin/reports/" + reportId + "/dismiss",
                        Map.of("handleNote", "举报理由不成立")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISMISSED"));
    }

    @Test
    @DisplayName("备选 · 举报不带 description（理由仅选枚举）")
    void shouldSubmitReportWithoutDescription() throws Exception {
        TestUser seller = createTestUser();
        TestUser reporter = createTestUser();
        long pid = createProduct(seller.userId(), "无描述举报", 10000, 1);

        mockMvc.perform(authPost(reporter.token(), "/api/products/" + pid + "/report",
                        Map.of("reasonType", "PROHIBITED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reasonType").value("PROHIBITED"))
                .andExpect(jsonPath("$.data.description").isEmpty());
    }

    @Test
    @DisplayName("备选 · 同一用户可举报多个不同商品")
    void shouldReportMultipleProducts() throws Exception {
        TestUser seller = createTestUser();
        TestUser reporter = createTestUser();
        long pid1 = createProduct(seller.userId(), "商品A", 1000, 1);
        long pid2 = createProduct(seller.userId(), "商品B", 2000, 1);

        mockMvc.perform(authPost(reporter.token(), "/api/products/" + pid1 + "/report",
                        Map.of("reasonType", "COUNTERFEIT")))
                .andExpect(status().isOk());
        mockMvc.perform(authPost(reporter.token(), "/api/products/" + pid2 + "/report",
                        Map.of("reasonType", "PRICE_FRAUD")))
                .andExpect(status().isOk());

        // 应该都有
        assertThat(reportRepo.count(), greaterThanOrEqualTo(2L));
    }

    // ==================== 异常流程 ====================

    @Test
    @DisplayName("异常 · 举报自己的商品返回 403")
    void shouldRejectReportOwnProduct() throws Exception {
        TestUser seller = createTestUser();
        long pid = createProduct(seller.userId(), "自举报", 1000, 1);

        mockMvc.perform(authPost(seller.token(), "/api/products/" + pid + "/report",
                        Map.of("reasonType", "OTHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 举报不存在的商品返回 404")
    void shouldRejectReportNonExistentProduct() throws Exception {
        TestUser reporter = createTestUser();
        mockMvc.perform(authPost(reporter.token(), "/api/products/99999999/report",
                        Map.of("reasonType", "OTHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("异常 · 举报缺少 reasonType 返回 400")
    void shouldRejectReportWithoutReasonType() throws Exception {
        TestUser seller = createTestUser();
        TestUser reporter = createTestUser();
        long pid = createProduct(seller.userId(), "无理由", 1000, 1);

        // 不传 reasonType 字段
        mockMvc.perform(authPost(reporter.token(), "/api/products/" + pid + "/report",
                        Map.of("description", "随便说")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("异常 · 未登录举报返回 401")
    void shouldRejectReportWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/products/1/report")
                        .contentType("application/json")
                        .content(toJson(Map.of("reasonType", "OTHER"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("异常 · 普通用户访问 /api/admin/reports 返回 403")
    void shouldRejectNonAdminAccessReports() throws Exception {
        TestUser user = createTestUser();
        mockMvc.perform(authGet(user.token(), "/api/admin/reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("异常 · 管理员处理不存在的举报返回 404")
    void shouldRejectHandleNonExistentReport() throws Exception {
        TestUser admin = createTestUser(com.secondhand.auth.entity.Role.ADMIN);
        mockMvc.perform(authPut(admin.token(), "/api/admin/reports/99999999/handle",
                        Map.of("handleNote", "x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
