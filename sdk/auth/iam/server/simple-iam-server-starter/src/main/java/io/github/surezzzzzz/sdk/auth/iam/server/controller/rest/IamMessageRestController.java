package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.message.response.WebMessagePageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.message.response.WebMessageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamUserDetailsSupport;
import io.github.surezzzzzz.sdk.auth.iam.server.service.MessageService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.MessageSseService;
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
import java.util.stream.Collectors;

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

    private final MessageService messageService;
    private final MessageSseService sseService;

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
            return ResponseEntity.ok(messageService.listMessages(userId).stream()
                    .map(WebMessageResponse::from)
                    .collect(Collectors.toList()));
        }
        return ResponseEntity.ok(messageService.listMessages(userId,
                        page == null ? 1 : page,
                        size == null ? 20 : size)
                .getContent().stream()
                .map(WebMessageResponse::from)
                .collect(Collectors.toList()));
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
        return ResponseEntity.ok(WebMessagePageResponse.from(messageService.listMessages(userId, page, size)));
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
        return ResponseEntity.ok(WebMessageResponse.from(messageService.markRead(userId, messageId)));
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
        sseService.pushUnreadCount(userId, emitter, messageService.countUnreadMessages(userId));
        return ResponseEntity.ok(emitter);
    }

    private Long getUserId(UserDetails userDetails) {
        return userDetails instanceof IamUserDetailsSupport
                ? ((IamUserDetailsSupport) userDetails).getUserId()
                : null;
    }
}
