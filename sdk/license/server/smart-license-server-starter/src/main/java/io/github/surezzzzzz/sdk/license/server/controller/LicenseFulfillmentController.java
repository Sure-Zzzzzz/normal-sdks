package io.github.surezzzzzz.sdk.license.server.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.integration.FulfillmentCoordinationService;
import io.github.surezzzzzz.sdk.license.server.constant.LicenseValidationConstant;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRM 履约同步触发面（消费范式：拉取+回写的执行入口；定时调度属宿主运维）。
 *
 * <p>DATA 围栏：同步会建授权（license-grant 资源 manage 全量围栏——受限管理员
 * 须先扩 DATA 授权）。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@RestController
@RequestMapping("/api/license/v1")
public class LicenseFulfillmentController {

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final FulfillmentCoordinationService coordination;

    public LicenseFulfillmentController(FulfillmentCoordinationService coordination) {
        this.coordination = coordination;
    }

    /** 触发一轮同步（body 可省；batchSize 默认 50 上限 100，与官方 CRM 客户端一致）。 */
    @RequireApiPermission(LicenseApi.GRANT_MANAGE)
    @PostMapping("/fulfillment-integration/synchronization")
    public ResponseEntity<String> synchronize(@RequestBody(required = false) String body,
                                             HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.GRANT_MANAGE);
        support.requireDataAll(context, LicenseApi.RESOURCE_GRANT, "manage");
        if (!coordination.portAvailable()) {
            throw LicenseHttpException.dependencyUnavailable("CRM 履约端口未装配，同步不可用");
        }
        int batchSize = 50;
        if (body != null && !body.isEmpty()) {
            ObjectNode input = support.object(body, "batchSize");
            Integer submitted = support.integer(input, "batchSize", false);
            batchSize = submitted == null ? 50 : submitted;
        }
        if (batchSize < 1 || batchSize > 100) {
            throw LicenseHttpException.unprocessable(LicenseValidationConstant.BATCH_SIZE_RANGE);
        }
        FulfillmentCoordinationService.SyncSummary summary = coordination.sync(batchSize);
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("pulled", summary.pulled);
        view.put("created", summary.created);
        view.put("skippedExisting", summary.skippedExisting);
        view.put("completed", summary.completed);
        view.put("awaitingConfiguration", summary.awaitingConfiguration);
        view.put("reportPending", summary.reportPending);
        view.put("failed", summary.failed);
        if (!summary.failures.isEmpty()) {
            view.set("failures", StrictJson.mapper().valueToTree(summary.failures));
        }
        return ResponseEntity.ok(view.toString());
    }

    private LicenseRequestContext require(HttpServletRequest request, String permission) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return support.requireApiPermission(context, permission);
    }
}
