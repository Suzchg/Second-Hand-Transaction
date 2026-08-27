package com.secondhand.report.service;

import com.secondhand.common.AppException;
import com.secondhand.product.entity.Product;
import com.secondhand.product.service.ProductService;
import com.secondhand.report.entity.Report;
import com.secondhand.report.entity.ReportReason;
import com.secondhand.report.entity.ReportStatus;
import com.secondhand.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReportService 单元测试（UC07 举报，重点用例）。
 * 覆盖：不能举报自己的商品、提交置为 PENDING、处理→HANDLED、驳回→DISMISSED。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService 单元测试")
class ReportServiceTest {

    @Mock ReportRepository reportRepo;
    @Mock ProductService productService;

    ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(reportRepo, productService);
    }

    private static Product productOf(long sellerId) {
        Product p = new Product();
        p.setSellerId(sellerId);
        return p;
    }

    private static Report report(long id, ReportStatus status) {
        Report r = new Report();
        try {
            var f = Report.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(r, id);
        } catch (Exception ignored) {}
        r.setStatus(status);
        return r;
    }

    @Nested
    @DisplayName("submit - 提交举报")
    class Submit {

        @Test
        @DisplayName("不能举报自己的商品应抛 403")
        void shouldRejectOwnProduct() {
            when(productService.getById(10L)).thenReturn(productOf(1L));

            AppException ex = assertThrows(AppException.class, () ->
                    service.submit(1L, 10L, ReportReason.FALSE_DESC, "描述"));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
            assertEquals("FORBIDDEN", ex.getCode());
            verify(reportRepo, never()).save(any());
        }

        @Test
        @DisplayName("举报成功应置为 PENDING 并保存")
        void shouldSaveAsPending() {
            when(productService.getById(10L)).thenReturn(productOf(2L));
            when(reportRepo.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

            Report r = service.submit(1L, 10L, ReportReason.FALSE_DESC, "描述");

            assertEquals(ReportStatus.PENDING, r.getStatus());
            assertEquals(1L, r.getReporterId());
            assertEquals(10L, r.getProductId());
            assertEquals(ReportReason.FALSE_DESC, r.getReasonType());
            verify(reportRepo).save(any(Report.class));
        }
    }

    @Nested
    @DisplayName("handle - 处理举报")
    class Handle {

        @Test
        @DisplayName("举报不存在应抛 404")
        void shouldThrowNotFound() {
            when(reportRepo.findById(99L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () -> service.handle(99L, 1L, "已处理"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        }

        @Test
        @DisplayName("处理成功应置为 HANDLED 并记录处理信息")
        void shouldMarkHandled() {
            Report r = report(1L, ReportStatus.PENDING);
            when(reportRepo.findById(1L)).thenReturn(Optional.of(r));
            when(reportRepo.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

            Report handled = service.handle(1L, 9L, "已下架");

            assertEquals(ReportStatus.HANDLED, handled.getStatus());
            assertEquals(9L, handled.getHandledBy());
            assertEquals("已下架", handled.getHandleNote());
            assertNotNull(handled.getHandledAt());
        }
    }

    @Nested
    @DisplayName("dismiss - 驳回举报")
    class Dismiss {

        @Test
        @DisplayName("举报不存在应抛 404")
        void shouldThrowNotFound() {
            when(reportRepo.findById(99L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () -> service.dismiss(99L, 1L, "原因"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        }

        @Test
        @DisplayName("驳回成功应置为 DISMISSED 并记录处理信息")
        void shouldMarkDismissed() {
            Report r = report(1L, ReportStatus.PENDING);
            when(reportRepo.findById(1L)).thenReturn(Optional.of(r));
            when(reportRepo.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

            Report dismissed = service.dismiss(1L, 9L, "证据不足");

            assertEquals(ReportStatus.DISMISSED, dismissed.getStatus());
            assertEquals(9L, dismissed.getHandledBy());
            assertEquals("证据不足", dismissed.getHandleNote());
            assertNotNull(dismissed.getHandledAt());
        }
    }
}
