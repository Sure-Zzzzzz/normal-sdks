package io.github.surezzzzzz.sdk.license.server.controller;

import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.SubjectRef;
import io.github.surezzzzzz.sdk.license.server.constant.ActivationConstant;
import io.github.surezzzzzz.sdk.license.server.constant.SubjectAssignmentConstant;
import io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ActivationQuotaAllocationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationQuotaAllocationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 固定资格与部署激活 HTTP 面：授权下两类子对象（人员/服务资格、部署激活）。
 *
 * <p>语义对齐设计 §4：部署授权不建资格（校验 subjectKind 反向约束）、人员授权不建激活；
 * 资格追加实名指标代数递增、暂停占席；激活暂停仅停止新签发（旧离线凭据不因此失效）。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@lombok.extern.slf4j.Slf4j
@RestController
@RequestMapping("/api/license/v1")
public class LicenseSubjectController {

    private static final Set<String> ASSIGNMENT_KINDS = new HashSet<>(java.util.Arrays.asList("PERSON", "SERVICE"));

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final SubjectAssignmentRepository assignments;
    private final ActivationRepository activations;
    private final GrantRepository grants;
    private final ActivationQuotaAllocationRepository quotaAllocations;
    private final ModelVersionRepository models;

    public LicenseSubjectController(SubjectAssignmentRepository assignments,
                                    ActivationRepository activations, GrantRepository grants) {
        this(assignments, activations, grants, null, null);
    }

    /** 生产装配使用分配台账与模型仓储；保留三参构造供既有隔离测试使用。 */
    @org.springframework.beans.factory.annotation.Autowired
    public LicenseSubjectController(SubjectAssignmentRepository assignments,
                                    ActivationRepository activations, GrantRepository grants,
                                    @org.springframework.beans.factory.annotation.Autowired(required = false)
                                    ActivationQuotaAllocationRepository quotaAllocations,
                                    @org.springframework.beans.factory.annotation.Autowired(required = false)
                                    ModelVersionRepository models) {
        this.assignments = assignments;
        this.activations = activations;
        this.grants = grants;
        this.quotaAllocations = quotaAllocations;
        this.models = models;
    }

    // ===== subject-assignments =====

    @RequireApiPermission(LicenseApi.SUBJECT_ASSIGNMENT_READ)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "read")
    @GetMapping("/grants/{grantId}/subject-assignments")
    public ResponseEntity<String> listAssignments(@PathVariable String grantId,
                                                  @CurrentDataAccessPlan
                                                          DataAccessPlan plan,
                                                  HttpServletRequest request) {
        require(request);
        grantInScope(plan, grantId);
        grants.findById(grantId).orElseThrow(LicenseHttpException::notFound);
        ObjectNode body = StrictJson.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        assignments.findByGrantId(grantId).forEach(item -> data.add(assignmentView(item)));
        body.put("total", data.size());
        body.put("page", 1);
        body.put("size", Math.max(1, data.size()));
        body.put("totalPages", 1);
        return ResponseEntity.ok(body.toString());
    }

    @RequireApiPermission(LicenseApi.SUBJECT_ASSIGNMENT_MANAGE)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    @PostMapping("/grants/{grantId}/subject-assignments")
    public ResponseEntity<String> createAssignment(@PathVariable String grantId, @RequestBody String body,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @CurrentDataAccessPlan
                                                           DataAccessPlan plan,
                                                   HttpServletRequest request) {
        require(request);
        grantInScope(plan, grantId);
        GrantRevisionEntity grant = grants.findById(grantId).orElseThrow(LicenseHttpException::notFound);
        if (!ASSIGNMENT_KINDS.contains(grant.getSubjectKind())) {
            throw LicenseHttpException.unprocessable("部署型授权不建固定资格（走部署激活）");
        }
        ObjectNode input = support.object(body, "grantRevision", "subjectKind", "sourceId",
                "subjectId", "requestedNamedMetrics");
        long grantRevision = currentGrantRevision(input, grant);
        String subjectKind = support.text(input, "subjectKind", true);
        if (!subjectKind.equals(grant.getSubjectKind())) {
            throw LicenseHttpException.unprocessable("资格对象种类必须与授权一致：" + grant.getSubjectKind());
        }
        SubjectAssignmentEntity created = assignments.save(new SubjectAssignmentEntity(
                "sa-" + UUID.randomUUID(), grantId, grantRevision, subjectKind,
                support.text(input, "sourceId", true), support.text(input, "subjectId", true),
                metricsJson(input)));
        return ResponseEntity.status(201).body(assignmentView(created).toString());
    }

    @RequireApiPermission(LicenseApi.SUBJECT_ASSIGNMENT_READ)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "read")
    @GetMapping("/subject-assignments/{assignmentId}")
    public ResponseEntity<String> getAssignment(@PathVariable String assignmentId,
                                                @CurrentDataAccessPlan
                                                        DataAccessPlan plan,
                                                HttpServletRequest request) {
        require(request);
        SubjectAssignmentEntity assignment = findAssignment(assignmentId);
        grantVisible(plan, assignment.getGrantId());
        return ResponseEntity.ok(assignmentView(assignment).toString());
    }

    @RequireApiPermission(LicenseApi.SUBJECT_ASSIGNMENT_MANAGE)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    @PatchMapping("/subject-assignments/{assignmentId}/state")
    public ResponseEntity<String> patchAssignmentState(@PathVariable String assignmentId,
                                                       @RequestBody String body,
                                                       @CurrentDataAccessPlan
                                                               DataAccessPlan plan,
                                                       HttpServletRequest request) {
        require(request);
        ObjectNode input = support.object(body, "state", "expectedRowVersion");
        SubjectAssignmentEntity entity = findAssignment(assignmentId);
        grantVisible(plan, entity.getGrantId());
        requireCurrentAssignmentRevision(entity);
        requireAssignmentVersion(input, entity);
        requireMutableAssignment(entity);
        String state = support.text(input, "state", true);
        if (!"ACTIVE".equals(state) && !"SUSPENDED".equals(state)) {
            throw LicenseHttpException.unprocessable("状态仅支持 ACTIVE/SUSPENDED（撤回走吊销）");
        }
        entity.setState(state);
        return ResponseEntity.ok(assignmentView(assignments.save(entity)).toString());
    }

    @RequireApiPermission(LicenseApi.SUBJECT_ASSIGNMENT_MANAGE)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    @PostMapping("/subject-assignments/{assignmentId}/named-seat-allocations")
    public ResponseEntity<String> appendMetrics(@PathVariable String assignmentId, @RequestBody String body,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                @CurrentDataAccessPlan
                                                        DataAccessPlan plan,
                                                HttpServletRequest request) {
        require(request);
        ObjectNode input = support.object(body, "expectedRowVersion", "grantRevision", "requestedNamedMetrics");
        SubjectAssignmentEntity entity = findAssignment(assignmentId);
        grantVisible(plan, entity.getGrantId());
        requireCurrentAssignmentRevision(entity);
        requireAssignmentVersion(input, entity);
        requireMutableAssignment(entity);
        if (entity.getAssignmentGeneration() == Long.MAX_VALUE) {
            throw LicenseHttpException.conflict(SubjectAssignmentConstant.INVALID_GENERATION);
        }
        Set<String> existing = metricsSet(entity.getMetrics());
        com.fasterxml.jackson.databind.JsonNode requestedValue = input.get("requestedNamedMetrics");
        if (requestedValue == null || !requestedValue.isArray() || requestedValue.isEmpty()) {
            throw LicenseHttpException.unprocessable(SubjectAssignmentConstant.INVALID_METRICS);
        }
        ArrayNode requested = (ArrayNode) requestedValue;
        for (com.fasterxml.jackson.databind.JsonNode item : requested) {
            if (!item.isTextual() || !existing.add(item.asText())) {
                // 重复指标拒绝（设计：既有指标不重复占）
                throw LicenseHttpException.conflict("指标已存在或形态非法：" + item.asText());
            }
        }
        GrantRevisionEntity grant = grants.findById(entity.getGrantId()).orElseThrow(LicenseHttpException::notFound);
        currentGrantRevision(input, grant);
        entity.appendMetrics(StrictJson.mapper().valueToTree(existing).toString(),
                entity.getAssignmentGeneration() + 1L);
        return ResponseEntity.ok(assignmentView(assignments.save(entity)).toString());
    }

    // ===== activations =====

    @RequireApiPermission(LicenseApi.ACTIVATION_READ)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "read")
    @GetMapping("/grants/{grantId}/activations")
    public ResponseEntity<String> listActivations(@PathVariable String grantId,
                                                  @CurrentDataAccessPlan
                                                          DataAccessPlan plan,
                                                  HttpServletRequest request) {
        require(request);
        grantInScope(plan, grantId);
        grants.findById(grantId).orElseThrow(LicenseHttpException::notFound);
        ObjectNode body = StrictJson.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        activations.findByGrantId(grantId).forEach(item -> data.add(activationView(item)));
        body.put("total", data.size());
        body.put("page", 1);
        body.put("size", Math.max(1, data.size()));
        body.put("totalPages", 1);
        return ResponseEntity.ok(body.toString());
    }

    @RequireApiPermission(LicenseApi.ACTIVATION_MANAGE)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    @PostMapping("/grants/{grantId}/activations")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<String> createActivation(@PathVariable String grantId, @RequestBody String body,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @CurrentDataAccessPlan
                                                           DataAccessPlan plan,
                                                   HttpServletRequest request) {
        support.requireApiPermission(require(request), LicenseApi.ACTIVATION_MANAGE);
        grantInScope(plan, grantId);
        java.util.Optional<GrantRevisionEntity> lockedGrant = grants.findWithLockByGrantId(grantId);
        GrantRevisionEntity grant = (lockedGrant != null && lockedGrant.isPresent()
                ? lockedGrant : grants.findById(grantId)).orElseThrow(LicenseHttpException::notFound);
        if (!"DEPLOYMENT".equals(grant.getSubjectKind())) {
            throw LicenseHttpException.unprocessable("仅部署型授权建激活");
        }
        ObjectNode input = support.object(body, "deploymentCode", "grantRevision", "componentDigests",
                ActivationConstant.CALLER_BINDING, "requestedAllocations");
        currentGrantRevision(input, grant);
        SubjectRef caller = callerBinding(input, !"OFFLINE".equals(grant.getMode()));
        ActivationEntity created = activations.saveAndFlush(new ActivationEntity(
                "act-" + UUID.randomUUID(), grantId,
                support.text(input, "deploymentCode", true),
                requireNoDeviceDigests(input), caller == null ? null : caller.getSourceId(),
                caller == null ? null : caller.getSubjectId()));
        persistQuotaAllocations(created, grant, input);
        return ResponseEntity.status(201).body(activationView(created).toString());
    }

    /**
     * 管理面显式登记部署获准服务；API与授权DATA均校验，裸在线请求不具备改绑能力。
     * 存量未绑定行需经此接口补齐，不在运行判定中猜测或自动迁移。
     */
    @RequireApiPermission(LicenseApi.ACTIVATION_MANAGE)
    @PatchMapping("/activations/{activationId}/caller-binding")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    public ResponseEntity<String> patchActivationCaller(@PathVariable String activationId,
                                                       @RequestBody String body,
                                                       @CurrentDataAccessPlan DataAccessPlan plan,
                                                       HttpServletRequest request) {
        LicenseRequestContext context = support.requireApiPermission(require(request), LicenseApi.ACTIVATION_MANAGE);
        ObjectNode input = support.object(body, "expectedRowVersion", ActivationConstant.CALLER_BINDING);
        ActivationEntity entity = activations.findById(activationId).orElseThrow(LicenseHttpException::notFound);
        grantVisible(plan, entity.getGrantId());
        requireActivationVersion(input, entity);
        requireMutableActivation(entity);
        SubjectRef caller = callerBinding(input, true);
        entity.bindCaller(caller.getSourceId(), caller.getSubjectId());
        ActivationEntity committed = activations.saveAndFlush(entity);
        log.info("部署许可调用者已登记：activationId={}，rowVersion={}，requestId={}",
                committed.getActivationId(), committed.getRowVersion(), context.getRequestId());
        return ResponseEntity.ok(activationView(committed).toString());
    }

    @RequireApiPermission(LicenseApi.ACTIVATION_MANAGE)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    @PatchMapping("/activations/{activationId}/state")
    public ResponseEntity<String> patchActivationState(@PathVariable String activationId,
                                                       @RequestBody String body,
                                                       @CurrentDataAccessPlan
                                                               DataAccessPlan plan,
                                                       HttpServletRequest request) {
        require(request);
        ObjectNode input = support.object(body, "state", "expectedRowVersion");
        ActivationEntity entity = activations.findById(activationId)
                .orElseThrow(LicenseHttpException::notFound);
        grantVisible(plan, entity.getGrantId());
        requireActivationVersion(input, entity);
        requireMutableActivation(entity);
        String state = support.text(input, "state", true);
        if (!"ACTIVE".equals(state) && !"SUSPENDED".equals(state)) {
            throw LicenseHttpException.unprocessable("状态仅支持 ACTIVE/SUSPENDED");
        }
        entity.setState(state);
        return ResponseEntity.ok(activationView(activations.save(entity)).toString());
    }

    /** 换人同事务撤旧建新；新资格编号的代数独立从1开始，不能与旧编号比较大小。 */
    @RequireApiPermission(LicenseApi.SUBJECT_ASSIGNMENT_MANAGE)
    @PostMapping("/subject-assignments/{assignmentId}/replacements")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<String> replaceAssignment(@PathVariable String assignmentId, @RequestBody String body,
            @CurrentDataAccessPlan DataAccessPlan plan,
            HttpServletRequest request) {
        support.requireApiPermission(require(request), LicenseApi.SUBJECT_ASSIGNMENT_MANAGE);
        ObjectNode input = support.object(body, "sourceId", "subjectId", "expectedRowVersion");
        SubjectAssignmentEntity old = findAssignment(assignmentId);
        grantVisible(plan, old.getGrantId());
        requireCurrentAssignmentRevision(old);
        requireAssignmentVersion(input, old);
        requireMutableAssignment(old);
        String sourceId = support.text(input, "sourceId", true);
        String subjectId = support.text(input, "subjectId", true);
        old.setState("REPLACED");
        assignments.save(old);
        SubjectAssignmentEntity created = assignments.save(new SubjectAssignmentEntity(
                "sa-" + UUID.randomUUID(), old.getGrantId(), old.getGrantRevision(),
                old.getSubjectKind(), sourceId, subjectId, old.getMetrics()));
        log.debug("固定资格替换已进入事务：previousAssignmentId={}，replacementAssignmentId={}",
                old.getAssignmentId(), created.getAssignmentId());
        return ResponseEntity.status(201).body(assignmentView(created).toString());
    }

    /** 换机（新激活记录替代关系）：旧行转REPLACED，新行代数+1，同部署编码及显式获准调用者。 */
    @RequireApiPermission(LicenseApi.ACTIVATION_MANAGE)
    @PostMapping("/activations/{activationId}/replacements")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<String> replaceActivation(@PathVariable String activationId, @RequestBody String body,
            @CurrentDataAccessPlan DataAccessPlan plan,
            HttpServletRequest request) {
        support.requireApiPermission(require(request), LicenseApi.ACTIVATION_MANAGE);
        ObjectNode input = support.object(body, "componentDigests", "reasonCategory", "expectedRowVersion",
                ActivationConstant.CALLER_BINDING);
        ActivationEntity old = activations.findById(activationId).orElseThrow(LicenseHttpException::notFound);
        grantVisible(plan, old.getGrantId());
        requireActivationVersion(input, old);
        requireMutableActivation(old);
        if (old.getActivationGeneration() == Long.MAX_VALUE) {
            throw LicenseHttpException.conflict(ActivationConstant.INVALID_GENERATION);
        }
        GrantRevisionEntity grant = grants.findById(old.getGrantId()).orElseThrow(LicenseHttpException::notFound);
        SubjectRef caller = input.has(ActivationConstant.CALLER_BINDING)
                ? callerBinding(input, !"OFFLINE".equals(grant.getMode()))
                : old.getCallerSourceId() == null || old.getCallerSubjectId() == null ? null
                : SubjectRef.of(SubjectKind.SERVICE, old.getCallerSourceId(), old.getCallerSubjectId());
        if (caller == null && !"OFFLINE".equals(grant.getMode())) {
            throw LicenseHttpException.unprocessable(ActivationConstant.CALLER_REQUIRED);
        }
        String digests = requireNoDeviceDigests(input);
        old.setState("REPLACED");
        activations.save(old);
        ActivationEntity created = new ActivationEntity(
                "act-" + UUID.randomUUID(), old.getGrantId(), old.getDeploymentCode(),
                digests, caller == null ? null : caller.getSourceId(), caller == null ? null : caller.getSubjectId());
        created.setActivationGeneration(old.getActivationGeneration() + 1);
        ActivationEntity saved = activations.save(created);
        copyQuotaAllocations(old, saved);
        return ResponseEntity.status(201).body(activationView(saved).toString());
    }

    /** 当前只开放NONE设备政策；任何非空摘要都不能被当成已经验证的设备证明。 */
    private static String requireNoDeviceDigests(ObjectNode input) {
        String json = digestsJson(input);
        try {
            if (StrictJson.mapper().readTree(json).size() != 0) {
                throw LicenseHttpException.unprocessable(ActivationConstant.DEVICE_NONE_ONLY);
            }
        } catch (java.io.IOException failure) {
            throw LicenseHttpException.unprocessable(ActivationConstant.DEVICE_NONE_ONLY);
        }
        return json;
    }

    /** 嵌套绑定对象只能声明服务的来源与稳定编号，管理员显式设置不是在线代理证明。 */
    private SubjectRef callerBinding(ObjectNode input, boolean required) {
        com.fasterxml.jackson.databind.JsonNode value = input.get(ActivationConstant.CALLER_BINDING);
        if ((value == null || value.isNull()) && !required) {
            return null;
        }
        if (value == null || !value.isObject()) {
            throw LicenseHttpException.unprocessable(ActivationConstant.INVALID_CALLER_BINDING);
        }
        ObjectNode binding = support.object(value.toString(), ActivationConstant.SOURCE_ID, ActivationConstant.SUBJECT_ID);
        String sourceId = support.text(binding, ActivationConstant.SOURCE_ID, true);
        String subjectId = support.text(binding, ActivationConstant.SUBJECT_ID, true);
        try {
            if (!sourceId.equals(sourceId.trim()) || !subjectId.equals(subjectId.trim())) {
                throw new IllegalArgumentException(ActivationConstant.INVALID_CALLER_BINDING);
            }
            return SubjectRef.of(SubjectKind.SERVICE, sourceId, subjectId);
        } catch (IllegalArgumentException failure) {
            throw LicenseHttpException.unprocessable(ActivationConstant.INVALID_CALLER_BINDING);
        }
    }

    private static void requireActivationVersion(ObjectNode input, ActivationEntity entity) {
        com.fasterxml.jackson.databind.JsonNode version = input.get("expectedRowVersion");
        if (version == null || !version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0) {
            throw LicenseHttpException.unprocessable(ActivationConstant.INVALID_VERSION);
        }
        if (version.longValue() != entity.getRowVersion()) {
            throw LicenseHttpException.conflict("版本已变化，请回读后重试");
        }
    }

    /** 不将文本、null、小数或溢出值宽松转换为版本0，避免绕过初始行版本的围栏。 */
    private static void requireAssignmentVersion(ObjectNode input, SubjectAssignmentEntity entity) {
        com.fasterxml.jackson.databind.JsonNode version = input.get("expectedRowVersion");
        if (version == null || !version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0) {
            throw LicenseHttpException.unprocessable(SubjectAssignmentConstant.INVALID_VERSION);
        }
        if (version.longValue() != entity.getRowVersion()) {
            throw LicenseHttpException.conflict("版本已变化，请回读后重试");
        }
    }

    /**
     * 按已发布模型登记部署额度；请求只提交指标与数量，分配编号、修订和窗口锚由服务端固化。
     * LOCAL 指标必须逐项登记，不能用在线余额或权益声明替代部署事实。
     */
    private void persistQuotaAllocations(ActivationEntity activation, GrantRevisionEntity grant,
                                         ObjectNode input) {
        com.fasterxml.jackson.databind.JsonNode requested = input.get("requestedAllocations");
        if (requested == null || requested.isNull()) {
            requested = StrictJson.mapper().createArrayNode();
        }
        if (!requested.isArray() || requested.size() > 128) {
            throw LicenseHttpException.unprocessable("requestedAllocations 必须为不超过128项的数组");
        }
        if (quotaAllocations == null) {
            if (requested.size() > 0) {
                throw LicenseHttpException.dependencyUnavailable("额度分配台账未装配");
            }
            return;
        }
        if (models == null || grant.getModelVersionId() == null || grant.getModelVersionId().isEmpty()) {
            if (requested.size() > 0) {
                throw LicenseHttpException.unprocessable("本地额度激活缺少已发布模型");
            }
            return;
        }
        // 额度总量是授权级事实；在同一事务内锁住授权主行，避免并发激活同时通过总量检查。
        java.util.Optional<GrantRevisionEntity> lockedGrant = grants.findWithLockByGrantId(grant.getGrantId());
        if (lockedGrant != null && lockedGrant.isPresent()) {
            grant = lockedGrant.get();
        }
        ModelVersionEntity model = models.findById(grant.getModelVersionId())
                .orElseThrow(LicenseHttpException::notFound);
        if (!"PUBLISHED".equals(model.getStatus())) {
            throw LicenseHttpException.unprocessable("本地额度只能引用已发布模型");
        }
        ObjectNode policy = ModelPolicyWire.parse(model.getSnapshot());
        ObjectNode definitions = (ObjectNode) policy.get("quotaDefinitions");
        if (definitions == null) {
            throw LicenseHttpException.unprocessable("模型缺少额度定义");
        }
        Map<String, JsonNode> localDefinitions = new HashMap<String, JsonNode>();
        Iterator<Map.Entry<String, JsonNode>> definitionIterator = definitions.fields();
        while (definitionIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = definitionIterator.next();
            if ("LOCAL".equals(entry.getValue().path("enforcement").asText())) {
                localDefinitions.put(entry.getKey(), entry.getValue());
            }
        }
        if (localDefinitions.isEmpty() && requested.size() > 0) {
            throw LicenseHttpException.unprocessable("当前模型没有本地额度指标");
        }
        if (!localDefinitions.isEmpty() && requested.size() == 0) {
            throw LicenseHttpException.unprocessable("本地额度激活必须提交完整分配");
        }
        Map<String, Long> used = activeAllocationTotals(grant.getGrantId());
        Set<String> seen = new HashSet<String>();
        List<ActivationQuotaAllocationEntity> created = new ArrayList<ActivationQuotaAllocationEntity>();
        for (JsonNode value : requested) {
            if (!value.isObject()) {
                throw LicenseHttpException.unprocessable("额度分配项必须为对象");
            }
            ObjectNode allocation = (ObjectNode) value;
            requireAllocationFields(allocation);
            String metric = text(allocation, "metric");
            JsonNode definition = localDefinitions.get(metric);
            if (definition == null || !seen.add(metric)) {
                throw LicenseHttpException.unprocessable("额度分配指标不是模型声明的本地指标或重复");
            }
            long quantity = positiveLong(allocation, "quantity");
            String retention = text(allocation, "retention");
            Long retainUntil = optionalPositiveLong(allocation, "retainUntil");
            if ("FIXED".equals(retention) && retainUntil == null) {
                throw LicenseHttpException.unprocessable("固定额度分配必须提供 retainUntil");
            }
            if ("PERPETUAL".equals(retention) && allocation.has("retainUntil")) {
                throw LicenseHttpException.unprocessable("永久额度分配不得提供 retainUntil");
            }
            if (!"FIXED".equals(retention) && !"PERPETUAL".equals(retention)) {
                throw LicenseHttpException.unprocessable("retention 仅支持 FIXED/PERPETUAL");
            }
            long limit = rightLimit(grant, definition);
            long previous = used.containsKey(metric) ? used.get(metric) : 0L;
            if (previous > Long.MAX_VALUE - quantity) {
                throw LicenseHttpException.conflict("本地额度分配超过授权限额");
            }
            long total = addAllocationTotal(previous, quantity);
            if (total > limit) {
                throw LicenseHttpException.conflict("本地额度分配超过授权限额");
            }
            used.put(metric, total);
            Long windowAnchor = "FIXED_WINDOW".equals(definition.path("window").asText())
                    ? grant.getStartsAt() : null;
            if ("FIXED_WINDOW".equals(definition.path("window").asText())
                    && (windowAnchor == null || windowAnchor <= 0L)) {
                throw LicenseHttpException.unprocessable("固定窗口额度缺少授权级窗口锚");
            }
            created.add(new ActivationQuotaAllocationEntity(
                    "alloc-" + UUID.randomUUID(), grant.getGrantId(), activation.getActivationId(),
                    grant.getGrantRevision(), metric, quantity, retention, retainUntil, windowAnchor));
        }
        if (!seen.equals(localDefinitions.keySet())) {
            throw LicenseHttpException.unprocessable("本地额度分配必须覆盖模型声明的全部指标");
        }
        quotaAllocations.saveAll(created);
        quotaAllocations.flush();
    }

    /** 替换激活沿用既有分配快照；旧激活自身保留历史，新的签发只绑定新代数。 */
    private void copyQuotaAllocations(ActivationEntity oldActivation, ActivationEntity newActivation) {
        if (quotaAllocations == null) {
            return;
        }
        List<ActivationQuotaAllocationEntity> source = quotaAllocations
                .findByActivationIdOrderByMetricAsc(oldActivation.getActivationId());
        if (source == null || source.isEmpty()) {
            return;
        }
        List<ActivationQuotaAllocationEntity> copied = new ArrayList<ActivationQuotaAllocationEntity>();
        for (ActivationQuotaAllocationEntity item : source) {
            copied.add(new ActivationQuotaAllocationEntity(
                    "alloc-" + UUID.randomUUID(), item.getGrantId(), newActivation.getActivationId(),
                    item.getGrantRevision(), item.getMetric(), item.getQuantity(), item.getRetention(),
                    item.getRetainUntil(), item.getWindowAnchorAt()));
        }
        quotaAllocations.saveAll(copied);
        quotaAllocations.flush();
    }

    private Map<String, Long> activeAllocationTotals(String grantId) {
        Map<String, Long> totals = new HashMap<String, Long>();
        List<ActivationEntity> rows = activations.findByGrantId(grantId);
        if (rows == null) {
            return totals;
        }
        for (ActivationEntity activation : rows) {
            if (!"ACTIVE".equals(activation.getState()) && !"SUSPENDED".equals(activation.getState())) {
                continue;
            }
            List<ActivationQuotaAllocationEntity> values = quotaAllocations
                    .findByActivationIdOrderByMetricAsc(activation.getActivationId());
            if (values == null) {
                continue;
            }
            for (ActivationQuotaAllocationEntity value : values) {
                Long previous = totals.get(value.getMetric());
                totals.put(value.getMetric(), addAllocationTotal(
                        previous == null ? 0L : previous, value.getQuantity()));
            }
        }
        return totals;
    }

    /** 历史分配汇总也必须失败关闭，不能让 long 溢出绕过授权限额。 */
    private static long addAllocationTotal(long previous, long quantity) {
        if (previous < 0L || quantity < 0L || previous > Long.MAX_VALUE - quantity) {
            throw LicenseHttpException.conflict("本地额度分配总量溢出");
        }
        return previous + quantity;
    }

    private static long rightLimit(GrantRevisionEntity grant, JsonNode definition) {
        String limitRight = definition.path("limitRight").asText("");
        try {
            JsonNode rights = StrictJson.mapper().readTree(grant.getRightsJson());
            JsonNode value = rights == null ? null : rights.get(limitRight);
            if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0L) {
                throw new IllegalArgumentException();
            }
            return value.longValue();
        } catch (Exception failure) {
            throw LicenseHttpException.unprocessable("本地额度缺少有效的授权限额");
        }
    }

    private static void requireAllocationFields(ObjectNode allocation) {
        Set<String> fields = new HashSet<String>();
        Iterator<String> iterator = allocation.fieldNames();
        while (iterator.hasNext()) {
            fields.add(iterator.next());
        }
        Set<String> allowed = new HashSet<String>(java.util.Arrays.asList(
                "metric", "quantity", "retention", "retainUntil"));
        if (!allowed.containsAll(fields) || !fields.contains("metric") || !fields.contains("quantity")
                || !fields.contains("retention")) {
            throw LicenseHttpException.unprocessable("额度分配字段不完整");
        }
    }

    private static String text(ObjectNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.asText().isEmpty() || value.asText().length() > 128) {
            throw LicenseHttpException.unprocessable("额度分配字段形态非法：" + field);
        }
        return value.asText();
    }

    private static long positiveLong(ObjectNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1L) {
            throw LicenseHttpException.unprocessable("额度分配字段形态非法：" + field);
        }
        return value.longValue();
    }

    private static Long optionalPositiveLong(ObjectNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1L) {
            throw LicenseHttpException.unprocessable("额度分配字段形态非法：" + field);
        }
        return value.longValue();
    }

    /** 省略修订使用当前值；显式请求只能引用当前修订，不能创建未来或过时资格。 */
    private static long currentGrantRevision(ObjectNode input, GrantRevisionEntity grant) {
        com.fasterxml.jackson.databind.JsonNode revision = input.get("grantRevision");
        if (revision == null) {
            return grant.getGrantRevision();
        }
        if (!revision.isIntegralNumber() || !revision.canConvertToLong() || revision.longValue() < 1) {
            throw LicenseHttpException.unprocessable(SubjectAssignmentConstant.INVALID_GRANT_REVISION);
        }
        if (revision.longValue() != grant.getGrantRevision()) {
            throw LicenseHttpException.conflict(SubjectAssignmentConstant.STALE_GRANT_REVISION);
        }
        return revision.longValue();
    }

    /** 固定资格是授权修订的快照；授权已修订后，旧资格只能保留历史，不能继续治理或消费。 */
    private void requireCurrentAssignmentRevision(SubjectAssignmentEntity assignment) {
        GrantRevisionEntity grant = grants.findById(assignment.getGrantId())
                .orElseThrow(LicenseHttpException::notFound);
        if (assignment.getGrantRevision() != grant.getGrantRevision()) {
            throw LicenseHttpException.conflict(SubjectAssignmentConstant.STALE_GRANT_REVISION);
        }
    }

    /** 终态保留历史；新分配通过新编号建立，不能借状态治理恢复旧资格。 */
    private static void requireMutableAssignment(SubjectAssignmentEntity entity) {
        if (!"ACTIVE".equals(entity.getState()) && !"SUSPENDED".equals(entity.getState())) {
            log.debug("拒绝治理终态固定资格：assignmentId={}，state={}", entity.getAssignmentId(), entity.getState());
            throw LicenseHttpException.conflict(SubjectAssignmentConstant.TERMINAL_ASSIGNMENT);
        }
    }

    /** 终态对象保留历史，不借caller治理或重复换机重新取得可用记录。 */
    private static void requireMutableActivation(ActivationEntity entity) {
        if (!"ACTIVE".equals(entity.getState()) && !"SUSPENDED".equals(entity.getState())) {
            throw LicenseHttpException.conflict(ActivationConstant.TERMINAL_ACTIVATION);
        }
    }

    // ===== helpers =====

    /** 授权子资源（资格/激活）的 license-grant 维度 DATA 范围校验。 */
    private void grantInScope(DataAccessPlan plan,
                              String grantId) {
        LicenseAccessScope.from(plan, "grantId")
                .requireVisible(grantId);
    }

    private void grantVisible(DataAccessPlan plan, String grantId) {
        LicenseAccessScope.from(plan, "grantId")
                .requireVisible(grantId);
    }

    private LicenseRequestContext require(HttpServletRequest request) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return context;
    }

    private SubjectAssignmentEntity findAssignment(String assignmentId) {
        return assignments.findById(assignmentId).orElseThrow(LicenseHttpException::notFound);
    }

    private static String metricsJson(ObjectNode input) {
        com.fasterxml.jackson.databind.JsonNode value = input.get("requestedNamedMetrics");
        if (value == null || !value.isArray()) {
            return "[]";
        }
        return value.toString();
    }

    private static Set<String> metricsSet(String json) {
        Set<String> values = new HashSet<>();
        try {
            for (com.fasterxml.jackson.databind.JsonNode item : StrictJson.mapper().readTree(json)) {
                values.add(item.asText());
            }
        } catch (Exception failure) {
            throw LicenseHttpException.dependencyUnavailable("服务暂不可用");
        }
        return values;
    }

    private static String digestsJson(ObjectNode input) {
        com.fasterxml.jackson.databind.JsonNode value = input.get("componentDigests");
        if (value == null || value.isNull()) {
            return "{}";
        }
        if (!value.isObject()) {
            throw LicenseHttpException.unprocessable("componentDigests 必须为对象（分量名→摘要）");
        }
        return value.toString();
    }

    private static ObjectNode assignmentView(SubjectAssignmentEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("assignmentId", entity.getAssignmentId());
        view.put("grantId", entity.getGrantId());
        ObjectNode ref = view.putObject("subjectRef");
        ref.put("kind", entity.getSubjectKind());
        ref.put("sourceId", entity.getSourceId());
        ref.put("subjectId", entity.getSubjectId());
        view.put("assignmentGeneration", entity.getAssignmentGeneration());
        view.put("state", entity.getState());
        view.set("metrics", StrictJson.mapper().valueToTree(metricsSet(entity.getMetrics())));
        view.put("rowVersion", entity.getRowVersion());
        return view;
    }

    private ObjectNode activationView(ActivationEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("activationId", entity.getActivationId());
        view.put("grantId", entity.getGrantId());
        view.put("deploymentCode", entity.getDeploymentCode());
        view.put("activationGeneration", entity.getActivationGeneration());
        if (entity.getCallerSourceId() == null || entity.getCallerSubjectId() == null) {
            view.putNull(ActivationConstant.CALLER_BINDING);
        } else {
            ObjectNode caller = view.putObject(ActivationConstant.CALLER_BINDING);
            caller.put(ActivationConstant.SOURCE_ID, entity.getCallerSourceId());
            caller.put(ActivationConstant.SUBJECT_ID, entity.getCallerSubjectId());
        }
        view.put("deviceBindingMode", "NONE");
        view.put("state", entity.getState());
        ArrayNode allocationNodes = view.putArray("requestedAllocations");
        if (quotaAllocations != null) {
            List<ActivationQuotaAllocationEntity> values = quotaAllocations
                    .findByActivationIdOrderByMetricAsc(entity.getActivationId());
            if (values != null) {
                for (ActivationQuotaAllocationEntity value : values) {
                    ObjectNode allocation = allocationNodes.addObject();
                    allocation.put("allocationId", value.getAllocationId());
                    allocation.put("metric", value.getMetric());
                    allocation.put("quantity", value.getQuantity());
                    allocation.put("grantRevision", value.getGrantRevision());
                    allocation.put("retention", value.getRetention());
                    if (value.getRetainUntil() != null) {
                        allocation.put("retainUntil", value.getRetainUntil());
                    }
                    if (value.getWindowAnchorAt() == null) {
                        allocation.putNull("windowAnchorAt");
                    } else {
                        allocation.put("windowAnchorAt", value.getWindowAnchorAt());
                    }
                }
            }
        }
        view.put("rowVersion", entity.getRowVersion());
        return view;
    }
}
