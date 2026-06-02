package com.secondhand.offer.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.offer.entity.Offer;
import com.secondhand.offer.service.OfferService;
import com.secondhand.order.entity.Order;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    /** 买家发起报价 */
    @PostMapping("/api/products/{productId}/offers")
    public ApiResponse<Offer> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId,
            @RequestBody CreateOfferRequest req) {
        return ApiResponse.ok(offerService.createOffer(
                principal.userId(), productId, req.offeredPriceCent(), req.message()));
    }

    /** 某商品的报价列表（卖家看） */
    @GetMapping("/api/products/{productId}/offers")
    public ApiResponse<List<Offer>> listForProduct(@PathVariable Long productId) {
        return ApiResponse.ok(offerService.getOffersForProduct(productId));
    }

    /** 卖家接受报价 */
    @PostMapping("/api/offers/{id}/accept")
    public ApiResponse<Order> accept(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long offerId) {
        return ApiResponse.ok(offerService.acceptOffer(principal.userId(), offerId));
    }

    /** 卖家拒绝报价 */
    @PostMapping("/api/offers/{id}/reject")
    public ApiResponse<Offer> reject(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long offerId) {
        return ApiResponse.ok(offerService.rejectOffer(principal.userId(), offerId));
    }

    /** 买家取消报价 */
    @PostMapping("/api/offers/{id}/cancel")
    public ApiResponse<Offer> cancel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long offerId) {
        return ApiResponse.ok(offerService.cancelOffer(principal.userId(), offerId));
    }

    /** 买家的报价列表 */
    @GetMapping("/api/my-offers")
    public ApiResponse<List<Offer>> myOffers(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(offerService.getBuyerOffers(principal.userId()));
    }

    /** 卖家收到的报价列表 */
    @GetMapping("/api/seller-offers")
    public ApiResponse<List<Offer>> sellerOffers(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(offerService.getSellerOffers(principal.userId()));
    }

    record CreateOfferRequest(Integer offeredPriceCent, String message) {}
}
