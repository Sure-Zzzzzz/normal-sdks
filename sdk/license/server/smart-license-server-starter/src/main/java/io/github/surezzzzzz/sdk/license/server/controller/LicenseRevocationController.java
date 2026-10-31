package io.github.surezzzzzz.sdk.license.server.controller;

import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationConstant;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationTargetKind;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.RevocationPackageService;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.CacheControl;

/**
 * 吊销 HTTP 面：登记五类目标的不可撤回吊销+清单。
 *
 * <p>吊销只增不减；授权类吊销联动授权终态（GRANT 目标吊销后授权不可再签发）。
 * 吊销包独立保存 manifest 与签名分片，客户端按完整批次验签后原子安装。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@Slf4j
@RestController
@RequestMapping("/api/license/v1")
public class LicenseRevocationController {

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private void publish(Object event) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
        }
    }
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final RevocationRepository revocations;
    private final LicenseRevocationGuard guard;
    private final RevocationPackageService packages;

    /** 客户限频履约由 License 通过自有 SPI 投影，缺件时启用授权的吊销必须失败关闭。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.surezzzzzz.sdk.license.server.service.LimiterPolicyProjectionService
            limiterPolicyProjectionService;

    public LicenseRevocationController(RevocationRepository revocations, LicenseRevocationGuard guard) {
        this(revocations, guard, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LicenseRevocationController(RevocationRepository revocations, LicenseRevocationGuard guard,
                                       RevocationPackageService packages) {
        this.revocations = revocations;
        this.guard = guard;
        this.packages = packages;
    }

    @RequireApiPermission(LicenseApi.REVOCATION_READ)
    @GetMapping("/revocations")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "read")
    public ResponseEntity<String> list(HttpServletRequest request,
                                       @org.springframework.web.bind.annotation.RequestParam(value = "targetKind", required = false) String targetKind,
                                       @org.springframework.web.bind.annotation.RequestParam(value = "targetId", required = false) String targetId,
                                       @CurrentDataAccessPlan
                                               DataAccessPlan plan,
                                       @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
                                       @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        require(request);
        if (page < 1 || size < 1 || size > RevocationConstant.MAX_PAGE_SIZE) {
            throw LicenseHttpException.badRequest(RevocationConstant.INVALID_PAGE);
        }
        if (targetKind != null && !targetKind.isEmpty() && !RevocationTargetKind.isValid(targetKind)) {
            throw LicenseHttpException.unprocessable(RevocationConstant.INVALID_KIND);
        }
        LicenseAccessScope scope = LicenseAccessScope.from(plan, RevocationConstant.GRANT_ID);
        Page<RevocationEntity> result = revocations.findVisible(scope.restrictedIdsOrNull(), targetKind, targetId,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, RevocationConstant.REVOCATION_ID)));
        ObjectNode body = StrictJson.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        result.getContent().forEach(item -> data.add(view(item)));
        body.put("total", result.getTotalElements());
        body.put("page", result.getNumber() + 1);
        body.put("size", result.getSize());
        body.put("totalPages", result.getTotalPages());
        log.debug("吊销清单查询完成：allData={}，page={}，size={}，total={}",
                scope.isAll(), page, size, result.getTotalElements());
        return ResponseEntity.ok(body.toString());
    }

    @RequireApiPermission(LicenseApi.REVOCATION_MANAGE)
    @PostMapping("/revocations")
    @Transactional
    public ResponseEntity<String> create(@RequestBody String body,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         HttpServletRequest request) {
        LicenseRequestContext context = require(request);
        // 吊销创建是治理动作：受限管理员吊销须先扩 DATA 授权（全量围栏，fail-closed）
        support.requireDataAll(context, LicenseApi.RESOURCE_GRANT, "manage");
        ObjectNode input = support.object(body, "targetKind", "targetId", "reasonCategory");
        String targetKind = support.text(input, "targetKind", true);
        if (!RevocationTargetKind.isValid(targetKind)) {
            throw LicenseHttpException.unprocessable(RevocationConstant.INVALID_KIND);
        }
        String targetId = support.text(input, "targetId", true);
        guard.requireTargetExists(RevocationTargetKind.fromCode(targetKind), targetId);
        Optional<RevocationEntity> existing = revocations.findByTargetKindAndTargetId(targetKind, targetId);
        if (existing.isPresent()) {
            // 维持重复目标 409 契约：唯一去重不是 Idempotency-Key 的原响应重放。
            throw LicenseHttpException.conflict(RevocationConstant.ALREADY_REVOKED);
        }
        disableLimiterPoliciesBeforeRevocation(RevocationTargetKind.fromCode(targetKind), targetId);
        RevocationEntity created;
        try {
            created = revocations.saveAndFlush(new RevocationEntity(
                    "rv-" + UUID.randomUUID(), targetKind, targetId,
                    support.text(input, "reasonCategory", true), context.getPrincipalId()));
        } catch (DataIntegrityViolationException failure) {
            // 仓储事务已回滚；只将确实存在的同目标赢家转为409，其他持久化故障原样透传。
            if (revocations.findByTargetKindAndTargetId(targetKind, targetId).isPresent()) {
                throw LicenseHttpException.conflict(RevocationConstant.ALREADY_REVOKED);
            }
            throw failure;
        }
        log.info("吊销事实已登记：revocationId={}，targetKind={}，requestId={}",
                created.getRevocationId(), targetKind, context.getRequestId());
        publish(new io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents.RevocationCreated(
                context.getPrincipalId(), context.getRequestId(), created.getRevocationId(),
                targetKind, targetId, created.getReasonCategory()));
        return ResponseEntity.status(201).body(view(created).toString());
    }

    /** 先停用客户限频，再登记不可撤回吊销事实，避免留下已吊销但仍可调用的远端规则。 */
    private void disableLimiterPoliciesBeforeRevocation(RevocationTargetKind kind, String targetId) {
        // 单个凭据、激活或人员资格吊销不等于整份商业授权失效，不能停掉客户级全局限频。
        if (kind != RevocationTargetKind.GRANT) {
            return;
        }
        Optional<GrantRevisionEntity> grant = guard.findGrantForTarget(kind, targetId);
        if (!grant.isPresent()) {
            return;
        }
        GrantRevisionEntity entity = grant.get();
        boolean hasPolicies = entity.getLimiterPoliciesJson() != null
                && !entity.getLimiterPoliciesJson().trim().isEmpty()
                && !"[]".equals(entity.getLimiterPoliciesJson().trim());
        if (!hasPolicies) {
            return;
        }
        if (limiterPolicyProjectionService == null && "ENABLED".equals(entity.getState())) {
            throw LicenseHttpException.dependencyUnavailable("客户限频履约端口未装配，不能登记吊销");
        }
        if (limiterPolicyProjectionService != null) {
            limiterPolicyProjectionService.disable(entity);
        }
    }

    @RequireApiPermission(LicenseApi.REVOCATION_READ)
    @GetMapping("/revocations/{revocationId}")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "read")
    public ResponseEntity<String> get(@PathVariable String revocationId,
                                      @CurrentDataAccessPlan
                                              DataAccessPlan plan,
                                      HttpServletRequest request) {
        require(request);
        LicenseAccessScope scope = LicenseAccessScope.from(plan, RevocationConstant.GRANT_ID);
        RevocationEntity entity = revocations.findVisibleById(revocationId, scope.restrictedIdsOrNull())
                .orElseThrow(LicenseHttpException::notFound);
        return ResponseEntity.ok(view(entity).toString());
    }

    /** 生成产品完整吊销包；幂等键绑定产品，失败序号不回收。 */
    @RequireApiPermission(LicenseApi.REVOCATION_DELIVER)
    @PostMapping("/products/{productId}/revocation-packages")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "manage")
    public ResponseEntity<String> createPackage(@PathVariable String productId,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                HttpServletRequest request) {
        LicenseRequestContext context = require(request);
        support.requireDataAll(context, LicenseApi.RESOURCE_PRODUCT, "manage");
        if (packages == null) {
            throw LicenseHttpException.dependencyUnavailable("吊销包服务未装配");
        }
        RevocationPackageService service = packages;
        // 幂等键属于准确的产品目标；产品纳入摘要后，不同产品可安全复用同一客户端键。
        String digest = sha256(productId + ":" + idempotencyKey);
        io.github.surezzzzzz.sdk.license.server.entity.RevocationPackageEntity entity =
                service.create(productId, digest);
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("packageId", entity.getPackageId());
        view.put("operationId", entity.getOperationId());
        view.put("productId", entity.getProductId());
        view.put("sequence", entity.getSequence());
        view.put("snapshotId", entity.getSnapshotId());
        view.put("state", entity.getState());
        view.put("partCount", entity.getPartCount());
        view.put("resultResource", "/api/license/v1/revocation-packages/"
                + entity.getPackageId() + "/content");
        return ResponseEntity.status("READY".equals(entity.getState()) ? 201 : 202).body(view.toString());
    }

    @RequireApiPermission(LicenseApi.REVOCATION_DELIVER)
    @GetMapping("/revocation-packages/{packageId}")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    public ResponseEntity<String> getPackage(@PathVariable String packageId, HttpServletRequest request) {
        LicenseRequestContext context = require(request);
        support.requireDataAll(context, LicenseApi.RESOURCE_PRODUCT, "read");
        if (packages == null) throw LicenseHttpException.dependencyUnavailable("吊销包服务未装配");
        io.github.surezzzzzz.sdk.license.server.entity.RevocationPackageEntity entity = packages.get(packageId);
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("packageId", entity.getPackageId());
        view.put("operationId", entity.getOperationId());
        view.put("productId", entity.getProductId());
        view.put("sequence", entity.getSequence());
        view.put("snapshotId", entity.getSnapshotId());
        view.put("state", entity.getState());
        view.put("partCount", entity.getPartCount());
        view.put("resultResource", "/api/license/v1/revocation-packages/"
                + entity.getPackageId() + "/content");
        return ResponseEntity.ok(view.toString());
    }

    @RequireApiPermission(LicenseApi.REVOCATION_DELIVER)
    @GetMapping("/revocation-packages/{packageId}/content")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    public ResponseEntity<String> packageContent(@PathVariable String packageId, HttpServletRequest request) {
        LicenseRequestContext context = require(request);
        support.requireDataAll(context, LicenseApi.RESOURCE_PRODUCT, "read");
        if (packages == null) throw LicenseHttpException.dependencyUnavailable("吊销包服务未装配");
        io.github.surezzzzzz.sdk.license.server.entity.RevocationPackageEntity metadata = packages.get(packageId);
        ArrayNode values = StrictJson.mapper().createArrayNode();
        for (io.github.surezzzzzz.sdk.license.server.entity.RevocationPackagePartEntity part
                : packages.content(packageId)) {
            values.add(part.getPartJws());
        }
        ObjectNode body = StrictJson.mapper().createObjectNode();
        body.put("packageId", packageId);
        body.put("productId", metadata.getProductId());
        body.put("sequence", metadata.getSequence());
        body.put("snapshotId", metadata.getSnapshotId());
        body.put("manifestJws", metadata.getManifestJws());
        body.set("revocationPartJws", values);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body.toString());
    }

    @RequireApiPermission(LicenseApi.PRODUCT_READ)
    @GetMapping("/products/{productId}/trust-material")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    public ResponseEntity<String> trustMaterial(@PathVariable String productId,
                                                @RequestParam(defaultValue = "0") long knownSequence,
                                                HttpServletRequest request) {
        LicenseRequestContext context = require(request);
        support.requireDataAll(context, LicenseApi.RESOURCE_PRODUCT, "read");
        if (knownSequence < 0) throw LicenseHttpException.badRequest("knownSequence 不得为负");
        if (packages == null) throw LicenseHttpException.dependencyUnavailable("吊销包服务未装配");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(packages.trustMaterial(productId, knownSequence).toString());
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes) result.append(String.format("%02x", item & 255));
            return result.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("JRE 缺少 SHA-256", failure);
        }
    }

    private LicenseRequestContext require(HttpServletRequest request) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return context;
    }

    private static ObjectNode view(RevocationEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("revocationId", entity.getRevocationId());
        view.put("targetKind", entity.getTargetKind());
        view.put("targetId", entity.getTargetId());
        view.put("reasonCategory", entity.getReasonCategory());
        view.put("revokedAt", String.valueOf(entity.getRevokedAt().getEpochSecond()));
        view.put("rowVersion", entity.getRowVersion());
        return view;
    }
}
