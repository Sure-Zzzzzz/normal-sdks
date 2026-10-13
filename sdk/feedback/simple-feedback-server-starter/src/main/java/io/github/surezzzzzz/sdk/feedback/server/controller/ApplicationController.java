package io.github.surezzzzzz.sdk.feedback.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackPermissionConstant;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.controller.request.ApplicationUpsertRequest;
import io.github.surezzzzzz.sdk.feedback.server.exception.FeedbackStateException;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplicationAccessScope;
import io.github.surezzzzzz.sdk.feedback.server.service.ApplicationRegistryService;
import io.github.surezzzzzz.sdk.feedback.server.service.ApplicationSourceService;
import io.github.surezzzzzz.sdk.feedback.server.service.FeedbackPrincipalResolver;
import io.github.surezzzzzz.sdk.feedback.server.service.FeedbackRequestContext;
import io.github.surezzzzzz.sdk.feedback.server.support.FeedbackRestExceptionHandler;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 应用注册表族 REST 端点（12 端点之 3；管理面）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@RestController
@RequestMapping("${" + SimpleFeedbackServerConstant.CONFIG_PREFIX + ".api-base-path:/api/feedback}")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationRegistryService registryService;
    private final ApplicationSourceService sourceService;
    private final FeedbackPrincipalResolver principalResolver;

    private static HttpHeaders correlationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", FeedbackRestExceptionHandler.currentCorrelationId());
        return headers;
    }

    /**
     * 注册表全量列表（管理用；DATA application 范围遮蔽，受限范围外不出现）。
     *
     * @return 200 列表
     */
    @GetMapping("/applications")
    @RequireApiPermission(FeedbackPermissionConstant.API_APPLICATION_READ)
    @DataPermissionOperation(resource = FeedbackPermissionConstant.DATA_RESOURCE_APPLICATION,
            action = FeedbackPermissionConstant.DATA_ACTION_READ)
    public ResponseEntity<List<ApplicationResponse>> list(@CurrentDataAccessPlan DataAccessPlan plan,
                                                          HttpServletRequest request) {
        requireContext(request);
        FeedbackApplicationAccessScope scope = FeedbackApplicationAccessScope.from(plan);
        if (scope.isDenied()) {
            throw new AccessDeniedException(ErrorMessage.APPLICATION_NOT_FOUND);
        }
        // DATA 范围进 SQL（受限时 WHERE IN；不做先全量取数再内存过滤）
        Map<String, AccessibleApplication> catalogIndex = sourceService.catalogIndex();
        List<ApplicationResponse> items = new ArrayList<>();
        for (FeedbackApplication application : registryService.listByScope(scope.restrictedCodesOrNull())) {
            items.add(ApplicationResponse.from(application,
                    ApplicationSourceService.sourceOf(application.getApplicationCode(), catalogIndex)));
        }
        return ResponseEntity.ok().headers(correlationHeaders()).body(items);
    }

    /**
     * 登记应用（applicationCode 唯一键天然幂等，不收幂等键；创建 201+Location）。
     *
     * <p>非直观约束（走查纠错）：受限 DATA 范围外的目标编码=403 越权拒绝——创建也是写，
     * 目标应用维度同样受投影约束，DENY 同 403；不因"新建"而绕过范围。</p>
     *
     * <p>同源抄录（修正㉔）：编码命中 IAM 目录时展示名强制抄录 IAM 官方名（单一事实源，
     * 手滑即纠）；目录不可核对（散形态/出站失败）时按提交值落库。</p>
     *
     * @return 201
     */
    @PostMapping("/applications")
    @RequireApiPermission(FeedbackPermissionConstant.API_APPLICATION_MANAGE)
    @DataPermissionOperation(resource = FeedbackPermissionConstant.DATA_RESOURCE_APPLICATION,
            action = FeedbackPermissionConstant.DATA_ACTION_MANAGE)
    public ResponseEntity<ApplicationResponse> create(@RequestBody ApplicationUpsertRequest upsertRequest,
                                                      @CurrentDataAccessPlan DataAccessPlan plan,
                                                      HttpServletRequest request) {
        FeedbackRequestContext context = requireContext(request);
        FeedbackApplicationAccessScope scope = FeedbackApplicationAccessScope.from(plan);
        if (scope.isDenied() || !scope.contains(upsertRequest.getApplicationCode())) {
            throw new AccessDeniedException(ErrorMessage.APPLICATION_NOT_FOUND);
        }
        Map<String, AccessibleApplication> catalogIndex = sourceService.catalogIndex();
        String displayName = upsertRequest.getDisplayName();
        String source;
        if (catalogIndex != null && catalogIndex.containsKey(upsertRequest.getApplicationCode())) {
            displayName = catalogIndex.get(upsertRequest.getApplicationCode()).getDisplayName();
            source = ApplicationSourceService.SOURCE_IAM;
            log.info("登记同源应用自动抄录官方名 code={} 来源=IAM目录", upsertRequest.getApplicationCode());
        } else {
            source = ApplicationSourceService.sourceOf(upsertRequest.getApplicationCode(), catalogIndex);
        }
        FeedbackApplication application = FeedbackApplication.builder()
                .applicationCode(upsertRequest.getApplicationCode())
                .displayName(displayName)
                .description(upsertRequest.getDescription())
                .enabled(upsertRequest.getEnabled() == null || upsertRequest.getEnabled())
                .ownerGroup(upsertRequest.getOwnerGroup())
                .build();
        registryService.create(application, context.getSubjectId());
        HttpHeaders headers = correlationHeaders();
        headers.setLocation(URI.create("/applications/" + application.getApplicationCode()));
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers)
                .body(ApplicationResponse.from(application, source));
    }

    /**
     * 更新注册项（expectedRowVersion CAS；编码不可变）。
     *
     * <p>同源锁（修正㉔）：编码命中 IAM 目录时展示名不可改写（SOURCE_001 拒绝），
     * 显式传 syncFromIam=true 则以 IAM 官方名重同步；受理门（enabled）与处理组
     * （ownerGroup）始终可改。目录不可核对时保持全字段可编辑（散形态自主面）。</p>
     *
     * @return 200 更新后
     */
    @PutMapping("/applications/{applicationCode}")
    @RequireApiPermission(FeedbackPermissionConstant.API_APPLICATION_MANAGE)
    @DataPermissionOperation(resource = FeedbackPermissionConstant.DATA_RESOURCE_APPLICATION,
            action = FeedbackPermissionConstant.DATA_ACTION_MANAGE)
    public ResponseEntity<ApplicationResponse> update(@PathVariable String applicationCode,
                                                      @RequestBody ApplicationUpsertRequest upsertRequest,
                                                      @CurrentDataAccessPlan DataAccessPlan plan,
                                                      HttpServletRequest request) {
        requireContext(request);
        FeedbackApplicationAccessScope scope = FeedbackApplicationAccessScope.from(plan);
        FeedbackApplication existing = registryService.findByCode(applicationCode);
        if (existing == null || !scope.contains(applicationCode)) {
            throw new SimpleFeedbackServerException(ErrorCode.APPLICATION_NOT_FOUND,
                    ErrorMessage.APPLICATION_NOT_FOUND);
        }
        if (upsertRequest.getExpectedRowVersion() == null) {
            throw new SimpleFeedbackServerException(ErrorCode.ROW_VERSION_CONFLICT,
                    ErrorMessage.ROW_VERSION_CONFLICT);
        }
        Map<String, AccessibleApplication> catalogIndex = sourceService.catalogIndex();
        boolean iamBacked = catalogIndex != null && catalogIndex.containsKey(applicationCode);
        String displayName;
        if (iamBacked) {
            if (Boolean.TRUE.equals(upsertRequest.getSyncFromIam())) {
                displayName = catalogIndex.get(applicationCode).getDisplayName();
            } else if (upsertRequest.getDisplayName() != null
                    && !upsertRequest.getDisplayName().equals(existing.getDisplayName())) {
                throw new FeedbackStateException(ErrorCode.IAM_SOURCED_FIELD_LOCKED,
                        ErrorMessage.IAM_SOURCED_FIELD_LOCKED);
            } else {
                displayName = existing.getDisplayName();
            }
        } else {
            displayName = upsertRequest.getDisplayName() == null ? existing.getDisplayName()
                    : upsertRequest.getDisplayName();
        }
        FeedbackApplication target = FeedbackApplication.builder()
                .applicationCode(applicationCode)
                .displayName(displayName)
                .description(upsertRequest.getDescription())
                .enabled(upsertRequest.getEnabled() == null ? existing.isEnabled() : upsertRequest.getEnabled())
                .ownerGroup(upsertRequest.getOwnerGroup())
                .createdBy(existing.getCreatedBy())
                .createdTime(existing.getCreatedTime())
                .updatedTime(Instant.now())
                .rowVersion(existing.getRowVersion())
                .build();
        FeedbackApplication updated = registryService.update(target, upsertRequest.getExpectedRowVersion());
        return ResponseEntity.ok().headers(correlationHeaders())
                .body(ApplicationResponse.from(updated,
                        ApplicationSourceService.sourceOf(applicationCode, catalogIndex)));
    }

    private FeedbackRequestContext requireContext(HttpServletRequest request) {
        FeedbackRequestContext context = principalResolver.resolve(request);
        if (context == null) {
            throw new SimpleFeedbackServerException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    ErrorMessage.DEPENDENCY_UNAVAILABLE);
        }
        return context;
    }

    /**
     * 注册项响应 DTO。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationResponse {

        /**
         * 应用编码
         */
        private String applicationCode;

        /**
         * 门户展示名
         */
        private String displayName;

        /**
         * 应用说明
         */
        private String description;

        /**
         * 是否启用
         */
        private boolean enabled;

        /**
         * 期望处理组名（备注）
         */
        private String ownerGroup;

        /**
         * 行版本
         */
        private int rowVersion;

        /**
         * 创建时间
         */
        private Instant createdTime;

        /**
         * 更新时间
         */
        private Instant updatedTime;

        /**
         * 数据来源（修正㉔）：IAM=与 IAM 可信应用同编码（展示名 IAM 承担）；MANUAL=本表登记；
         * UNKNOWN=目录不可核对（散形态或出站失败，全字段可编辑）
         */
        private String source;

        private static ApplicationResponse from(FeedbackApplication application) {
            return from(application, null);
        }

        private static ApplicationResponse from(FeedbackApplication application, String source) {
            return ApplicationResponse.builder()
                    .applicationCode(application.getApplicationCode())
                    .displayName(application.getDisplayName())
                    .description(application.getDescription())
                    .enabled(application.isEnabled())
                    .ownerGroup(application.getOwnerGroup())
                    .rowVersion(application.getRowVersion())
                    .createdTime(application.getCreatedTime())
                    .updatedTime(application.getUpdatedTime())
                    .source(source)
                    .build();
        }
    }

    /**
     * 可选应用精简 DTO（提交人下拉用）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectableApplication {

        /**
         * 应用编码
         */
        private String applicationCode;

        /**
         * 门户展示名
         */
        private String displayName;

        /**
         * 应用说明
         */
        private String description;
    }
}
