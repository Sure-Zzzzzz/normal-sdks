package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.exception.KmsUnauthenticatedException;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Map;

/**
 * KMS 门户主体自省 REST 控制器。
 *
 * <p>主体和 KMS API 权限由已注册的解析器读取，页面权限只从已验证的中立授权快照读取。
 * 该端点不返回归属范围或任何 IAM 内部用户字段，页面数据范围仍必须由每个业务接口的
 * DataPlan 单独执行。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RestController
@RequestMapping(SmartKmsServerConstant.API_BASE_PATH + "/me")
public class KmsMeController extends KmsHttpControllerSupport {

    private final KmsPrincipalResolver principalResolver;

    /**
     * 创建门户主体自省控制器。
     */
    public KmsMeController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties) {
        super(principalResolver, properties);
        this.principalResolver = principalResolver;
    }

    /**
     * 返回当前已认证主体可见的 KMS 页面权限。
     */
    @GetMapping(produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_ME_READ)
    public ResponseEntity<String> me(HttpServletRequest request) {
        KmsRequestContext requestContext = principalResolver.resolve(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object authenticationPrincipal = authentication == null ? null : authentication.getPrincipal();
        if (requestContext == null || !(authenticationPrincipal instanceof VerifiedResourceContext)) {
            throw new KmsUnauthenticatedException();
        }
        requireApiPermission(requestContext, SmartKmsServerConstant.API_PERMISSION_ME_READ);
        VerifiedResourceContext context = (VerifiedResourceContext) authenticationPrincipal;
        Map<String, Object> response = map();
        response.put("principalId", requestContext.getPrincipal().getPrincipalId());
        response.put("subjectType", context.getPrincipal().getSubjectType().getCode());
        response.put("scopes", new ArrayList<String>(requestContext.getPrincipal().getScopes()));
        response.put("pagePermissions", new ArrayList<String>(context.getApplicationAuthorization().getPagePermissions()));
        log.info("KMS 门户主体自省成功 requestId={} principalId={} pagePermissions={}",
                requestContext.getRequestId(), requestContext.getPrincipal().getPrincipalId(),
                context.getApplicationAuthorization().getPagePermissions().size());
        return json(200, response);
    }
}
