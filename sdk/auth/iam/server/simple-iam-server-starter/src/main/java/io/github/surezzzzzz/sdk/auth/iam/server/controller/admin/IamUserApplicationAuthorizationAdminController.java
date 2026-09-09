package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.PutApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.ApplicationAuthorizationDetailResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.ApplicationAuthorizationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamApplicationAuthorizationAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IAM 管理域用户应用授权管理 API
 *
 * <p>应用授权是用户在可信应用下的授权投影（roles / PAGE / API / DATA 四类内容
 * + 准入 + 版本），由 token 发放与资源令牌验证实时消费。管理面变更立即生效：
 * 撤销 / 收紧后旧令牌的下一次验证即拒绝或降级。PUT 为全量替换 upsert
 * （撤销后再次 PUT 即重激活）；authorizationVersion 由服务端单调递增。
 *
 * <p>权限：需要 ROLE_iam_admin + iam:user:api（SecurityFilterChain + 方法级鉴权）。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin/users/{userId}/application-authorizations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
public class IamUserApplicationAuthorizationAdminController {

    private final IamApplicationAuthorizationAdminService authorizationAdminService;

    /**
     * 列出用户全部应用授权摘要。
     */
    @GetMapping
    public ResponseEntity<List<ApplicationAuthorizationResponse>> listAuthorizations(
            @PathVariable Long userId) {
        return ResponseEntity.ok(authorizationAdminService.listAuthorizations(userId));
    }

    /**
     * 查询单条应用授权详情（含四类授权内容投影）。
     */
    @GetMapping("/{applicationId}")
    public ResponseEntity<ApplicationAuthorizationDetailResponse> getAuthorization(
            @PathVariable Long userId, @PathVariable Long applicationId) {
        return ResponseEntity.ok(authorizationAdminService.getAuthorization(userId, applicationId));
    }

    /**
     * 全量替换 / 创建用户应用授权（幂等重放安全，返回最新版本详情）。
     */
    @PutMapping("/{applicationId}")
    public ResponseEntity<ApplicationAuthorizationDetailResponse> putAuthorization(
            @PathVariable Long userId,
            @PathVariable Long applicationId,
            @RequestBody PutApplicationAuthorizationRequest request) {
        ApplicationAuthorizationDetailResponse response =
                authorizationAdminService.putAuthorization(userId, applicationId, request);
        // version 从 1 单调递增且记录不物理删除，version=1 即首次创建
        boolean created = response.getAuthorizationVersion() != null
                && response.getAuthorizationVersion() == 1L;
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    /**
     * 撤销用户应用授权（语义撤销留痕，验证立即失效；重复撤销幂等返回 204）。
     */
    @DeleteMapping("/{applicationId}")
    public ResponseEntity<Void> revokeAuthorization(
            @PathVariable Long userId, @PathVariable Long applicationId) {
        authorizationAdminService.revokeAuthorization(userId, applicationId);
        return ResponseEntity.noContent().build();
    }
}
