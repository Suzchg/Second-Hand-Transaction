package com.secondhand.offer.service;

import com.secondhand.common.AppException;
import com.secondhand.offer.entity.Offer;
import com.secondhand.offer.entity.OfferStatus;
import com.secondhand.offer.repository.OfferRepository;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.service.OrderService;
import com.secondhand.product.entity.Product;
import com.secondhand.product.entity.ProductStatus;
import com.secondhand.product.service.ProductService;
import com.secondhand.testutil.TestId;
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
 * 出价议价单元测试(用例5 出价议价:出价+接受/拒绝)。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OfferService 单元测试")
class OfferServiceTest {

    @Mock
    OfferRepository offerRepo;
    @Mock
    ProductService productService;
    @Mock
    OrderService orderService;

    OfferService offerService;

    private static final long BUYER = 1L;
    private static final long SELLER = 2L;

    @BeforeEach
    void buildService() {
        offerService = new OfferService(offerRepo, productService, orderService);
        // 异常路径测试不会触发 save,故 lenient
        lenient().when(offerRepo.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product onSaleProduct() {
        Product p = new Product();
        p.setSellerId(SELLER);
        p.setPriceCent(5000);
        p.setQuantity(1);
        p.setStatus(ProductStatus.ON_SALE);
        TestId.set(p, 10L);
        return p;
    }

    private Offer pendingOffer() {
        Offer offer = new Offer();
        offer.setProductId(10L);
        offer.setBuyerId(BUYER);
        offer.setSellerId(SELLER);
        offer.setOfferedPriceCent(4000);
        offer.setStatus(OfferStatus.PENDING);
        TestId.set(offer, 30L);
        return offer;
    }

    @Nested
    @DisplayName("用例5 发起报价 createOffer")
    class CreateOffer {

        @Test
        @DisplayName("UNIT-TC05-01 买家成功发起报价:状态为待卖家回复")
        void shouldCreateOffer() {
            when(productService.getById(10L)).thenReturn(onSaleProduct());

            Offer result = offerService.createOffer(BUYER, 10L, 4000, "便宜点吧");

            assertEquals(OfferStatus.PENDING, result.getStatus());
            assertEquals(BUYER, result.getBuyerId());
            assertEquals(SELLER, result.getSellerId());
            assertEquals(4000, result.getOfferedPriceCent());
            assertEquals("便宜点吧", result.getMessage());
            assertNull(result.getOrderId());
        }

        @Test
        @DisplayName("UNIT-TC05-02 不能给自己的商品报价,抛出 FORBIDDEN")
        void shouldRejectSelfOffer() {
            when(productService.getById(10L)).thenReturn(onSaleProduct());

            AppException ex = assertThrows(AppException.class,
                    () -> offerService.createOffer(SELLER, 10L, 4000, null));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC05-03 已售罄商品不能报价,抛出 CONFLICT")
        void shouldRejectSoldOutOffer() {
            Product product = onSaleProduct();
            product.setQuantity(0);
            when(productService.getById(10L)).thenReturn(product);

            AppException ex = assertThrows(AppException.class,
                    () -> offerService.createOffer(BUYER, 10L, 4000, null));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC05-04 无效报价金额被拒绝,抛出 BAD_REQUEST")
        void shouldRejectInvalidPrice() {
            when(productService.getById(10L)).thenReturn(onSaleProduct());

            assertThrows(AppException.class, () -> offerService.createOffer(BUYER, 10L, null, null));
            AppException ex = assertThrows(AppException.class,
                    () -> offerService.createOffer(BUYER, 10L, 0, null));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        }
    }

    @Nested
    @DisplayName("用例5 卖家接受报价 acceptOffer")
    class AcceptOffer {

        @Test
        @DisplayName("UNIT-TC05-05 接受报价:报价标记已接受,按报价价格创建订单")
        void shouldAcceptOffer() {
            Offer offer = pendingOffer();
            when(offerRepo.findById(30L)).thenReturn(Optional.of(offer));
            when(productService.getById(10L)).thenReturn(onSaleProduct());

            Order created = new Order();
            created.setBuyerId(BUYER);
            created.setSellerId(SELLER);
            created.setAmountCent(4000);
            created.setStatus(OrderStatus.WAIT_PAY);
            TestId.set(created, 40L);
            when(orderService.createOrderWithPrice(eq(BUYER), any(OrderService.CreateOrderCommand.class), eq(4000)))
                    .thenReturn(created);

            Order result = offerService.acceptOffer(SELLER, 30L);

            assertSame(created, result);
            assertEquals(OfferStatus.ACCEPTED, offer.getStatus());
            assertEquals(40L, offer.getOrderId());
            verify(offerRepo).save(offer);
        }

        @Test
        @DisplayName("UNIT-TC05-06 非卖家不能接受报价,抛出 FORBIDDEN")
        void shouldRejectAcceptByNonSeller() {
            when(offerRepo.findById(30L)).thenReturn(Optional.of(pendingOffer()));

            AppException ex = assertThrows(AppException.class, () -> offerService.acceptOffer(99L, 30L));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC05-07 非待处理报价不能接受,抛出 CONFLICT")
        void shouldRejectAcceptNonPending() {
            Offer offer = pendingOffer();
            offer.setStatus(OfferStatus.CANCELLED);
            when(offerRepo.findById(30L)).thenReturn(Optional.of(offer));

            AppException ex = assertThrows(AppException.class, () -> offerService.acceptOffer(SELLER, 30L));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
            verify(orderService, never()).createOrderWithPrice(anyLong(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("用例5 卖家拒绝报价 rejectOffer")
    class RejectOffer {

        @Test
        @DisplayName("UNIT-TC05-08 卖家拒绝报价:状态为已拒绝")
        void shouldRejectOffer() {
            Offer offer = pendingOffer();
            when(offerRepo.findById(30L)).thenReturn(Optional.of(offer));

            Offer result = offerService.rejectOffer(SELLER, 30L);

            assertEquals(OfferStatus.REJECTED, result.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC05-09 非待处理报价不能拒绝,抛出 CONFLICT")
        void shouldRejectNonPending() {
            Offer offer = pendingOffer();
            offer.setStatus(OfferStatus.ACCEPTED);
            when(offerRepo.findById(30L)).thenReturn(Optional.of(offer));

            AppException ex = assertThrows(AppException.class, () -> offerService.rejectOffer(SELLER, 30L));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }
    }

    @Nested
    @DisplayName("用例5 买家取消报价 cancelOffer")
    class CancelOffer {

        @Test
        @DisplayName("UNIT-TC05-10 买家取消报价:状态为已取消")
        void shouldCancelOffer() {
            Offer offer = pendingOffer();
            when(offerRepo.findById(30L)).thenReturn(Optional.of(offer));

            Offer result = offerService.cancelOffer(BUYER, 30L);

            assertEquals(OfferStatus.CANCELLED, result.getStatus());
        }

        @Test
        @DisplayName("UNIT-TC05-11 非买家不能取消报价,抛出 FORBIDDEN")
        void shouldRejectCancelByNonBuyer() {
            when(offerRepo.findById(30L)).thenReturn(Optional.of(pendingOffer()));

            AppException ex = assertThrows(AppException.class, () -> offerService.cancelOffer(99L, 30L));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        }

        @Test
        @DisplayName("UNIT-TC05-12 非待处理报价不能取消,抛出 CONFLICT")
        void shouldRejectCancelNonPending() {
            Offer offer = pendingOffer();
            offer.setStatus(OfferStatus.REJECTED);
            when(offerRepo.findById(30L)).thenReturn(Optional.of(offer));

            AppException ex = assertThrows(AppException.class, () -> offerService.cancelOffer(BUYER, 30L));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }
    }
}
