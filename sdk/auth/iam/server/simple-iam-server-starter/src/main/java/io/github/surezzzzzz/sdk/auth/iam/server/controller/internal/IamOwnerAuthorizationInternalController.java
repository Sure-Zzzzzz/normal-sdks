package io.github.surezzzzzz.sdk.auth.iam.server.controller.internal;

import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationCandidate;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationChangePage;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationReadResult;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.request.OwnerAuthorizationCandidateRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.request.OwnerAuthorizationChangePullRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.request.OwnerAuthorizationResolveRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamOwnerAuthorizationChangeLogPullService;
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
 * IAM 对外凭证服务的内部 owner 授权读取接口（协作契约端点，路径即约定）。
 *
 * <p>认证由独立无状态 reader SecurityFilterChain 完成；本控制器不接受浏览器会话或 Basic 认证。</p>
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/internal/owner-authorization")
@RequiredArgsConstructor
public class IamOwnerAuthorizationInternalController {

    private final IamOwnerAuthorizationReaderService readerService;
    private final IamOwnerAuthorizationChangeLogPullService changePullService;

    @PostMapping("/resolve")
    public ResponseEntity<OwnerAuthorizationReadResult> resolve(
            @Valid @RequestBody OwnerAuthorizationResolveRequest request) {
        OwnerAuthorizationReadResult response = readerService.resolve(request.getOwnerSourceId(),
                request.getOwnerSubjectId(), request.getTargetApplicationId());
        return response == null
                ? ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build()
                : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/candidate-applications")
    public ResponseEntity<java.util.List<OwnerAuthorizationCandidate>> candidates(
            @Valid @RequestBody OwnerAuthorizationCandidateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                readerService.listCandidates(request.getOwnerSourceId(), request.getOwnerSubjectId()));
    }

    @PostMapping("/changes/pull")
    public ResponseEntity<OwnerAuthorizationChangePage> pullChanges(
            @Valid @RequestBody OwnerAuthorizationChangePullRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                changePullService.pull(request.getAfterSequence(), request.getPageSize()));
    }
}
