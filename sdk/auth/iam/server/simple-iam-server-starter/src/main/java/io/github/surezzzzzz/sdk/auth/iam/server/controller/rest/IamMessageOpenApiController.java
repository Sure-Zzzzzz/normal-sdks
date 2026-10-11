package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.request.CreateMessageRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.IamOpenApiMessageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageSendResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.message.IamMessageEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.message.IamMessageService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 开放 API 站内信族（{@code /iam/api/messages}，1.3.7）。
 *
 * <p>供外部系统（工单/反馈/审批）给 IAM 用户发通知与对账：发信（批量收件人形态与
 * 管理台同构）、按收件人查信、发送批次详情。外部只发不删——删除与已读是用户/管理台职能。
 * 纯码控无 DATA 面（对齐管理台同族口径：收件人过滤是查询参数非行级范围语义）；
 * 发信者记录为外部主体标识（aksk:{clientId}），senderUserId 留空。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/api/messages")
@RequiredArgsConstructor
public class IamMessageOpenApiController {

    private final IamMessageService messageService;
    private final IamUserService userService;

    /**
     * 发送站内信（收件人主体数组/部门/协作组并集，与管理台同构；201 返回批次标识）。
     */
    @PostMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API)
    public ResponseEntity<MessageSendResponse> sendMessage(@RequestBody CreateMessageRequest request) {
        MessageSendResponse response = messageService.sendMessage(request, null, resolveOperator());
        log.debug("开放 API 发信：operator={}, batchId={}, 命中 {} 收件人",
                resolveOperator(), response.getSendBatchId(), response.getRecipientCount());
        return ResponseEntity.status(201).body(response);
    }

    /**
     * 按收件人查信（subjectId 必填——防全库遍历；分页）。
     */
    @GetMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API)
    public ResponseEntity<List<IamOpenApiMessageResponse>> listBySubject(
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        IamUserEntity user = userService.getBySubjectId(subjectId);
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Page<IamMessageEntity> messages = messageService.listMessages(user.getId(),
                Math.max(page, 1), safeSize);
        // yaml 契约声明数组（集合资源），Spring Page 只作内部分页载体不外泄包装
        return ResponseEntity.ok(messages.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    /**
     * 发送批次详情（对账：批次元数据；收件人明细分页见批次收件人族）。
     */
    @GetMapping("/{sendBatchId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API)
    public ResponseEntity<io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageBatchDetailResponse> getBatch(
            @PathVariable String sendBatchId) {
        return ResponseEntity.ok(messageService.getMessageBatch(sendBatchId));
    }

    private IamOpenApiMessageResponse toResponse(IamMessageEntity message) {
        return new IamOpenApiMessageResponse(message.getTitle(), message.getContent(),
                message.getCreatedAt(), message.getReadAt() != null);
    }

    private String resolveOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof
                io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext) {
            io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal principal =
                    ((io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext)
                            authentication.getPrincipal()).getPrincipal();
            return principal.getSourceId().getValue() + ":" + principal.getSubjectId();
        }
        return authentication == null ? "unknown" : authentication.getName();
    }
}
