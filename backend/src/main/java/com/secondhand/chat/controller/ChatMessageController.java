package com.secondhand.chat.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.chat.entity.ChatMessage;
import com.secondhand.chat.service.ChatMessageService;
import com.secondhand.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ChatMessageController {

    private final ChatMessageService chatService;

    public ChatMessageController(ChatMessageService chatService) {
        this.chatService = chatService;
    }

    /** 发送私聊消息（receiverId 必填，调用方明确知道发给谁） */
    @PostMapping("/api/products/{productId}/chat")
    public ApiResponse<ChatMessage> send(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody SendRequest req) {
        return ApiResponse.ok(chatService.send(productId, principal.userId(), req.receiverId(), req.content()));
    }

    /** 获取当前用户与指定用户在该商品下的对话 */
    @GetMapping("/api/products/{productId}/chat")
    public ApiResponse<List<ChatMessage>> getConversation(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId,
            @RequestParam("with") Long otherUserId) {
        return ApiResponse.ok(chatService.getConversation(productId, principal.userId(), otherUserId));
    }

    /** 消息中心：获取所有对话摘要 */
    @GetMapping("/api/users/messages")
    public ApiResponse<List<ChatMessageService.ConversationSummary>> conversationList(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(chatService.getConversationList(principal.userId()));
    }

    /** 标记消息已读 */
    @PutMapping("/api/messages/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ReadRequest req) {
        chatService.markRead(req.productId(), principal.userId(), req.otherUserId());
        return ApiResponse.ok();
    }

    record SendRequest(@NotNull Long receiverId, @NotBlank String content) {}
    record ReadRequest(@NotNull Long productId, @NotNull Long otherUserId) {}
}
