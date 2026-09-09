package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.request.CreateMessageRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageBatchDetailResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageBatchRecipientResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageBatchSummaryResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageSendResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamUserDetailsSupport;
import io.github.surezzzzzz.sdk.auth.iam.server.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 管理台站内信域 API：发送站内信与发送批次查询
 *
 * <p>进门门为 SecurityFilterChain 管理链（Order 4），端点级由 {@code @PreAuthorize} 强制。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin")
@RequiredArgsConstructor
public class IamMessageAdminController {

    private final MessageService messageService;

    /**
     * 站内信发送批次分页
     */
    @GetMapping("/messages/page")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API + "')")
    public ResponseEntity<AdminPageResponse<MessageBatchSummaryResponse>> listMessageBatches(
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        return ResponseEntity.ok(messageService.listMessageBatches(page, size));
    }

    /**
     * 发送批次详情
     */
    @GetMapping("/messages/{sendBatchId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API + "')")
    public ResponseEntity<MessageBatchDetailResponse> getMessageBatch(@PathVariable String sendBatchId) {
        return ResponseEntity.ok(messageService.getMessageBatch(sendBatchId));
    }

    /**
     * 发送批次收件人分页
     */
    @GetMapping("/messages/{sendBatchId}/recipients")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API + "')")
    public ResponseEntity<AdminPageResponse<MessageBatchRecipientResponse>> listMessageBatchRecipients(
            @PathVariable String sendBatchId,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        return ResponseEntity.ok(messageService.listMessageBatchRecipients(sendBatchId, page, size));
    }

    /**
     * 发送站内信
     */
    @PostMapping("/messages")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API + "')")
    public ResponseEntity<MessageSendResponse> createMessage(@RequestBody CreateMessageRequest request,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        IamUserDetailsSupport iamUserDetails = requireIamUserDetails(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendMessage(
                request, iamUserDetails.getUserId(), iamUserDetails.getUsername()));
    }

    private IamUserDetailsSupport requireIamUserDetails(UserDetails userDetails) {
        if (userDetails instanceof IamUserDetailsSupport) {
            return (IamUserDetailsSupport) userDetails;
        }
        throw new AccessDeniedException(ServerErrorMessage.PERMISSION_DENIED);
    }
}
