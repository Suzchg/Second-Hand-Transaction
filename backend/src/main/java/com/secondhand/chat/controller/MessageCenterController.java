package com.secondhand.chat.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.chat.service.MessageCenterService;
import com.secondhand.chat.service.MessageCenterService.CommentNotification;
import com.secondhand.chat.service.MessageCenterService.SystemMessage;
import com.secondhand.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageCenterController {

    private final MessageCenterService messageCenterService;

    public MessageCenterController(MessageCenterService messageCenterService) {
        this.messageCenterService = messageCenterService;
    }

    /** 系统消息（订单状态变更等） */
    @GetMapping("/system")
    public ApiResponse<List<SystemMessage>> systemMessages(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(messageCenterService.getSystemMessages(principal.userId()));
    }

    /** 评论通知（别人对我商品的评论） */
    @GetMapping("/comments")
    public ApiResponse<List<CommentNotification>> commentNotifications(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(messageCenterService.getCommentNotifications(principal.userId()));
    }
}
