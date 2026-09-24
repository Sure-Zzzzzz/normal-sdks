package io.github.surezzzzzz.sdk.auth.iam.server.controller.internal;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.request.OwnerAuthorizationCandidateRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.request.OwnerAuthorizationChangePullRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.request.OwnerAuthorizationResolveRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response.OwnerAuthorizationCandidateResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response.OwnerAuthorizationChangePullResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response.OwnerAuthorizationResolveResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamAkskAuthorizationChangePullService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamOwnerAuthorizationReaderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * IAM 到 AKSK 的内部 owner 授权读取接口。
 *
 * <p>认证由独立无状态 reader SecurityFilterChain 完成；本控制器不接受浏览器会话或 Basic 认证。</p>
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/internal/aksk/owner-authorizations")
@RequiredArgsConstructor
public class IamOwnerAuthorizationInternalController {

    private final IamOwnerAuthorizationReaderService readerService;
    private final IamAkskAuthorizationChangePullService changePullService;

    @PostMapping("/resolve")
    public ResponseEntity<OwnerAuthorizationResolveResponse> resolve(
            @Valid @RequestBody OwnerAuthorizationResolveRequest request) {
        OwnerAuthorizationResolveResponse response = readerService.resolve(request.getOwnerSourceId(),
                request.getOwnerSubjectId(), request.getTargetApplicationId());
        return response == null
                ? ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build()
                : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/candidate-applications")
    public ResponseEntity<OwnerAuthorizationCandidateResponse> candidates(
            @Valid @RequestBody OwnerAuthorizationCandidateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                readerService.listCandidates(request.getOwnerSourceId(), request.getOwnerSubjectId()));
    }

    @PostMapping("/changes/pull")
    public ResponseEntity<OwnerAuthorizationChangePullResponse> pullChanges(
            @Valid @RequestBody OwnerAuthorizationChangePullRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                changePullService.pull(request.getAfterSequence(), request.getPageSize()));
    }
}
