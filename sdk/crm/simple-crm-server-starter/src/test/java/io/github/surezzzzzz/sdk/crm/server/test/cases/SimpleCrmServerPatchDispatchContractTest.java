package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.crm.server.constant.SimpleCrmServerConstant;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerArchiveController;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerFulfillmentController;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerQuotationController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PATCH 动作分派契约测试：每个 PATCH 方法必须带专属查询参数路由条件与逐动作权限码，
 * 防止退回「单方法复用一个权限码」形态（拆权限码是发版契约，不能回退）。
 *
 * @author surezzzzzz
 */
class SimpleCrmServerPatchDispatchContractTest {

    @Test
    void customerPatchActionsCarryDedicatedCodes() {
        assertDispatch(SimpleCrmServerArchiveController.class, "deactivateCustomer", new String[]{"state=inactive", "!owner"},
                SimpleCrmServerConstant.API_CODE_CUSTOMER_DISABLE);
        assertDispatch(SimpleCrmServerArchiveController.class, "activateCustomer", new String[]{"state=active", "!owner"},
                SimpleCrmServerConstant.API_CODE_CUSTOMER_ACTIVATE);
        assertDispatch(SimpleCrmServerArchiveController.class, "mergeCustomer", new String[]{"state=merged", "!owner"},
                SimpleCrmServerConstant.API_CODE_CUSTOMER_MERGE);
        assertDispatch(SimpleCrmServerArchiveController.class, "transferCustomer", new String[]{"owner", "!state"},
                SimpleCrmServerConstant.API_CODE_CUSTOMER_TRANSFER);
    }

    @Test
    void offeringPatchActionsCarryDedicatedCodes() {
        assertDispatch(SimpleCrmServerArchiveController.class, "publishOffering", new String[]{"state=published", "!owner"},
                SimpleCrmServerConstant.API_CODE_OFFERING_PUBLISH);
        assertDispatch(SimpleCrmServerArchiveController.class, "disableOffering", new String[]{"state=disabled", "!owner"},
                SimpleCrmServerConstant.API_CODE_OFFERING_DISABLE);
    }

    @Test
    void quotationPatchActionsCarryDedicatedCodes() {
        assertDispatch(SimpleCrmServerQuotationController.class, "issueQuotation", new String[]{"state=issued", "!owner"},
                SimpleCrmServerConstant.API_CODE_QUOTATION_ISSUE);
        assertDispatch(SimpleCrmServerQuotationController.class, "confirmQuotation", new String[]{"state=confirmed", "!owner"},
                SimpleCrmServerConstant.API_CODE_QUOTATION_CONFIRM);
        assertDispatch(SimpleCrmServerQuotationController.class, "withdrawQuotation", new String[]{"state=withdrawn", "!owner"},
                SimpleCrmServerConstant.API_CODE_QUOTATION_WITHDRAW);
        assertDispatch(SimpleCrmServerQuotationController.class, "transferQuotation", new String[]{"owner", "!state"},
                SimpleCrmServerConstant.API_CODE_QUOTATION_TRANSFER);
    }

    @Test
    void fulfillmentPatchActionsCarryDedicatedCodes() {
        assertDispatch(SimpleCrmServerFulfillmentController.class, "reportFulfillmentResult", new String[]{"sequence", "!state"},
                SimpleCrmServerConstant.API_CODE_FULFILLMENT_REPORT);
        assertDispatch(SimpleCrmServerFulfillmentController.class, "resetFulfillmentItem",
                new String[]{"state=PENDING_DISPATCH", "!sequence"},
                SimpleCrmServerConstant.API_CODE_FULFILLMENT_RESET);
        assertDataAction(SimpleCrmServerFulfillmentController.class, "reportFulfillmentResult",
                SimpleCrmServerConstant.DATA_ACTION_WRITE);
        assertDataAction(SimpleCrmServerFulfillmentController.class, "resetFulfillmentItem",
                SimpleCrmServerConstant.DATA_ACTION_WRITE);
    }

    @Test
    void quotationCreateAndRenegotiateCarryDedicatedCodes() {
        assertPostDispatch(SimpleCrmServerQuotationController.class, "createQuotation", "!sourceQuotationId",
                SimpleCrmServerConstant.API_CODE_QUOTATION_CREATE);
        assertPostDispatch(SimpleCrmServerQuotationController.class, "renegotiateQuotation",
                "sourceQuotationId", SimpleCrmServerConstant.API_CODE_QUOTATION_RENEGOTIATE);
    }

    private void assertPostDispatch(Class<?> controller, String methodName, String expectedParams,
                                    String expectedApiCode) {
        Method method = findPatchMethod(controller, methodName);
        org.springframework.web.bind.annotation.PostMapping mapping =
                method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
        assertNotNull(mapping, methodName + " 必须是 @PostMapping");
        assertArrayEquals(new String[]{expectedParams}, mapping.params(),
                methodName + " 的分派查询参数必须逐字一致");
        RequireApiPermission permission = method.getAnnotation(RequireApiPermission.class);
        assertNotNull(permission, methodName + " 必须声明专属权限码");
        assertEquals(expectedApiCode, permission.value(),
                methodName + " 的权限码必须与动作一一对应");
    }

    private void assertDispatch(Class<?> controller, String methodName, String[] expectedParams,
                                String expectedApiCode) {
        Method method = findPatchMethod(controller, methodName);
        PatchMapping mapping = method.getAnnotation(PatchMapping.class);
        assertNotNull(mapping, methodName + " 必须是 @PatchMapping");
        assertArrayEquals(expectedParams, mapping.params(),
                methodName + " 的分派查询参数必须逐字一致");
        RequireApiPermission permission = method.getAnnotation(RequireApiPermission.class);
        assertNotNull(permission, methodName + " 必须声明专属权限码");
        assertEquals(expectedApiCode, permission.value(),
                methodName + " 的权限码必须与动作一一对应");
    }

    private Method findPatchMethod(Class<?> controller, String methodName) {
        for (Method method : controller.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new AssertionError(controller.getSimpleName() + " 缺少方法 " + methodName);
    }

    private void assertDataAction(Class<?> controller, String methodName, String expectedAction) {
        Method method = findPatchMethod(controller, methodName);
        DataPermissionOperation operation = method.getAnnotation(DataPermissionOperation.class);
        assertNotNull(operation, methodName + " 必须声明数据权限动作");
        assertEquals(SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER, operation.resource(),
                methodName + " 的数据资源必须落在客户归属维度");
        assertEquals(expectedAction, operation.action(),
                methodName + " 的数据动作必须与写路径一致");
    }
}
