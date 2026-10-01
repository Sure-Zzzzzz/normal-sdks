package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.message.response.WebMessagePageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.message.response.WebMessageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.message.IamMessageService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.message.IamMessageSseService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamUserDetails;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;

/**
 * IAM Web 站内信 API
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/web/messages")
@RequiredArgsConstructor
public class IamMessageRestController {

    private final IamMessageService messageService;
    private final io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository userRepository;
    private final IamMessageSseService sseService;

    /**
     * 站内信列表
     */
    @GetMapping
    public ResponseEntity<List<WebMessageResponse>> listMessages(@AuthenticationPrincipal UserDetails userDetails,
                                                                 @RequestParam(required = false) Integer page,
                                                                 @RequestParam(required = false) Integer size) {
        Long userId = getUserId(userDetails);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (page == null && size == null) {
            return ResponseEntity.ok(toResponses(messageService.listMessages(userId)));
        }
        return ResponseEntity.ok(toResponses(messageService.listMessages(userId,
                page == null ? 1 : page, size == null ? 20 : size).getContent()));
    }

    /**
     * 站内信分页
     */
    @GetMapping("/page")
    public ResponseEntity<WebMessagePageResponse> listMessagePage(@AuthenticationPrincipal UserDetails userDetails,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        Long userId = getUserId(userDetails);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        org.springframework.data.domain.Page<io.github.surezzzzzz.sdk.auth.iam.server.entity.message.IamMessageEntity> messagePage =
                messageService.listMessages(userId, page, size);
        return ResponseEntity.ok(WebMessagePageResponse.from(messagePage, subjectIdMap(messagePage.getContent())));
    }

    /**
     * 未读数
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Long> countUnreadMessages(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(messageService.countUnreadMessages(userId));
    }

    /**
     * 全部已读
     */
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        messageService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 单条已读
     */
    @PutMapping("/{messageId}/read")
    public ResponseEntity<WebMessageResponse> markRead(@AuthenticationPrincipal UserDetails userDetails,
                                                       @PathVariable Long messageId) {
        Long userId = getUserId(userDetails);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(WebMessageResponse.from(messageService.markRead(userId, messageId),
                subjectIdMap(java.util.Collections.singletonList(messageService.markRead(userId, messageId)))));
    }

    /**
     * SSE 未读数推送通道
     *
     * <p>建立连接后立即推送一次当前未读数；后续站内信创建 / 标记已读时由服务端主动推送。
     * 前端使用 EventSource 订阅，事件名为 {@code unread-count}。
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> messageEvents(@AuthenticationPrincipal UserDetails userDetails,
                                                    HttpServletRequest request) {
        Long userId = getUserId(userDetails);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        HttpSession servletSession = request.getSession(false);
        String servletSessionIdHash = servletSession == null
                ? null : TokenHashHelper.sha256Hex(servletSession.getId());
        SseEmitter emitter = sseService.register(userId, servletSessionIdHash);
        // SSE 请求永不结束：未读数查询若在请求线程触碰 JPA，OSIV 会话绑定的连接
        // 会随 emitter 终身占用（连接池泄漏），首帧必须由后台线程查询并推送
        sseService.pushInitialUnreadCount(userId, emitter, () -> messageService.countUnreadMessages(userId));
        return ResponseEntity.ok(emitter);
    }

    private java.util.Map<Long, String> subjectIdMap(java.util.List<io.github.surezzzzzz.sdk.auth.iam.server.entity.message.IamMessageEntity> messages) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (io.github.surezzzzzz.sdk.auth.iam.server.entity.message.IamMessageEntity m : messages) {
            ids.add(m.getRecipientUserId());
            ids.add(m.getSenderUserId());
        }
        return userRepository.findAllById(ids).stream()
                .filter(user -> user.getSubjectId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity::getId,
                        io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity::getSubjectId, (a, b) -> a));
    }

    private java.util.List<WebMessageResponse> toResponses(java.util.List<io.github.surezzzzzz.sdk.auth.iam.server.entity.message.IamMessageEntity> messages) {
        java.util.Map<Long, String> subjectIds = subjectIdMap(messages);
        java.util.List<WebMessageResponse> out = new java.util.ArrayList<>();
        for (io.github.surezzzzzz.sdk.auth.iam.server.entity.message.IamMessageEntity m : messages) {
            out.add(WebMessageResponse.from(m, subjectIds));
        }
        return out;
    }

    private Long getUserId(UserDetails userDetails) {
        return userDetails instanceof IamUserDetails
                ? ((IamUserDetails) userDetails).getUserId()
                : null;
    }
}
