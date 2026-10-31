package io.github.surezzzzzz.sdk.license.server.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.constant.SigningOperationConstant;
import io.github.surezzzzzz.sdk.license.server.service.CredentialIssuanceService;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import javax.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签发任务 HTTP 面（协作任务节面后端）：任务列表/详情/受控恢复/安全放弃。
 *
 * <p>恢复认领后同步执行原冻结输入的签名，UNCERTAIN→SIGNING时执行代数递增，
 * 旧工作者不能提交；放弃走同一CAS围栏，READY永不退回。任务按授权范围收窄。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@RestController
@RequestMapping("/api/license/v1")
public class LicenseOperationController {

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final SigningOperationRepository operations;
    private final CredentialIssuanceService issuance;

    public LicenseOperationController(SigningOperationRepository operations, CredentialIssuanceService issuance) {
        this.operations = operations;
        this.issuance = issuance;
    }

    @RequireApiPermission(LicenseApi.OPERATION_READ)
    @GetMapping("/operations")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "read")
    public ResponseEntity<String> list(HttpServletRequest request,
                                       @CurrentDataAccessPlan DataAccessPlan plan,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(value = "grantId", required = false) String grantId) {
        require(request, LicenseApi.OPERATION_READ);
        if (page < 1 || size < 1 || size > SigningOperationConstant.MAX_PAGE_SIZE) {
            throw LicenseHttpException.badRequest(SigningOperationConstant.INVALID_PAGE);
        }
        LicenseAccessScope scope = LicenseAccessScope.from(plan, "grantId");
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, SigningOperationConstant.OPERATION_ID));
        Page<SigningOperationEntity> result = operations.findCredentials(scope.restrictedIdsOrNull(),
                grantId, null, pageable);
        ObjectNode body = StrictJson.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        result.getContent().forEach(item -> data.add(view(item)));
        body.put("total", result.getTotalElements());
        body.put("page", result.getNumber() + 1);
        body.put("size", result.getSize());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body.toString());
    }

    @RequireApiPermission(LicenseApi.OPERATION_READ)
    @GetMapping("/operations/{operationId}")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "read")
    public ResponseEntity<String> get(@PathVariable String operationId,
                                      @CurrentDataAccessPlan DataAccessPlan plan,
                                      HttpServletRequest request) {
        require(request, LicenseApi.OPERATION_READ);
        SigningOperationEntity entity = find(operationId);
        LicenseAccessScope.from(plan, "grantId").requireVisible(entity.getGrantId());
        return ResponseEntity.ok(view(entity).toString());
    }

    /** 受控恢复：UNCERTAIN保留原请求；失联SIGNING额外要求原代数、接管理由和宿主窗口。 */
    @RequireApiPermission(LicenseApi.OPERATION_RECOVER)
    @PostMapping("/operations/{operationId}/recoveries")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    public ResponseEntity<String> recover(@PathVariable String operationId, @RequestBody String body,
                                         @CurrentDataAccessPlan DataAccessPlan plan,
                                         HttpServletRequest request) {
        require(request, LicenseApi.OPERATION_RECOVER);
        ObjectNode input = support.object(body, "expectedRowVersion", "expectedExecutionGeneration", "reasonCategory");
        SigningOperationEntity entity = find(operationId);
        LicenseAccessScope.from(plan, "grantId").requireVisible(entity.getGrantId());
        try {
            issuance.recoverAndCommit(operationId, expectedVersion(input), expectedGeneration(input),
                    support.text(input, "reasonCategory", false));
        } catch (LicenseHttpException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw LicenseHttpException.dependencyUnavailable(SigningOperationConstant.EXECUTION_UNAVAILABLE);
        }
        return ResponseEntity.ok(view(find(operationId)).toString());
    }

    /** 安全放弃：状态机合法迁移（READY 拒绝）；不回收历史有效文件占用。 */
    @RequireApiPermission(LicenseApi.OPERATION_RECOVER)
    @PostMapping("/operations/{operationId}/abandonments")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    public ResponseEntity<String> abandon(@PathVariable String operationId, @RequestBody String body,
                                         @CurrentDataAccessPlan DataAccessPlan plan,
                                         HttpServletRequest request) {
        require(request, LicenseApi.OPERATION_RECOVER);
        ObjectNode input = support.object(body, "expectedRowVersion", "reasonCategory");
        SigningOperationEntity entity = find(operationId);
        LicenseAccessScope.from(plan, "grantId").requireVisible(entity.getGrantId());
        issuance.abandon(operationId, expectedVersion(input), support.text(input, "reasonCategory", false));
        return ResponseEntity.ok(view(find(operationId)).toString());
    }

    private long expectedVersion(ObjectNode input) {
        com.fasterxml.jackson.databind.JsonNode value = input.get("expectedRowVersion");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw LicenseHttpException.unprocessable(SigningOperationConstant.INVALID_EXPECTED_VERSION);
        }
        return value.longValue();
    }

    /** 执行代数为可选兼容字段；显式提供时不能null、字符串、小数或溢出。 */
    private Long expectedGeneration(ObjectNode input) {
        com.fasterxml.jackson.databind.JsonNode value = input.get("expectedExecutionGeneration");
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1) {
            throw LicenseHttpException.unprocessable(SigningOperationConstant.INVALID_EXPECTED_GENERATION);
        }
        return value.longValue();
    }

    private SigningOperationEntity find(String operationId) {
        return operations.findById(operationId).orElseThrow(LicenseHttpException::notFound);
    }

    private LicenseRequestContext require(HttpServletRequest request, String permission) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return support.requireApiPermission(context, permission);
    }

    private static ObjectNode view(SigningOperationEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("operationId", entity.getOperationId());
        view.put("credentialId", entity.getCredentialId());
        view.put("grantId", entity.getGrantId());
        view.put("state", entity.getState());
        view.put("executionGeneration", entity.getExecutionGeneration());
        view.put("rowVersion", entity.getRowVersion());
        if (entity.getLastKmsCallAt() == null) {
            view.putNull("lastKmsCallAt");
        } else {
            view.put("lastKmsCallAt", entity.getLastKmsCallAt().toString());
        }
        if (entity.getReasonCategory() != null) {
            view.put("reasonCategory", entity.getReasonCategory());
        } else {
            view.putNull("reasonCategory");
        }
        if ("READY".equals(entity.getState()) && entity.getCommittedSignature() != null) {
            view.put("resultResource", "/api/license/v1/credentials/"
                    + entity.getCredentialId() + "/content");
        } else {
            view.putNull("resultResource");
        }
        return view;
    }
}
