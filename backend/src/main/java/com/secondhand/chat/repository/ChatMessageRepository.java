package com.secondhand.chat.repository;

import com.secondhand.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 两人在某个商品下的对话（双向） */
    @Query("SELECT m FROM ChatMessage m WHERE m.productId = :productId " +
           "AND ((m.senderId = :user1 AND m.receiverId = :user2) OR (m.senderId = :user2 AND m.receiverId = :user1)) " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessage> findConversation(@Param("productId") Long productId,
                                       @Param("user1") Long user1,
                                       @Param("user2") Long user2);

    /** 用户在某个商品下的所有消息 */
    @Query("SELECT m FROM ChatMessage m WHERE m.productId = :productId " +
           "AND (m.senderId = :userId OR m.receiverId = :userId) " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessage> findAllByProductAndUser(@Param("productId") Long productId,
                                               @Param("userId") Long userId);

    /** 用户参与的所有对话的商品 ID（去重） */
    @Query("SELECT DISTINCT m.productId FROM ChatMessage m WHERE m.senderId = :userId OR m.receiverId = :userId")
    List<Long> findDistinctProductIdsByUserId(@Param("userId") Long userId);

    /** 用户参与的所有对话（按 productId + 对方 userId 去重） */
    @Query("SELECT DISTINCT m.productId, " +
           "CASE WHEN m.senderId = :userId THEN m.receiverId ELSE m.senderId END " +
           "FROM ChatMessage m WHERE m.senderId = :userId OR m.receiverId = :userId")
    List<Object[]> findDistinctConversationPairs(@Param("userId") Long userId);

    /** 某商品下用户相关的最后一条消息 */
    @Query("SELECT m FROM ChatMessage m WHERE m.productId = :productId " +
           "AND (m.senderId = :userId OR m.receiverId = :userId) " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> findLatestByProductAndUser(@Param("productId") Long productId,
                                                  @Param("userId") Long userId);

    /** 标记已读 */
    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.productId = :productId " +
           "AND m.senderId = :senderId AND m.receiverId = :receiverId AND m.isRead = false")
    int markRead(@Param("productId") Long productId,
                 @Param("senderId") Long senderId,
                 @Param("receiverId") Long receiverId);

    /** 用户未读消息总数 */
    long countByReceiverIdAndIsReadFalse(Long receiverId);
}
