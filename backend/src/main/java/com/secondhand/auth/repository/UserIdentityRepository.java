package com.secondhand.auth.repository;

import com.secondhand.auth.entity.IdentityType;
import com.secondhand.auth.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户标识数据访问层。
 */
@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    /** 检查某个标识是否已被注册 */
    boolean existsByIdentityTypeAndIdentifier(IdentityType type, String identifier);

    /** 按类型和标识值查找 */
    Optional<UserIdentity> findByIdentityTypeAndIdentifier(IdentityType type, String identifier);
}
