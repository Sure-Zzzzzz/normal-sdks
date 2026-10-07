package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsAuthorizationException;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.service.KmsMyPublicKeyService;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 受角色 API 权限约束的人员本人公钥入口，不消费调用方使用策略。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping(SmartKmsServerConstant.API_BASE_PATH + "/me/keys")
public class KmsMyPublicKeyController extends KmsHttpControllerSupport {
    /**
     * 可替换的本人公钥服务。
     */
    private final KmsMyPublicKeyService publicKeyService;

    /**
     * 创建人员本人公钥控制器。
     *
     * @param principalResolver 可信认证主体解析器
     * @param properties        KMS 配置
     * @param publicKeyService  本人公钥服务
     */
    public KmsMyPublicKeyController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties,
                                    KmsMyPublicKeyService publicKeyService) {
        super(principalResolver, properties);
        this.publicKeyService = publicKeyService;
    }

    /**
     * 读取全部合法可分发本人公钥；人员与归属由服务再次复核。
     */
    @GetMapping(value = "/{keyRef}/public-keys", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY)
    public ResponseEntity<KmsPublicKeyListResponse> list(@PathVariable String keyRef, HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY);
        if (!context.isVerifiedHumanSubject()
                || !context.getPrincipal().getPrincipalId().equals(context.getPrincipal().getOwnerPrincipalId())) {
            throw new KmsAuthorizationException();
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, SmartKmsServerConstant.HTTP_CACHE_CONTROL_NO_STORE)
                .body(new KmsPublicKeyListResponse(publicKeyService.list(context, keyRef)));
    }
}
