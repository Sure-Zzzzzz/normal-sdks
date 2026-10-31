package io.github.surezzzzzz.sdk.license.server.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.entity.CustomerEntity;
import io.github.surezzzzzz.sdk.license.server.entity.OrganizationBindingEntity;
import io.github.surezzzzzz.sdk.license.server.repository.CustomerRepository;
import io.github.surezzzzzz.sdk.license.server.repository.OrganizationBindingRepository;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户组织绑定 HTTP 面（IAM 部门准入事实源）：读取/重绑（重绑递增代数）。
 *
 * <p>DATA 维度：license-customer 资源按 customerId 收窄（绑定属客户域配置）。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@RestController
@RequestMapping("/api/license/v1")
public class LicenseOrganizationBindingController {

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final OrganizationBindingRepository bindings;
    private final CustomerRepository customers;

    public LicenseOrganizationBindingController(OrganizationBindingRepository bindings,
                                               CustomerRepository customers) {
        this.bindings = bindings;
        this.customers = customers;
    }

    @RequireApiPermission(LicenseApi.CUSTOMER_READ)
    @GetMapping("/customers/{customerId}/organization-binding")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_CUSTOMER, action = "read")
    public ResponseEntity<String> getBinding(@PathVariable String customerId,
            @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan
                    io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan plan,
            HttpServletRequest request) {
        require(request, LicenseApi.CUSTOMER_READ);
        io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope.from(plan, "customerId")
                .requireVisible(customerId);
        customers.findById(customerId).orElseThrow(LicenseHttpException::notFound);
        return ResponseEntity.ok(bindings.findById(customerId)
                .map(LicenseOrganizationBindingController::view)
                .orElseGet(() -> emptyView(customerId)).toString());
    }

    /** 绑定/重绑（组织实例或根部门变化递增代数；联动清单按代数识别过期差异）。 */
    @RequireApiPermission(LicenseApi.CUSTOMER_MANAGE)
    @PutMapping("/customers/{customerId}/organization-binding")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_CUSTOMER, action = "manage")
    public ResponseEntity<String> putBinding(@PathVariable String customerId, @RequestBody String body,
            @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan
                    io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan plan,
            HttpServletRequest request) {
        require(request, LicenseApi.CUSTOMER_MANAGE);
        io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope.from(plan, "customerId")
                .requireAllowed(customerId);
        customers.findById(customerId).orElseThrow(LicenseHttpException::notFound);
        ObjectNode input = support.object(body, "organizationInstanceId", "rootDepartmentId");
        String organizationInstanceId = support.text(input, "organizationInstanceId", true);
        long rootDepartmentId = input.path("rootDepartmentId").asLong(0L);
        if (rootDepartmentId <= 0L) {
            throw LicenseHttpException.unprocessable("rootDepartmentId 必须为正整数");
        }
        OrganizationBindingEntity entity = bindings.findById(customerId).orElse(null);
        if (entity == null) {
            entity = new OrganizationBindingEntity(customerId, organizationInstanceId, rootDepartmentId);
        } else {
            entity.rebind(organizationInstanceId, rootDepartmentId);
        }
        return ResponseEntity.ok(view(bindings.save(entity)).toString());
    }

    private LicenseRequestContext require(HttpServletRequest request, String permission) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return support.requireApiPermission(context, permission);
    }

    private static ObjectNode view(OrganizationBindingEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("customerId", entity.getCustomerId());
        view.put("organizationInstanceId", entity.getOrganizationInstanceId());
        view.put("rootDepartmentId", entity.getRootDepartmentId());
        view.put("bindingGeneration", entity.getBindingGeneration());
        view.put("rowVersion", entity.getRowVersion());
        return view;
    }

    private static ObjectNode emptyView(String customerId) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("customerId", customerId);
        view.putNull("organizationInstanceId");
        view.putNull("rootDepartmentId");
        view.putNull("bindingGeneration");
        return view;
    }
}
