package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.IamPortalMachineDirectoryConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalMachineApplicationPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalMachineApplicationDirectoryService;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleHttpHelper;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal 可信应用路由投影开放 API（1.3.8）。
 *
 * <p>该端点只返回路由投影，不代理业务请求，也不接受浏览器 Session。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping(IamPortalMachineDirectoryConstant.PATH)
@RequiredArgsConstructor
public class IamPortalMachineApplicationOpenApiController {

    private final IamPortalMachineApplicationDirectoryService directoryService;

    /**
     * 读取一页 Portal 可信应用路由投影。
     */
    @GetMapping
    @RequireApiPermission(IamPortalMachineDirectoryConstant.API_PERMISSION)
    @DataPermissionOperation(resource = IamPortalMachineDirectoryConstant.DATA_RESOURCE,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<?> listApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long catalogRevision,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        if (!isServiceSubject()) {
            log.debug("Portal 可信应用路由投影拒绝非 SERVICE 主体");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore())
                    .body(IamOpenRoleHttpHelper.error(HttpStatus.FORBIDDEN));
        }
        if (page < 0 || size < 1 || size > SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE
                || (catalogRevision != null && catalogRevision < 1L)) {
            log.debug("Portal 可信应用路由投影拒绝非法分页：page={}, size={}, revision={}",
                    page, size, catalogRevision);
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                    .body(IamOpenRoleHttpHelper.error(HttpStatus.BAD_REQUEST));
        }
        IamPortalMachineApplicationDirectoryService.ReadResult result = directoryService.read(
                page, size, catalogRevision, plan);
        if (!result.isSuccess()) {
            log.debug("Portal 可信应用路由投影 revision 冲突：stage={}, page={}, expectedRevision={}, before={}, after={}",
                    result.getStage(), page, result.getExpectedRevision(), result.getBeforeRevision(),
                    result.getAfterRevision());
            return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                    .body(IamOpenRoleHttpHelper.error(HttpStatus.CONFLICT));
        }
        PortalMachineApplicationPageResponse response = result.getPage();
        log.debug("Portal 可信应用路由投影读取成功：page={}, size={}, revision={}, count={}", page, size,
                response.getCatalogRevision(), response.getItems().size());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    private boolean isServiceSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof VerifiedResourceContext)) {
            return false;
        }
        VerifiedResourceContext context = (VerifiedResourceContext) authentication.getPrincipal();
        return context.getPrincipal() != null
                && context.getPrincipal().getSubjectType() == ResourceSubjectType.SERVICE;
    }
}
