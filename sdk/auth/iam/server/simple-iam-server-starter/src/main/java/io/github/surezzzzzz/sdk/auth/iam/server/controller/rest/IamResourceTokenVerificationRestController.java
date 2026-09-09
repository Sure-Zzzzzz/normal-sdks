package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.request.ResourceTokenVerificationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.response.ResourceTokenVerificationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamResourceVerificationClientEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamResourceTokenVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * IAM 资源令牌受控验证接口。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/resource/tokens")
@RequiredArgsConstructor
public class IamResourceTokenVerificationRestController {

    private final IamResourceTokenVerificationService verificationService;

    /**
     * 由已认证的资源验证客户端验证 IAM access token。
     */
    @PostMapping("/verify")
    public ResponseEntity<ResourceTokenVerificationResponse> verify(
            @RequestBody ResourceTokenVerificationRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null
                && authentication.getPrincipal() instanceof IamResourceVerificationClientEntity)) {
            return unauthorized();
        }
        ResourceTokenVerificationResponse response = verificationService.verify(
                (IamResourceVerificationClientEntity) authentication.getPrincipal(),
                request == null ? null : request.getToken());
        return response == null ? unauthorized() : ResponseEntity.ok(response);
    }

    private ResponseEntity<ResourceTokenVerificationResponse> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"iam-resource-verification\"")
                .build();
    }
}
