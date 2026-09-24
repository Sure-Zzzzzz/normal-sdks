package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationCleanupOperationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 可信应用异步删除操作查询与重试 API。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin/trusted-application-cleanup-operations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_API + "')")
public class IamTrustedApplicationCleanupOperationAdminController {

    private final IamTrustedApplicationLifecycleService lifecycleService;

    @GetMapping("/{operationId}")
    public ResponseEntity<TrustedApplicationCleanupOperationResponse> getOperation(
            @PathVariable Long operationId) {
        return ResponseEntity.ok(lifecycleService.getOperation(operationId));
    }

    @PostMapping("/{operationId}/retry")
    public ResponseEntity<TrustedApplicationCleanupOperationResponse> retryOperation(
            @PathVariable Long operationId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(lifecycleService.retryOperation(operationId));
    }
}
