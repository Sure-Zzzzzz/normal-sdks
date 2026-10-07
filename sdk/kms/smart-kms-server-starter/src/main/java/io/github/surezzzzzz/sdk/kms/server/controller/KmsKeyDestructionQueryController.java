package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsAuthorizationException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsNotFoundException;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyDestructionQueryRepository;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyQueryRepository;
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
 * 密钥级销毁只读入口，与安排和取消命令分别检查读取和销毁权限。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping(SmartKmsServerConstant.API_BASE_PATH)
public class KmsKeyDestructionQueryController extends KmsHttpControllerSupport {
    /**
     * 安全的密钥级任务快照查询。
     */
    private final KmsKeyDestructionQueryRepository destructionQueryRepository;
    /**
     * 治理模式受 DATA 约束的密钥查询。
     */
    private final KmsKeyQueryRepository keyQueryRepository;

    /**
     * 创建销毁只读控制器。
     *
     * @param principalResolver          认证主体解析器
     * @param properties                 KMS 配置
     * @param destructionQueryRepository 任务明细查询
     * @param keyQueryRepository         受控归属查询
     */
    public KmsKeyDestructionQueryController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties,
                                            KmsKeyDestructionQueryRepository destructionQueryRepository,
                                            KmsKeyQueryRepository keyQueryRepository) {
        super(principalResolver, properties);
        this.destructionQueryRepository = destructionQueryRepository;
        this.keyQueryRepository = keyQueryRepository;
    }

    /**
     * 固定本人归属查询，不执行治理 DATA。
     */
    @GetMapping(value = "/me/keys/{keyRef}/destruction", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_READ)
    public ResponseEntity<KmsKeyDestructionDetailsResponse> getMy(@PathVariable String keyRef, HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_READ);
        if (!context.getPrincipal().getPrincipalId().equals(context.getPrincipal().getOwnerPrincipalId())) {
            throw new KmsAuthorizationException();
        }
        return details(context.getPrincipal().getOwnerPrincipalId(), keyRef);
    }

    /**
     * 从完整读取 DATA 范围确定目标归属后查询明细。
     */
    @GetMapping(value = "/admin/keys/{keyRef}/destruction", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_READ)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY, action = SmartKmsServerConstant.DATA_ACTION_KEY_READ)
    public ResponseEntity<KmsKeyDestructionDetailsResponse> getAdmin(@PathVariable String keyRef,
                                                                     @CurrentDataAccessPlan DataAccessPlan plan,
                                                                     HttpServletRequest request) {
        requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_READ);
        String owner = keyQueryRepository.findMetadata(KmsOwnerAccessScope.from(plan), keyRef)
                .orElseThrow(KmsNotFoundException::new).getKey().getOwnerPrincipalId();
        return details(owner, keyRef);
    }

    private ResponseEntity<KmsKeyDestructionDetailsResponse> details(String owner, String keyRef) {
        KmsKeyDestructionDetailsResponse response = KmsKeyDestructionDetailsResponse.fromDetails(
                destructionQueryRepository.findDetails(owner, keyRef).orElseThrow(KmsNotFoundException::new));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, SmartKmsServerConstant.HTTP_CACHE_CONTROL_NO_STORE).body(response);
    }
}
