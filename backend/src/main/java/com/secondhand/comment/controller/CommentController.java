package com.secondhand.comment.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.comment.entity.Comment;
import com.secondhand.comment.service.CommentService;
import com.secondhand.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /** 获取商品评论列表 */
    @GetMapping("/api/products/{productId}/comments")
    public ApiResponse<List<Comment>> list(@PathVariable Long productId) {
        return ApiResponse.ok(commentService.getComments(productId));
    }

    /** 发表评论 */
    @PostMapping("/api/products/{productId}/comments")
    public ApiResponse<Comment> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody AddCommentRequest req) {
        return ApiResponse.ok(commentService.addComment(principal.userId(), productId, req.content()));
    }

    record AddCommentRequest(@NotBlank String content) {}
}
