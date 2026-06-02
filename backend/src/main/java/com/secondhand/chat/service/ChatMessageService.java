package com.secondhand.chat.service;

import com.secondhand.chat.entity.ChatMessage;
import com.secondhand.chat.repository.ChatMessageRepository;
import com.secondhand.product.entity.Product;
import com.secondhand.product.service.ProductService;
import com.secondhand.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatMessageService {

    private final ChatMessageRepository chatRepo;
    private final ProductService productService;
    private final UserService userService;

    public ChatMessageService(ChatMessageRepository chatRepo,
                              ProductService productService,
                              UserService userService) {
        this.chatRepo = chatRepo;
        this.productService = productService;
        this.userService = userService;
    }

    @Transactional
    public ChatMessage send(Long productId, Long senderId, Long receiverId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setProductId(productId);
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        msg.setIsRead(false);
        msg.setCreatedAt(LocalDateTime.now());
        return chatRepo.save(msg);
    }

    /** 获取用户在商品下的对话。userId2 为 null 时返回所有对方的消息 */
    @Transactional(readOnly = true)
    public List<ChatMessage> getConversation(Long productId, Long userId1, Long userId2) {
        if (userId2 == null) {
            return chatRepo.findAllByProductAndUser(productId, userId1);
        }
        return chatRepo.findConversation(productId, userId1, userId2);
    }

    /** 获取对话摘要列表（消息中心用） */
    @Transactional(readOnly = true)
    public List<ConversationSummary> getConversationList(Long userId) {
        List<Long> productIds = chatRepo.findDistinctProductIdsByUserId(userId);
        List<ConversationSummary> result = new ArrayList<>();

        for (Long productId : productIds) {
            Product product;
            try {
                product = productService.getById(productId);
            } catch (Exception e) {
                continue; // 商品可能已被删除
            }

            // 确定对话对方
            Long otherUserId = product.getSellerId().equals(userId)
                    ? null : product.getSellerId();
            // 如果是卖家，取第一个买家
            if (otherUserId == null) {
                List<ChatMessage> msgs = chatRepo.findLatestByProductAndUser(productId, userId);
                if (!msgs.isEmpty()) {
                    ChatMessage first = msgs.get(msgs.size() - 1); // oldest
                    otherUserId = first.getSenderId().equals(userId)
                            ? first.getReceiverId() : first.getSenderId();
                }
            }

            // 最后一条消息
            List<ChatMessage> latest = chatRepo.findLatestByProductAndUser(productId, userId);
            ChatMessage lastMsg = latest.isEmpty() ? null : latest.get(0);

            // 未读数
            long unread = latest.stream()
                    .filter(m -> m.getReceiverId().equals(userId) && !m.getIsRead())
                    .count();

            UserService.PublicUserDto otherUser = null;
            if (otherUserId != null) {
                try {
                    otherUser = userService.getPublicInfo(otherUserId);
                } catch (Exception ignored) {}
            }

            result.add(new ConversationSummary(
                    productId, product.getTitle(),
                    otherUserId, otherUser != null ? otherUser.nickname() : "未知",
                    otherUser != null ? otherUser.avatarUrl() : null,
                    lastMsg != null ? lastMsg.getContent() : "",
                    lastMsg != null ? lastMsg.getCreatedAt() : null,
                    unread
            ));
        }

        // 按最新消息时间倒序
        result.sort((a, b) -> {
            if (a.lastMessageTime() == null) return 1;
            if (b.lastMessageTime() == null) return -1;
            return b.lastMessageTime().compareTo(a.lastMessageTime());
        });
        return result;
    }

    /** 标记已读 */
    @Transactional
    public void markRead(Long productId, Long readerId, Long otherUserId) {
        chatRepo.markRead(productId, otherUserId, readerId);
    }

    public record ConversationSummary(
            Long productId, String productTitle,
            Long otherUserId, String otherUserName, String otherUserAvatar,
            String lastMessage, LocalDateTime lastMessageTime,
            long unreadCount) {}
}
