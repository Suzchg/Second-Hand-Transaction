package com.secondhand.auth.repository;

import com.secondhand.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Page<User> findByNicknameContainingIgnoreCase(String nickname, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime after);

    boolean existsByRole(com.secondhand.auth.entity.Role role);

    java.util.Optional<User> findFirstByOrderByIdAsc();
}
