package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response.AdminSessionResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.dashboard.IamAdminSessionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 开放 API 会话族（{@code /iam/api/sessions}，1.3.7）。
 *
 * <p>风控/安全审计视角：按用户列在线会话、踢指定会话。subjectId 必填（防全库遍历）；
 * 不开放全库会话分页。纯码控无 DATA 面（会话是运行时状态非行级业务数据，对齐管理台）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/api/sessions")
@RequiredArgsConstructor
public class IamSessionOpenApiController {

    private final IamAdminSessionService adminSessionService;
    private final IamSessionService sessionService;
    private final IamUserService userService;

    /**
     * 按用户列在线会话（分页）。
     */
    @GetMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_SESSION_API)
    public ResponseEntity<java.util.List<AdminSessionResponse>> listBySubject(
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        IamUserEntity user = userService.getBySubjectId(subjectId);
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Page<AdminSessionResponse> sessions = adminSessionService.listActiveSessions(user.getId(),
                Math.max(page, 1), safeSize);
        log.debug("开放 API 会话查询：subjectId={}, 命中 {} 条", subjectId, sessions.getTotalElements());
        // yaml 契约声明数组（集合资源），Page 只作内部分页载体不外泄包装
        return ResponseEntity.ok(sessions.getContent());
    }

    /**
     * 踢指定会话（204；风控强制下线，会话失效即时生效）。
     */
    @DeleteMapping("/{sessionId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_SESSION_API)
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        sessionService.revokeSession(sessionId);
        log.info("开放 API 踢会话：sessionId={}", sessionId);
        return ResponseEntity.noContent().build();
    }
}
