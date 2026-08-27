package com.secondhand.product.service;

import com.secondhand.common.AppException;
import com.secondhand.favorite.repository.FavoriteRepository;
import com.secondhand.product.entity.Product;
import com.secondhand.product.entity.ProductCondition;
import com.secondhand.product.entity.ProductStatus;
import com.secondhand.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProductService 单元测试。
 * 覆盖 UC04 发布、UC05 编辑、UC06 浏览的关键业务规则与异常分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 单元测试")
class ProductServiceTest {

    @Mock ProductRepository productRepo;
    @Mock FavoriteRepository favoriteRepo;

    ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepo, favoriteRepo);
    }

    /** 通过反射构造带 id、sellerId 的 Product（无 setter） */
    private static Product product(long id, long sellerId) {
        Product p = new Product();
        try {
            var f = Product.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (Exception ignored) {}
        p.setSellerId(sellerId);
        return p;
    }

    @Nested
    @DisplayName("create - 发布商品")
    class Create {

        @Test
        @DisplayName("发布成功应保存并默认在售")
        void shouldSaveOnSale() {
            when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product p = service.create(1L, new ProductService.CreateCommand(
                    "商品", 1000, null, "描述", null, 1, ProductCondition.NEW, false, 500));

            assertEquals(1L, p.getSellerId());
            assertEquals(ProductStatus.ON_SALE, p.getStatus());
            assertEquals(1000, p.getPriceCent());
            verify(productRepo).save(any(Product.class));
        }

        @Test
        @DisplayName("库存为空或非正数时默认 1")
        void shouldDefaultQuantityToOne() {
            when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product p = service.create(1L, new ProductService.CreateCommand(
                    "商品", 1000, null, "描述", null, null, ProductCondition.NEW, false, 500));
            assertEquals(1, p.getQuantity());

            Product p2 = service.create(1L, new ProductService.CreateCommand(
                    "商品", 1000, null, "描述", null, 0, ProductCondition.NEW, false, 500));
            assertEquals(1, p2.getQuantity());
        }

        @Test
        @DisplayName("免邮时邮费强制为 0")
        void shouldZeroShippingWhenFree() {
            when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product p = service.create(1L, new ProductService.CreateCommand(
                    "商品", 1000, null, "描述", null, 1, ProductCondition.NEW, true, 500));

            assertTrue(p.getFreeShipping());
            assertEquals(0, p.getShippingFeeCent());
        }
    }

    @Nested
    @DisplayName("update - 编辑商品")
    class Update {

        @Test
        @DisplayName("商品不存在应抛 404")
        void shouldThrowNotFound() {
            when(productRepo.findById(99L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () ->
                    service.update(1L, 99L, new ProductService.UpdateCommand(
                            null, null, null, null, null, null, null, null, null)));
            assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
            assertEquals("NOT_FOUND", ex.getCode());
        }

        @Test
        @DisplayName("非卖家编辑应抛 403")
        void shouldThrowForbiddenForNonSeller() {
            when(productRepo.findById(5L)).thenReturn(Optional.of(product(5L, 2L)));

            AppException ex = assertThrows(AppException.class, () ->
                    service.update(1L, 5L, new ProductService.UpdateCommand(
                            null, null, null, null, null, null, null, null, null)));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
            assertEquals("FORBIDDEN", ex.getCode());
        }

        @Test
        @DisplayName("售罄商品（库存 0）上架应抛 409")
        void shouldRejectRelistWhenSoldOut() {
            Product p = product(5L, 1L);
            p.setQuantity(0);
            p.setStatus(ProductStatus.OFF_SALE);
            when(productRepo.findById(5L)).thenReturn(Optional.of(p));

            AppException ex = assertThrows(AppException.class, () ->
                    service.update(1L, 5L, new ProductService.UpdateCommand(
                            null, null, null, null, ProductStatus.ON_SALE, null, null, null, null)));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
            assertEquals("CONFLICT", ex.getCode());
        }

        @Test
        @DisplayName("卖家本人可编辑且仅更新非空字段")
        void shouldUpdateOwnProduct() {
            Product p = product(5L, 1L);
            p.setTitle("旧标题");
            p.setPriceCent(1000);
            p.setQuantity(1);
            when(productRepo.findById(5L)).thenReturn(Optional.of(p));
            when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product updated = service.update(1L, 5L, new ProductService.UpdateCommand(
                    "新标题", null, null, null, null, null, ProductCondition.LIKE_NEW, null, null));

            assertEquals("新标题", updated.getTitle());
            assertEquals(1000, updated.getPriceCent()); // 未更新的字段保持不变
            assertEquals(ProductCondition.LIKE_NEW, updated.getCondition());
            verify(productRepo).save(p);
        }

        @Test
        @DisplayName("免邮切换时邮费强制为 0")
        void shouldZeroShippingOnFreeSwitch() {
            Product p = product(5L, 1L);
            p.setQuantity(1);
            p.setShippingFeeCent(800);
            when(productRepo.findById(5L)).thenReturn(Optional.of(p));
            when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product updated = service.update(1L, 5L, new ProductService.UpdateCommand(
                    null, null, null, null, null, null, null, true, null));

            assertTrue(updated.getFreeShipping());
            assertEquals(0, updated.getShippingFeeCent());
        }
    }

    @Nested
    @DisplayName("getById / listOnSale - 浏览")
    class Browse {

        @Test
        @DisplayName("详情不存在应抛 404")
        void shouldThrowNotFoundOnDetail() {
            when(productRepo.findById(99L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () -> service.getById(99L));
            assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        }

        @Test
        @DisplayName("列表只查在售且有库存，size 上限 50")
        void shouldListOnlyOnSaleWithCappedSize() {
            Page<Product> page = Page.empty();
            when(productRepo.findByStatusAndQuantityGreaterThan(
                    eq(ProductStatus.ON_SALE), eq(0), any(Pageable.class))).thenReturn(page);

            Page<Product> result = service.listOnSale(0, 200);

            assertSame(page, result);
            ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
            verify(productRepo).findByStatusAndQuantityGreaterThan(
                    eq(ProductStatus.ON_SALE), eq(0), captor.capture());
            assertEquals(50, captor.getValue().getPageSize());
        }
    }
}
