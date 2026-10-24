package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.ApplicationOpenApiResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放 API 可信应用目录（{@code /iam/api/applications}，1.3.7）。
 *
 * <p>供外部系统（如限流管理的目录 adaptor）拉取 IAM 权威注册的应用清单：目录为全局事实，
 * API 码 {@code iam:application:api} + DATA {@code iam:application} read——注册表无部门归属，
 * 授权恒为 all=true（IN 范围维度不适用）。响应只含目录三字段，不带 client 明细与管理视图字段。
 * 排序固定 createdAt 倒序（新注册在前），分页为 Spring Page wire 形态（页码 0 起）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/api/applications")
@RequiredArgsConstructor
public class IamApplicationOpenApiController {

    private final IamTrustedApplicationRepository trustedApplicationRepository;

    /**
     * 分页列出全部可信应用（DATA 计划注入经 {@code @CurrentDataAccessPlan}，all=true 即全量目录）。
     */
    @GetMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_APPLICATION_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_APPLICATION,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<Page<ApplicationOpenApiResponse>> listApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ApplicationOpenApiResponse> response = trustedApplicationRepository.findAll(pageable)
                .map(this::toResponse);
        log.debug("开放 API 可信应用目录查询：operator={}, 命中 {} 条", resolveOperator(), response.getTotalElements());
        return ResponseEntity.ok(response);
    }

    private ApplicationOpenApiResponse toResponse(IamTrustedApplicationEntity app) {
        return new ApplicationOpenApiResponse(
                app.getApplicationCode(), app.getApplicationName(), app.getStatus());
    }

    private String resolveOperator() {
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof
                io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext) {
            io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal principal =
                    ((io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext)
                            authentication.getPrincipal()).getPrincipal();
            return principal.getSourceId().getValue() + ":" + principal.getSubjectId();
        }
        return authentication == null ? "unknown" : authentication.getName();
    }
}
