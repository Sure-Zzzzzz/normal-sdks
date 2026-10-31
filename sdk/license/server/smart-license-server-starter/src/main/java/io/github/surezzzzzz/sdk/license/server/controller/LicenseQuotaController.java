package io.github.surezzzzzz.sdk.license.server.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.server.constant.QuotaConstant;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.QuotaReservationEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.service.JpaQuotaReservationService;
import io.github.surezzzzzz.sdk.license.server.model.QuotaReservationCommand;
import io.github.surezzzzzz.sdk.license.server.event.QuotaOperationCompleted;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行端额度预占/累计扣减入口。请求必须落到当前认证主体和授权范围，累计扣减不可撤回。
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@Slf4j
@RestController
@RequestMapping("/api/license/v1")
public class LicenseQuotaController {

    private final GrantRepository grants;
    private final JpaQuotaReservationService reservations;
    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    public LicenseQuotaController(GrantRepository grants,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                          JpaQuotaReservationService reservations) {
        this.grants = grants;
        this.reservations = reservations;
    }

    /** 读取额度操作元数据；DATA 过滤先进入 SQL，再执行分页和计数。 */
    @RequireApiPermission(LicenseApi.QUOTA_READ)
    @GetMapping("/quota-reservations")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "read")
    public ResponseEntity<String> list(@CurrentDataAccessPlan DataAccessPlan plan,
                                       HttpServletRequest request,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(value = "grantId", required = false) String grantId,
                                       @RequestParam(value = "activationId", required = false) String activationId,
                                       @RequestParam(value = "metric", required = false) String metric,
                                       @RequestParam(value = "kind", required = false) String kind,
                                       @RequestParam(value = "state", required = false) String state) {
        require(request, LicenseApi.QUOTA_READ);
        if (reservations == null) {
            throw LicenseHttpException.dependencyUnavailable("额度台账未装配");
        }
        String normalizedGrantId = optionalFilter(grantId, "grantId");
        String normalizedActivationId = optionalFilter(activationId, "activationId");
        String normalizedMetric = optionalFilter(metric, "metric");
        String normalizedKind = optionalFilter(kind, "kind");
        String normalizedState = optionalFilter(state, "state");
        if (normalizedKind != null && !"CONCURRENT".equals(normalizedKind)
                && !"CONSUMPTION".equals(normalizedKind)
                || normalizedState != null && !"ACTIVE".equals(normalizedState)
                && !"RELEASED".equals(normalizedState) && !"EXPIRED".equals(normalizedState)) {
            throw LicenseHttpException.unprocessable("额度筛选值无效");
        }
        if (page < 1 || size < 1 || size > 100) {
            throw LicenseHttpException.unprocessable("page从1开始且size范围为1..100");
        }
        LicenseAccessScope scope = LicenseAccessScope.from(plan, "grantId");
        Pageable pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "reservationId")));
        Page<QuotaReservationEntity> result = scope.resolve(
                () -> reservations.list(normalizedGrantId, normalizedActivationId, normalizedMetric,
                        normalizedKind, normalizedState, null, pageable),
                ids -> reservations.list(normalizedGrantId, normalizedActivationId, normalizedMetric,
                        normalizedKind, normalizedState, ids, pageable));
        return pageOf(result);
    }

    /** 读取单条额度操作元数据；单行仍须通过当前授权 DATA 范围。 */
    @RequireApiPermission(LicenseApi.QUOTA_USE)
    @GetMapping("/quota-reservations/{reservationId}")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "use")
    public ResponseEntity<String> get(@PathVariable String reservationId,
                                      @CurrentDataAccessPlan DataAccessPlan plan,
                                      HttpServletRequest request) {
        if (reservations == null) {
            throw LicenseHttpException.dependencyUnavailable("额度台账未装配");
        }
        QuotaReservationEntity entity;
        LicenseRequestContext context = require(request, LicenseApi.QUOTA_USE);
        try {
            entity = reservations.findOwned(reservationId, context.getPrincipalId(),
                    context.getAuthenticationSourceId());
        } catch (SecurityException denied) {
            throw LicenseHttpException.notFound();
        } catch (IllegalArgumentException missing) {
            throw LicenseHttpException.notFound();
        }
        LicenseAccessScope.from(plan, "grantId").requireVisible(entity.getGrantId());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(view(entity).toString());
    }

    /** 创建 CONCURRENT 预占或 CONSUMPTION 累计扣减。 */
    @RequireApiPermission(LicenseApi.QUOTA_USE)
    @PostMapping("/quota-reservations")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "use")
    public ResponseEntity<String> create(@RequestBody String body,
                                         @CurrentDataAccessPlan DataAccessPlan plan,
                                         HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.QUOTA_USE);
        if (reservations == null) {
            throw LicenseHttpException.dependencyUnavailable("额度台账未装配");
        }
        ObjectNode input = support.object(body, "productId", "grantId", "subjectKind", "action", "metric",
                "quantity", "kind", "businessOperationId", "expectedGrantRevision", "assignmentId", "activationId");
        String grantId = support.text(input, "grantId", true);
        String metric = support.text(input, "metric", true);
        String kind = support.text(input, "kind", true);
        String operationId = support.text(input, "businessOperationId", true);
        String productId = support.text(input, "productId", true);
        String subjectKind = support.text(input, "subjectKind", true);
        String action = support.text(input, "action", true);
        long expectedGrantRevision = integral(input, "expectedGrantRevision");
        String assignmentId = support.text(input, "assignmentId", false);
        String activationId = support.text(input, "activationId", false);
        if (!SubjectKind.isValid(subjectKind) || expectedGrantRevision < 1L
                || productId.length() > QuotaConstant.ID_LENGTH || grantId.length() > QuotaConstant.ID_LENGTH
                || metric.length() > QuotaConstant.ID_LENGTH || action.length() > QuotaConstant.ID_LENGTH
                || operationId.length() > QuotaConstant.OPERATION_LENGTH
                || assignmentId != null && assignmentId.length() > QuotaConstant.ID_LENGTH
                || activationId != null && activationId.length() > QuotaConstant.ID_LENGTH) {
            throw LicenseHttpException.unprocessable(QuotaConstant.INVALID_INPUT);
        }
        if (!("CONCURRENT".equals(kind) || "CONSUMPTION".equals(kind))) {
            throw LicenseHttpException.unprocessable("额度 kind 仅支持 CONCURRENT/CONSUMPTION");
        }
        long quantity = integral(input, "quantity");
        if (quantity <= 0L) {
            throw LicenseHttpException.unprocessable("额度 quantity 必须为正整数");
        }
        LicenseAccessScope.from(plan, "grantId").requireVisible(grantId);
        grant(grantId);
        try {
            QuotaReservationCommand command = QuotaReservationCommand.builder()
                    .productId(productId).grantId(grantId).subjectKind(subjectKind).action(action)
                    .metric(metric).quantity(quantity).kind(kind).businessOperationId(operationId)
                    .expectedGrantRevision(expectedGrantRevision)
                    .principalId(context.getPrincipalId()).authenticationSourceId(context.getAuthenticationSourceId())
                    .subjectType(context.getSubjectType()).assignmentId(assignmentId).activationId(activationId).build();
            QuotaReservationEntity entity = reservations.reserve(command);
            publishObserved(entity, context, QuotaOperationCompleted.Operation.RESERVATION_OBSERVED);
            return ResponseEntity.status(201).header("Cache-Control", "no-store").body(view(entity).toString());
        } catch (JpaQuotaReservationService.QuotaUnavailableException unavailable) {
            throw LicenseHttpException.conflict(unavailable.getMessage());
        } catch (IllegalArgumentException invalid) {
            throw LicenseHttpException.conflict(invalid.getMessage());
        } catch (SecurityException denied) {
            throw LicenseHttpException.notFound();
        } catch (DataAccessException unavailable) {
            throw LicenseHttpException.dependencyUnavailable("额度台账不可用");
        }
    }

    /** 并发预占释放；累计扣减返回409。 */
    @RequireApiPermission(LicenseApi.QUOTA_USE)
    @DeleteMapping("/quota-reservations/{reservationId}")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "use")
    public ResponseEntity<String> release(@PathVariable String reservationId,
                                          @CurrentDataAccessPlan DataAccessPlan plan,
                                          HttpServletRequest request) {
        return releaseInternal(reservationId, plan, request);
    }

    /** POST 兼容入口；与 DELETE 使用独立映射，避免 Spring 只识别同方法上的一个映射注解。 */
    @RequireApiPermission(LicenseApi.QUOTA_USE)
    @PostMapping("/quota-reservations/{reservationId}/release")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "use")
    public ResponseEntity<String> releasePost(@PathVariable String reservationId,
                                              @CurrentDataAccessPlan DataAccessPlan plan,
                                              HttpServletRequest request) {
        return releaseInternal(reservationId, plan, request);
    }

    private ResponseEntity<String> releaseInternal(String reservationId, DataAccessPlan plan,
                                                    HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.QUOTA_USE);
        if (reservations == null) {
            throw LicenseHttpException.dependencyUnavailable("额度台账未装配");
        }
        try {
            // 先以同一 DATA 计划核验授权范围，再允许服务层修改余额和状态。
            QuotaReservationEntity current = reservations.find(reservationId)
                    .orElseThrow(LicenseHttpException::notFound);
            LicenseAccessScope.from(plan, "grantId").requireVisible(current.getGrantId());
            QuotaReservationEntity entity = reservations.release(reservationId, context.getPrincipalId(),
                    context.getAuthenticationSourceId());
            publishObserved(entity, context, QuotaOperationCompleted.Operation.RELEASE_OBSERVED);
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(view(entity).toString());
        } catch (SecurityException denied) {
            throw LicenseHttpException.notFound();
        } catch (IllegalStateException invalid) {
            throw LicenseHttpException.conflict(invalid.getMessage());
        } catch (IllegalArgumentException missing) {
            throw LicenseHttpException.notFound();
        } catch (DataAccessException unavailable) {
            throw LicenseHttpException.dependencyUnavailable("额度台账不可用");
        }
    }

    /** 并发预占续期；当前fence必须匹配，DATA校验先于服务层任何余额或状态写入。 */
    @RequireApiPermission(LicenseApi.QUOTA_USE)
    @PutMapping("/quota-reservations/{reservationId}/renewal")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "use")
    public ResponseEntity<String> renew(@PathVariable String reservationId, @RequestBody String body,
                                        @CurrentDataAccessPlan DataAccessPlan plan, HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.QUOTA_USE);
        if (reservations == null) {
            throw LicenseHttpException.dependencyUnavailable("额度台账未装配");
        }
        ObjectNode input = support.object(body, "expectedFence");
        long expectedFence = integral(input, "expectedFence");
        if (expectedFence < 0L) {
            throw LicenseHttpException.unprocessable("expectedFence不能为负数");
        }
        try {
            QuotaReservationEntity current = reservations.find(reservationId)
                    .orElseThrow(LicenseHttpException::notFound);
            LicenseAccessScope.from(plan, "grantId").requireVisible(current.getGrantId());
            QuotaReservationEntity entity = reservations.renew(reservationId, expectedFence,
                    context.getPrincipalId(), context.getAuthenticationSourceId(), context.getSubjectType());
            publishObserved(entity, context, QuotaOperationCompleted.Operation.RENEWAL_OBSERVED);
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(view(entity).toString());
        } catch (SecurityException denied) {
            throw LicenseHttpException.forbidden();
        } catch (IllegalStateException invalid) {
            throw LicenseHttpException.conflict(invalid.getMessage());
        } catch (IllegalArgumentException missing) {
            throw LicenseHttpException.notFound();
        } catch (DataAccessException unavailable) {
            throw LicenseHttpException.dependencyUnavailable("额度台账不可用");
        }
    }

    private LicenseRequestContext require(HttpServletRequest request, String permission) {
        LicenseRequestContext context = support.context(resolver, request);
        if (context == null) {
            throw LicenseHttpException.unauthenticated();
        }
        request.setAttribute("licenseRequestId", context.getRequestId());
        return support.requireApiPermission(context, permission);
    }

    private void publishObserved(QuotaReservationEntity entity, LicenseRequestContext context,
                                  QuotaOperationCompleted.Operation operation) {
        if (eventPublisher == null) {
            return;
        }
        // 服务的独立事务已经完成提交；观察事件失败不能把已扣减额度伪装为业务失败。
        try {
            eventPublisher.publishEvent(new QuotaOperationCompleted(context.getPrincipalId(), context.getRequestId(),
                    entity.getGrantId(), entity.getReservationId(), entity.getKind(), entity.getState(),
                    entity.getFence(), operation));
        } catch (RuntimeException failure) {
            log.warn("额度提交后审计发布失败：reservationId={}，异常类型={}",
                    entity.getReservationId(), failure.getClass().getSimpleName());
        }
    }

    private GrantRevisionEntity grant(String grantId) {
        try {
            return grants.findById(grantId).orElseThrow(LicenseHttpException::notFound);
        } catch (DataAccessException unavailable) {
            throw LicenseHttpException.dependencyUnavailable("授权依赖不可用");
        }
    }

    private static long integral(ObjectNode input, String field) {
        com.fasterxml.jackson.databind.JsonNode node = input.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw LicenseHttpException.unprocessable(field + " 必须为整数");
        }
        return node.longValue();
    }

    private static ObjectNode view(QuotaReservationEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("reservationId", entity.getReservationId());
        view.put("grantId", entity.getGrantId());
        view.put("metric", entity.getMetric());
        view.put("quantity", entity.getQuantity());
        view.put("kind", entity.getKind());
        view.put("action", entity.getAction());
        view.put("grantRevision", entity.getGrantRevision());
        view.put("businessOperationId", entity.getBusinessOperationId());
        view.put("fence", entity.getFence());
        view.put("validUntil", entity.getValidUntil());
        view.put("state", entity.getState());
        return view;
    }

    private static ResponseEntity<String> pageOf(Page<QuotaReservationEntity> page) {
        ObjectNode body = StrictJson.mapper().createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode data = body.putArray("data");
        page.getContent().forEach(item -> data.add(view(item)));
        body.put("total", page.getTotalElements());
        body.put("page", page.getNumber() + 1);
        body.put("size", page.getSize());
        body.put("totalPages", page.getTotalPages());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body.toString());
    }

    private static String optionalFilter(String value, String field) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() > QuotaConstant.ID_LENGTH) {
            throw LicenseHttpException.unprocessable(field + "长度超过128");
        }
        return value;
    }
}
