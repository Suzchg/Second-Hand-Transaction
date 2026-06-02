package com.secondhand.comment.service;

import com.secondhand.comment.entity.Comment;
import com.secondhand.comment.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepo;

    public CommentService(CommentRepository commentRepo) {
        this.commentRepo = commentRepo;
    }

    @Transactional
    public Comment addComment(Long userId, Long productId, String content) {
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
