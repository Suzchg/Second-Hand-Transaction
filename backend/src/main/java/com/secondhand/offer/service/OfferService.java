package com.secondhand.offer.service;

import com.secondhand.common.AppException;
import com.secondhand.offer.entity.Offer;
import com.secondhand.offer.entity.OfferStatus;
import com.secondhand.offer.repository.OfferRepository;
import com.secondhand.order.entity.Order;
import com.secondhand.order.service.OrderService;
import com.secondhand.product.entity.Product;
import com.secondhand.product.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OfferService {

    private final OfferRepository offerRepo;
    private final ProductService productService;
    private final OrderService orderService;

    public OfferService(OfferRepository offerRepo, ProductService productService,
                        OrderService orderService) {
        this.offerRepo = offerRepo;
        this.productService = productService;
        this.orderService = orderService;
    }

    /** 买家发起报价 */
    @Transactional
    public Offer createOffer(Long buyerId, Long productId, Integer offeredPriceCent, String message) {
        Product product = productService.getById(productId);
        if (product.getSellerId().equals(buyerId)) {
            throw new AppException("FORBIDDEN", "不能给自己的商品报价", HttpStatus.FORBIDDEN);
        }
        if (product.getQuantity() == null || product.getQuantity() < 1) {
            throw new AppException("CONFLICT", "商品已售罄", HttpStatus.CONFLICT);
        }
        if (offeredPriceCent == null || offeredPriceCent <= 0) {
            throw new AppException("BAD_REQUEST", "报价金额无效", HttpStatus.BAD_REQUEST);
        }

        Offer offer = new Offer();
        offer.setProductId(productId);
        offer.setBuyerId(buyerId);
        offer.setSellerId(product.getSellerId());
        offer.setOfferedPriceCent(offeredPriceCent);
        offer.setMessage(message);
        offer.setStatus(OfferStatus.PENDING);
        offer.setCreatedAt(LocalDateTime.now());
        offer.setUpdatedAt(LocalDateTime.now());
        return offerRepo.save(offer);
    }

    /** 卖家接受报价 → 创建订单 */
    @Transactional
    public Order acceptOffer(Long sellerId, Long offerId) {
        Offer offer = offerRepo.findById(offerId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "报价不存在", HttpStatus.NOT_FOUND));
        if (!offer.getSellerId().equals(sellerId)) {
            throw new AppException("FORBIDDEN", "无权操作该报价", HttpStatus.FORBIDDEN);
        }
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new AppException("CONFLICT", "报价状态不允许接受", HttpStatus.CONFLICT);
        }

        // 标记报价为已接受
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setUpdatedAt(LocalDateTime.now());

        // 以报价价格创建订单
        Product product = productService.getById(offer.getProductId());
        OrderService.CreateOrderCommand cmd = new OrderService.CreateOrderCommand(
                offer.getProductId(), null, null, null, null);
        Order order = orderService.createOrderWithPrice(offer.getBuyerId(), cmd, offer.getOfferedPriceCent());

        // 记录订单 ID 到报价上
        offer.setOrderId(order.getId());
        offerRepo.save(offer);

        return order;
    }

    /** 卖家拒绝报价 */
    @Transactional
    public Offer rejectOffer(Long sellerId, Long offerId) {
        Offer offer = offerRepo.findById(offerId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "报价不存在", HttpStatus.NOT_FOUND));
        if (!offer.getSellerId().equals(sellerId)) {
            throw new AppException("FORBIDDEN", "无权操作该报价", HttpStatus.FORBIDDEN);
        }
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new AppException("CONFLICT", "报价状态不允许拒绝", HttpStatus.CONFLICT);
        }
        offer.setStatus(OfferStatus.REJECTED);
        offer.setUpdatedAt(LocalDateTime.now());
        return offerRepo.save(offer);
    }

    /** 买家取消报价 */
    @Transactional
    public Offer cancelOffer(Long buyerId, Long offerId) {
        Offer offer = offerRepo.findById(offerId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "报价不存在", HttpStatus.NOT_FOUND));
        if (!offer.getBuyerId().equals(buyerId)) {
            throw new AppException("FORBIDDEN", "无权操作该报价", HttpStatus.FORBIDDEN);
        }
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new AppException("CONFLICT", "报价状态不允许取消", HttpStatus.CONFLICT);
        }
        offer.setStatus(OfferStatus.CANCELLED);
        offer.setUpdatedAt(LocalDateTime.now());
        return offerRepo.save(offer);
    }

    /** 商品的报价列表（卖家查看） */
    public List<Offer> getOffersForProduct(Long productId) {
        return offerRepo.findByProductIdOrderByCreatedAtDesc(productId);
    }

    /** 买家自己的报价 */
    public List<Offer> getBuyerOffers(Long buyerId) {
        return offerRepo.findByBuyerIdOrderByCreatedAtDesc(buyerId);
    }

    /** 卖家收到的报价 */
    public List<Offer> getSellerOffers(Long sellerId) {
        return offerRepo.findBySellerIdOrderByCreatedAtDesc(sellerId);
    }

    /** 商品待处理报价数 */
    public long countPendingOffers(Long productId) {
        return offerRepo.countByProductIdAndStatus(productId, OfferStatus.PENDING);
    }
}
