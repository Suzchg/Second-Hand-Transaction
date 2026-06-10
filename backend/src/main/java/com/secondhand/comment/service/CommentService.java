package com.secondhand.comment.service;

import com.secondhand.comment.entity.Comment;
import com.secondhand.comment.repository.CommentRepository;
import com.secondhand.common.AppException;
import com.secondhand.product.entity.Product;
import com.secondhand.product.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepo;
    private final ProductService productService;

    public CommentService(CommentRepository commentRepo, ProductService productService) {
        this.commentRepo = commentRepo;
        this.productService = productService;
    }

    @Transactional
    public Comment addComment(Long userId, Long productId, String content) {
        Product product = productService.getById(productId);
        if (product.getSellerId().equals(userId)) {
            throw new AppException("FORBIDDEN", "不能评论自己的商品", HttpStatus.FORBIDDEN);
        }
        Comment c = new Comment();
        c.setUserId(userId);
        c.setProductId(productId);
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        return commentRepo.save(c);
    }

    @Transactional(readOnly = true)
    public List<Comment> getComments(Long productId) {
        return commentRepo.findByProductIdOrderByCreatedAtAsc(productId);
    }
}
