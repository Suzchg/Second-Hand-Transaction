package com.secondhand.comment.repository;

import com.secondhand.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByProductIdOrderByCreatedAtAsc(Long productId);

    List<Comment> findByProductIdInAndUserIdNotOrderByCreatedAtDesc(List<Long> productIds, Long userId);
}
