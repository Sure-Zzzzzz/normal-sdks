package io.github.surezzzzzz.sdk.license.server.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.claim.DataGrantDocumentClaimMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionValidationException;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.core.spi.RoleProjectionPort;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.GrantState;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationConstant;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationTargetKind;
import io.github.surezzzzzz.sdk.license.server.constant.RoleProjectionConstant;
import io.github.surezzzzzz.sdk.license.server.entity.CustomerEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.IntegrationPlanEntity;
import io.github.surezzzzzz.sdk.license.server.entity.IntegrationRequestEntity;
import io.github.surezzzzzz.sdk.license.server.entity.IntegrationState;
import io.github.surezzzzzz.sdk.license.server.entity.OrganizationBindingEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RoleProjectionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import io.github.surezzzzzz.sdk.license.server.event.RoleProjectionChanged;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.IntegrationPlanRepository;
import io.github.surezzzzzz.sdk.license.server.repository.IntegrationRequestRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RoleProjectionRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.SubjectAdmissionService;
import io.github.surezzzzzz.sdk.license.server.support.GrantValidityHelper;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 专用角色协调：商业事实、批准规则、环境绑定与真实 IAM 观察共同产生计划。
 * 每次显式执行持有本地目标锁，写后回读并保存围栏；未知写入立即停止该角色。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
@SimpleLicenseServerComponent
public class RoleProjectionService {
    private final IntegrationPlanRepository plans;
    private final RoleProjectionRepository projections;
    private final GrantRepository grants;
    private final SubjectAssignmentRepository assignments;
    private final LicenseRevocationGuard revocations;
    private final SubjectAdmissionService admission;
    private final ObjectProvider<RoleProjectionPort> ports;
    private final PlatformTransactionManager transactionManager;
    private final IntegrationRequestRepository requests;
    private final ApplicationEventPublisher events;
    @PersistenceContext
    private EntityManager entityManager;

    /** 创建仅回读的真实清单；不创建 IAM 角色，不写远端权限。 */
    @Transactional(noRollbackFor = GovernanceException.class)
    public IntegrationPlanEntity create(String customerId, String productId, long applicationId,
                                         ObjectNode explicitRules) {
        return create(customerId, productId, applicationId, explicitRules, null, null);
    }

    /** 同键同输入回读首份计划，同键异输入拒绝，不因重放推进期望修订。 */
    @Transactional(noRollbackFor = GovernanceException.class)
    public IntegrationPlanEntity create(String customerId, String productId, long applicationId,
                                         ObjectNode explicitRules, String requestDigest, String inputDigest) {
        RoleProjectionEntity projection = lock(customerId, productId, applicationId);
        IntegrationPlanEntity replay = replay(requestDigest, inputDigest);
        if (replay != null) { return replay; }
        IntegrationPlanEntity created = createLocked(projection, explicitRules);
        remember(requestDigest, inputDigest, created);
        return created;
    }

    /** 人工回执须核环境与绑定、实际专用角色身份及执行后的规则/部门，仍标人工确认。 */
    @Transactional(noRollbackFor = GovernanceException.class)
    public IntegrationPlanEntity confirm(String planId, long expectedRevision, String environment,
                                          int generation, String openRoleId) {
        IntegrationPlanEntity plan = plans.findById(planId).orElseThrow(LicenseHttpException::notFound);
        RoleProjectionEntity projection = lock(plan.getCustomerId(), plan.getProductId(),
                positiveId(plan.getApplicationId()));
        ObjectNode snapshot = snapshot(plan);
        requireLatest(projection, plan, expectedRevision, environment, generation);
        ObjectNode current = prepare(projection, object(snapshot.get("approvedRules")), true);
        requireExpectation(snapshot, current);
        String roleId = projection.getOpenRoleId() == null ? openRoleId : projection.getOpenRoleId();
        if (roleId == null || roleId.trim().isEmpty()) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.STALE_PLAN);
        }
        RoleProjectionPort.Role actual = port().read(roleId);
        requireIdentity(projection, actual);
        requireDesired(current, actual);
        projection.bind(actual.getOpenRoleId(), actual.getCode());
        acknowledge(projection, actual);
        plan.confirmManually(Instant.now().getEpochSecond(), expectedRevision);
        projection.mark(IntegrationState.MANUALLY_CONFIRMED.getCode());
        scheduleEvent(projection, plan);
        log.debug("角色联动人工回执记录：projectionId={}，revision={}", projection.getProjectionId(), expectedRevision);
        return plans.save(plan);
    }

    /**
     * 人工收口未知/漂移：治理者先核实此前调用已终止，再要求实际角色匹配最新商业期望。
     * 该声明是人工证据，不冒充身份服务提供的执行状态；本方法不发任何远端写入。
     */
    @Transactional(noRollbackFor = GovernanceException.class)
    public IntegrationPlanEntity reconcile(String commandId, long expectedRevision, String environment,
                                            int generation, String openRoleId, boolean priorCallTerminated) {
        if (!priorCallTerminated) { throw LicenseHttpException.conflict(RoleProjectionConstant.UNSAFE_ROLE); }
        IntegrationPlanEntity previous = plans.findByCommandId(commandId).orElseThrow(LicenseHttpException::notFound);
        RoleProjectionEntity projection = lock(previous.getCustomerId(), previous.getProductId(), positiveId(previous.getApplicationId()));
        if (expectedRevision != previous.getDesiredRevision() || expectedRevision != projection.getDesiredRevision()
                || !projection.getOrganizationInstanceId().equals(environment) || generation != projection.getBindingGeneration()
                || !IntegrationState.UNCERTAIN.getCode().equals(projection.getState())
                && !IntegrationState.DRIFTED.getCode().equals(projection.getState())) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.STALE_PLAN);
        }
        ObjectNode stored = snapshot(previous);
        ObjectNode current = prepare(projection, object(stored.get("approvedRules")), true);
        String roleId = projection.getOpenRoleId() == null ? openRoleId : projection.getOpenRoleId();
        if (roleId == null || roleId.trim().isEmpty()) { throw LicenseHttpException.conflict(RoleProjectionConstant.UNSAFE_ROLE); }
        RoleProjectionPort.Role actual = port().read(roleId);
        requireIdentity(projection, actual);
        requireDesired(current, actual);
        projection.bind(actual.getOpenRoleId(), actual.getCode());
        acknowledge(projection, actual);
        projection.mark(IntegrationState.MANUALLY_CONFIRMED.getCode());
        IntegrationPlanEntity receipt = createLocked(projection, object(stored.get("approvedRules")));
        receipt.confirmManually(Instant.now().getEpochSecond(), receipt.getDesiredRevision());
        scheduleEvent(projection, receipt);
        log.debug("角色未知/漂移经人工收口：projectionId={}，previousRevision={}，receiptRevision={}",
                projection.getProjectionId(), expectedRevision, receipt.getDesiredRevision());
        return plans.save(receipt);
    }

    /**
     * 已知身份环境更换后的显式重确认；验证当前绑定及目录，生成新的外部身份和新计划。
     * 旧角色留历史，本方法不会按旧编号访问或删除远端对象。
     */
    @Transactional
    public IntegrationPlanEntity reconfirmEnvironment(String customerId, String productId, long applicationId,
                                                       String environment, int generation, ObjectNode rules,
                                                       boolean priorCallTerminated) {
        if (!priorCallTerminated) { throw LicenseHttpException.conflict(RoleProjectionConstant.UNSAFE_ROLE); }
        CustomerEntity customer = entityManager.find(CustomerEntity.class, customerId, LockModeType.PESSIMISTIC_WRITE);
        OrganizationBindingEntity binding = entityManager.find(OrganizationBindingEntity.class, customerId, LockModeType.PESSIMISTIC_READ);
        ProductEntity product = entityManager.find(ProductEntity.class, productId, LockModeType.PESSIMISTIC_READ);
        if (customer == null || product == null || binding == null || customer.getStatus() != 1 || product.getStatus() != 1
                || !Objects.equals(environment, binding.getOrganizationInstanceId()) || generation != binding.getBindingGeneration()) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.INVALID_BINDING);
        }
        bindingRules(product, applicationId);
        RoleProjectionEntity projection = projections.lockTarget(customerId, productId, applicationId)
                .orElseThrow(LicenseHttpException::notFound);
        if (Objects.equals(environment, projection.getOrganizationInstanceId())
                && generation == projection.getBindingGeneration() && binding.getRootDepartmentId() == projection.getRootDepartmentId()) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.STALE_PLAN);
        }
        projection.reconfirmEnvironment(binding, UUID.randomUUID().toString());
        return createLocked(projection, rules);
    }

    /** MANUAL 刷新最新清单；AUTO 仅经显式选择，不启动后台调度。 */
    public IntegrationPlanEntity attempt(String commandId, String executionMode) {
        return attempt(commandId, executionMode, null, null);
    }

    /** 显式执行也按请求键复用结果；首次AUTO占位与请求台账同事务先提交。 */
    public IntegrationPlanEntity attempt(String commandId, String executionMode, String requestDigest, String inputDigest) {
        if (RoleProjectionConstant.MANUAL.equals(executionMode)) {
            return transaction().execute(status -> {
                IntegrationPlanEntity source = plans.findByCommandId(commandId).orElseThrow(LicenseHttpException::notFound);
                lock(source.getCustomerId(), source.getProductId(), positiveId(source.getApplicationId()));
                IntegrationPlanEntity prior = replay(requestDigest, inputDigest);
                if (prior != null) { return prior; }
                IntegrationPlanEntity result = attemptLocked(commandId, executionMode, null);
                remember(requestDigest, inputDigest, result);
                return result;
            });
        }
        if (!RoleProjectionConstant.AUTO.equals(executionMode)) {
            throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES);
        }
        String executionToken = UUID.randomUUID().toString();
        boolean[] ownsReservation = {false};
        // 必须先提交占位：崩溃、数据库最终提交失败、写后无法回读均保留未知，不能自动再发。
        IntegrationPlanEntity reserved = transaction().execute(status -> {
            IntegrationPlanEntity pending = plans.findByCommandId(commandId).orElseThrow(LicenseHttpException::notFound);
            RoleProjectionEntity projection = lock(pending.getCustomerId(), pending.getProductId(), positiveId(pending.getApplicationId()));
            IntegrationPlanEntity prior = replay(requestDigest, inputDigest);
            if (prior != null) { return prior; }
            requireLatest(projection, pending, pending.getDesiredRevision(), projection.getOrganizationInstanceId(), projection.getBindingGeneration());
            if (!IntegrationState.PENDING_EXECUTION.getCode().equals(pending.getState())) {
                throw LicenseHttpException.conflict(RoleProjectionConstant.STALE_PLAN);
            }
            ObjectNode stored = snapshot(pending);
            requireExpectation(stored, prepare(projection, object(stored.get("approvedRules"))));
            projection.mark(IntegrationState.UNCERTAIN.getCode());
            pending.execution(RoleProjectionConstant.AUTO, IntegrationState.UNCERTAIN, executionToken);
            projections.saveAndFlush(projection);
            plans.saveAndFlush(pending);
            remember(requestDigest, inputDigest, pending);
            scheduleEvent(projection, pending);
            ownsReservation[0] = true;
            return pending;
        });
        if (!ownsReservation[0]) { return reserved; }
        return transaction().execute(status -> {
            IntegrationPlanEntity result = attemptLocked(commandId, executionMode, executionToken);
            remember(requestDigest, inputDigest, result);
            return result;
        });
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private IntegrationPlanEntity attemptLocked(String commandId, String executionMode, String executionToken) {
        IntegrationPlanEntity plan = plans.findByCommandId(commandId).orElseThrow(LicenseHttpException::notFound);
        RoleProjectionEntity projection = lock(plan.getCustomerId(), plan.getProductId(),
                positiveId(plan.getApplicationId()));
        ObjectNode stored = snapshot(plan);
        if (RoleProjectionConstant.MANUAL.equals(executionMode)) {
            return createLocked(projection, object(stored.get("approvedRules")));
        }
        if (!RoleProjectionConstant.AUTO.equals(executionMode)) {
            throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES);
        }
        if (executionToken == null || !executionToken.equals(plan.getFailureReason())
                || !IntegrationState.UNCERTAIN.getCode().equals(plan.getState())
                || !IntegrationState.UNCERTAIN.getCode().equals(projection.getState())) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.STALE_PLAN);
        }
        // 仅独立占位令牌持有者可在本事务中继续；其他实例仍观察到已提交的 UNCERTAIN。
        projection.mark(IntegrationState.PENDING_EXECUTION.getCode());
        plan.execution(RoleProjectionConstant.AUTO, IntegrationState.PENDING_EXECUTION, null);
        try {
            ObjectNode current = prepare(projection, object(stored.get("approvedRules")));
            requireExpectation(stored, current);
            RoleProjectionPort.Role role = projection.getOpenRoleId() == null ? null : port().read(projection.getOpenRoleId());
            if (!current.path("contributionPresent").asBoolean() && role == null) {
                // 无商业贡献且无角色时，不创建空角色，也不执行任何身份写入。
                complete(projection, plan);
                scheduleEvent(projection, plan);
                return plans.save(plan);
            }
            if (role == null) {
                role = port().create(projection.getExternalId(), projection.getApplicationId(),
                        projection.getRootDepartmentId(), "lic_" + projection.getExternalId());
                requireIdentity(projection, role);
                // 幂等创建返回的已有对象必须是无未知贡献的初始对象，禁止接管旧外部对象。
                if (role.getRulesJson() != null || !role.getDepartmentIds().isEmpty()) {
                    throw LicenseHttpException.conflict(RoleProjectionConstant.UNSAFE_ROLE);
                }
                projection.bind(role.getOpenRoleId(), role.getCode());
                acknowledge(projection, role);
            } else {
                requireIdentity(projection, role);
                requireNoDrift(projection, role);
            }
            if (current.path("contributionPresent").asBoolean()) {
                if (!rulesEqual(role.getRulesJson(), current.path("desiredRules"))) {
                    port().putRules(role.getOpenRoleId(), projection.getApplicationId(), role.getRevision(),
                            current.path("manifestVersion").longValue(), current.path("manifestDigest").textValue(),
                            current.path("desiredRules").toString());
                    role = afterWrite(projection, current.path("desiredRules").toString(), role.getDepartmentIds(), role.getRevision());
                }
            } else if (role.getRulesJson() != null) {
                port().deleteRules(role.getOpenRoleId(), projection.getApplicationId(), role.getRevision());
                role = afterWrite(projection, null, role.getDepartmentIds(), role.getRevision());
            }
            List<Long> desired = ids(current.get("targetDepartmentIds"));
            // 先收缩旧挂载，再增加当前批准挂载，每次回读新的远端修订。
            for (Long departmentId : new ArrayList<>(role.getDepartmentIds())) {
                if (!desired.contains(departmentId)) {
                    port().unmount(role.getOpenRoleId(), departmentId, role.getRevision());
                    List<Long> expected = new ArrayList<>(role.getDepartmentIds()); expected.remove(departmentId);
                    role = afterWrite(projection, role.getRulesJson(), expected, role.getRevision());
                }
            }
            for (Long departmentId : desired) {
                if (!role.getDepartmentIds().contains(departmentId)) {
                    port().mount(role.getOpenRoleId(), departmentId, role.getRevision());
                    List<Long> expected = new ArrayList<>(role.getDepartmentIds()); expected.add(departmentId);
                    role = afterWrite(projection, role.getRulesJson(), expected, role.getRevision());
                }
            }
            // 远端写入不是商业事务：完成前再核商业、环境、manifest和目录，变化则停止新增写。
            ObjectNode latest = prepare(projection, object(stored.get("approvedRules")));
            requireExpectation(current, latest);
            requireDesired(latest, role);
            complete(projection, plan);
        } catch (RoleProjectionPort.Failure failure) {
            if (failure.getKind() == RoleProjectionPort.FailureKind.REVISION_CONFLICT
                    || failure.getKind() == RoleProjectionPort.FailureKind.MANIFEST_CONFLICT) {
                // 412/409 只重读重算成新清单，绝不在原请求中再写远端。
                try {
                    return createLocked(projection, object(stored.get("approvedRules")));
                } catch (RuntimeException refreshFailure) {
                    stop(projection, plan, IntegrationState.DRIFTED, failure.getKind().name());
                }
            } else {
                // 明确拒绝并非结果未知；保留待执行与固定原因，允许治理者修正授权后重新生成清单。
                stop(projection, plan, failure.getKind() == RoleProjectionPort.FailureKind.REJECTED
                        ? IntegrationState.PENDING_EXECUTION : IntegrationState.UNCERTAIN, failure.getKind().name());
            }
        } catch (LicenseHttpException failure) {
            stop(projection, plan, IntegrationState.DRIFTED, "BUSINESS_OR_ROLE_CHANGED");
        } catch (RuntimeException failure) {
            stop(projection, plan, IntegrationState.UNCERTAIN, "EXECUTION_RESULT_UNKNOWN");
        }
        log.debug("角色联动执行结束：projectionId={}，revision={}，state={}",
                projection.getProjectionId(), plan.getDesiredRevision(), plan.getState());
        scheduleEvent(projection, plan);
        return plans.save(plan);
    }

    private IntegrationPlanEntity createLocked(RoleProjectionEntity projection, ObjectNode rules) {
        requireSafe(projection);
        ObjectNode prepared = prepare(projection, rules);
        long revision = projection.nextRevision();
        for (IntegrationPlanEntity old : plans.findByCustomerIdAndProductIdAndApplicationIdOrderByDesiredRevisionDesc(
                projection.getCustomerId(), projection.getProductId(), String.valueOf(projection.getApplicationId()))) {
            old.supersede();
            plans.save(old);
        }
        IntegrationPlanEntity plan = new IntegrationPlanEntity("rpp-" + UUID.randomUUID(), "ic-" + UUID.randomUUID(),
                projection.getCustomerId(), projection.getProductId(), String.valueOf(projection.getApplicationId()),
                prepared.path("desiredRules").toString(), prepared.path("departmentDiff").toString(),
                revision, prepared.path("validUntil").longValue());
        prepared.put("desiredRevision", revision);
        plan.snapshot(prepared.toString());
        projections.save(projection);
        scheduleEvent(projection, plan);
        log.debug("角色联动清单生成：projectionId={}，revision={}，contributionPresent={}",
                projection.getProjectionId(), revision, prepared.path("contributionPresent").asBoolean());
        return plans.save(plan);
    }

    private RoleProjectionEntity lock(String customerId, String productId, long applicationId) {
        // 首行尚未存在时先锁客户，避免两个实例同时建立同目标台账；唯一键仍作最终兜底。
        CustomerEntity customer = entityManager.find(CustomerEntity.class, customerId, LockModeType.PESSIMISTIC_WRITE);
        ProductEntity product = entityManager.find(ProductEntity.class, productId, LockModeType.PESSIMISTIC_READ);
        OrganizationBindingEntity binding = entityManager.find(OrganizationBindingEntity.class, customerId,
                LockModeType.PESSIMISTIC_READ);
        if (customer == null || product == null) { throw LicenseHttpException.notFound(); }
        if (customer.getStatus() != 1 || product.getStatus() != 1 || binding == null
                || binding.getRootDepartmentId() <= 0 || binding.getOrganizationInstanceId() == null
                || binding.getOrganizationInstanceId().trim().isEmpty()) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.INVALID_BINDING);
        }
        bindingRules(product, applicationId);
        RoleProjectionEntity projection = projections.lockTarget(customerId, productId, applicationId).orElse(null);
        if (projection == null) {
            projection = projections.saveAndFlush(new RoleProjectionEntity(UUID.randomUUID().toString(),
                    customerId, productId, applicationId, binding));
        }
        if (!Objects.equals(binding.getOrganizationInstanceId(), projection.getOrganizationInstanceId())
                || binding.getBindingGeneration() != projection.getBindingGeneration()
                || binding.getRootDepartmentId() != projection.getRootDepartmentId()) {
            projection.mark(IntegrationState.ENVIRONMENT_RECONFIRM_REQUIRED.getCode());
            projections.save(projection);
            throw new GovernanceException(RoleProjectionConstant.STALE_PLAN);
        }
        return projection;
    }

    private ObjectNode prepare(RoleProjectionEntity projection, ObjectNode explicitRules) {
        return prepare(projection, explicitRules, false);
    }

    private ObjectNode prepare(RoleProjectionEntity projection, ObjectNode explicitRules, boolean manualReceipt) {
        RoleProjectionPort.Manifest manifest = port().manifest(projection.getApplicationId());
        ProductEntity product = entityManager.find(ProductEntity.class, projection.getProductId());
        ObjectNode binding = bindingRules(product, projection.getApplicationId());
        ObjectNode approved = normalize(explicitRules == null ? object(binding.get("basePermissionSnapshot")) : explicitRules);
        if (!manifest.getPagePermissions().containsAll(strings(approved.path("pagePermissions")))
                || !manifest.getApiPermissions().containsAll(strings(approved.path("apiPermissions")))) {
            throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES);
        }
        List<RoleProjectionDiffCalculator.DepartmentFact> departments = new ArrayList<>();
        for (RoleProjectionPort.Department item : port().departments(projection.getRootDepartmentId())) {
            departments.add(new RoleProjectionDiffCalculator.DepartmentFact(item.getId(), item.getParentId(), item.isActive()));
        }
        RoleProjectionPort.Role role = projection.getOpenRoleId() == null ? null : port().read(projection.getOpenRoleId());
        if (role != null) {
            requireIdentity(projection, role);
            if (!manualReceipt) {
                try { requireNoDrift(projection, role); }
                catch (LicenseHttpException drift) {
                    projection.mark(IntegrationState.DRIFTED.getCode());
                    projections.save(projection);
                    throw new GovernanceException(RoleProjectionConstant.UNSAFE_ROLE);
                }
            }
        }
        List<Long> mounted = role == null ? Collections.emptyList() : role.getDepartmentIds();
        RoleProjectionDiffCalculator.Diff diff;
        try {
            diff = RoleProjectionDiffCalculator.calculate(departments, projection.getRootDepartmentId(), mounted);
        } catch (IllegalArgumentException failure) {
            throw LicenseHttpException.dependencyUnavailable(RoleProjectionConstant.INVALID_DIRECTORY);
        }
        long now = Instant.now().getEpochSecond();
        ObjectNode contributions = contributions(projection, now);
        boolean present = contributions.path("grantIds").size() > 0;
        List<Long> target = present ? diff.getActiveSubtree() : Collections.emptyList();
        List<Long> toMount = new ArrayList<>(target); toMount.removeAll(mounted);
        List<Long> toUnmount = new ArrayList<>(mounted); toUnmount.removeAll(target);
        ObjectNode result = StrictJson.mapper().createObjectNode();
        result.put("projectionId", projection.getProjectionId());
        result.put("externalId", projection.getExternalId());
        result.put("organizationInstanceId", projection.getOrganizationInstanceId());
        result.put("bindingGeneration", projection.getBindingGeneration());
        result.put("rootDepartmentId", projection.getRootDepartmentId());
        result.put("applicationId", projection.getApplicationId());
        result.put("manifestVersion", manifest.getVersion());
        result.put("manifestDigest", manifest.getDigest());
        result.put("contributionPresent", present);
        result.set("contributions", contributions.path("grantIds"));
        result.set("approvedRules", approved);
        result.set("desiredRules", present ? approved : emptyRules());
        result.set("targetDepartmentIds", StrictJson.mapper().valueToTree(target));
        ObjectNode departmentDiff = result.putObject("departmentDiff");
        departmentDiff.set("toMount", StrictJson.mapper().valueToTree(toMount));
        departmentDiff.set("toUnmount", StrictJson.mapper().valueToTree(toUnmount));
        result.put("validUntil", contributions.path("validUntil").longValue());
        if (role != null) { result.put("openRoleId", role.getOpenRoleId()); result.put("remoteRevision", role.getRevision()); }
        return result;
    }

    private ObjectNode contributions(RoleProjectionEntity projection, long now) {
        ObjectNode result = StrictJson.mapper().createObjectNode();
        ArrayNode ids = result.putArray("grantIds");
        long validUntil = now + RoleProjectionConstant.PLAN_TTL_SECONDS;
        for (GrantRevisionEntity grant : grants.findByCustomerIdAndProductIdOrderByGrantId(
                projection.getCustomerId(), projection.getProductId())) {
            if (!SubjectKind.PERSON.getCode().equals(grant.getSubjectKind())
                    || !GrantState.ENABLED.getCode().equals(grant.getState())
                    || revocations.isRevoked(RevocationTargetKind.GRANT, grant.getGrantId())) { continue; }
            ValidityPolicy validity = GrantValidityHelper.fromGrant(grant);
            Decision.CommercialPhase phase = validity.commercialPhaseAt(now);
            if (phase == Decision.CommercialPhase.NOT_STARTED) {
                validUntil = Math.min(validUntil, validity.getStartsAt());
                continue;
            }
            if (phase == Decision.CommercialPhase.ENDED) { continue; }
            if (validity.getCommercialGraceUntil() != null) {
                validUntil = Math.min(validUntil, validity.getCommercialGraceUntil());
            }
            if (EligibilityPolicy.Kind.ASSIGNED_SUBJECTS.getCode().equals(grant.getEligibilityKind())
                    && !hasUsableAssignment(grant)) { continue; }
            if (!EligibilityPolicy.Kind.ASSIGNED_SUBJECTS.getCode().equals(grant.getEligibilityKind())
                    && !EligibilityPolicy.Kind.CUSTOMER_MEMBERS.getCode().equals(grant.getEligibilityKind())) { continue; }
            ObjectNode item = ids.addObject();
            item.put("grantId", grant.getGrantId());
            item.put("grantRevision", grant.getGrantRevision());
            item.put("grantStateVersion", grant.getGrantStateVersion());
        }
        result.put("validUntil", validUntil);
        return result;
    }

    private boolean hasUsableAssignment(GrantRevisionEntity grant) {
        for (SubjectAssignmentEntity assignment : assignments.findByGrantId(grant.getGrantId())) {
            if (SubjectKind.PERSON.getCode().equals(assignment.getSubjectKind())
                    && RevocationConstant.ACTIVE.equals(assignment.getState())
                    && RevocationConstant.IAM_SOURCE.equals(assignment.getSourceId())
                    && !revocations.isRevoked(RevocationTargetKind.ASSIGNMENT, assignment.getAssignmentId())) {
                SubjectAdmissionService.Admission observed = admission.isAdmitted(grant.getCustomerId(), assignment.getSubjectId());
                if (observed == SubjectAdmissionService.Admission.UNAVAILABLE) {
                    throw LicenseHttpException.dependencyUnavailable(RoleProjectionConstant.INVALID_DIRECTORY);
                }
                if (observed == SubjectAdmissionService.Admission.ADMITTED) { return true; }
            }
        }
        return false;
    }

    private RoleProjectionPort.Role afterWrite(RoleProjectionEntity projection, String expectedRules,
                                               List<Long> expectedDepartments, long previousRevision) {
        try {
            RoleProjectionPort.Role actual = port().read(projection.getOpenRoleId());
            requireIdentity(projection, actual);
            List<Long> mounted = new ArrayList<>(expectedDepartments); Collections.sort(mounted);
            if (actual.getRevision() != previousRevision + 1
                    || !rulesEqual(actual.getRulesJson(), expectedRules == null ? null
                    : StrictJson.parse(expectedRules, ObjectNode.class)) || !mounted.equals(actual.getDepartmentIds())) {
                throw LicenseHttpException.conflict(RoleProjectionConstant.UNSAFE_ROLE);
            }
            acknowledge(projection, actual);
            return actual;
        } catch (RuntimeException failure) {
            // 请求已发出，后续回读失败不能当作确定未写；禁止自动重试。
            throw new RoleProjectionPort.Failure(RoleProjectionPort.FailureKind.UNCERTAIN, failure);
        }
    }

    private void requireLatest(RoleProjectionEntity projection, IntegrationPlanEntity plan, long revision,
                               String environment, int generation) {
        requireSafe(projection);
        if (revision != plan.getDesiredRevision() || revision != projection.getDesiredRevision()
                || Instant.now().getEpochSecond() >= plan.getValidUntil()
                || !projection.getOrganizationInstanceId().equals(environment)
                || generation != projection.getBindingGeneration()) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.STALE_PLAN);
        }
    }

    private static void requireSafe(RoleProjectionEntity projection) {
        if (IntegrationState.UNCERTAIN.getCode().equals(projection.getState())
                || IntegrationState.DRIFTED.getCode().equals(projection.getState())
                || IntegrationState.ENVIRONMENT_RECONFIRM_REQUIRED.getCode().equals(projection.getState())) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.UNSAFE_ROLE);
        }
    }

    private static void requireIdentity(RoleProjectionEntity projection, RoleProjectionPort.Role role) {
        if (role == null || !projection.getExternalId().equals(role.getExternalId())
                || projection.getApplicationId() != role.getApplicationId()
                || projection.getRootDepartmentId() != role.getRootDepartmentId()
                || projection.getOpenRoleId() != null && !projection.getOpenRoleId().equals(role.getOpenRoleId())) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.UNSAFE_ROLE);
        }
    }

    private static void requireNoDrift(RoleProjectionEntity projection, RoleProjectionPort.Role actual) {
        if (projection.getRemoteRevision() == null || projection.getRemoteRevision() != actual.getRevision()
                || !rulesEqual(actual.getRulesJson(), projection.getAcknowledgedRules() == null ? null
                : StrictJson.parse(projection.getAcknowledgedRules(), ObjectNode.class))
                || !ids(StrictJson.parse(projection.getAcknowledgedDepartments(), ArrayNode.class))
                .equals(actual.getDepartmentIds())) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.UNSAFE_ROLE);
        }
    }

    private static void requireExpectation(ObjectNode old, ObjectNode fresh) {
        // 快照经数据库 JSON 回读后，小整数会由 LongNode 收敛为 IntNode；
        // 两侧先按同一持久化语义回读，仍精确比较所有整数，避免把节点类型差异误判为业务变化。
        ObjectNode persistedOld = StrictJson.parse(old.toString(), ObjectNode.class);
        ObjectNode persistedFresh = StrictJson.parse(fresh.toString(), ObjectNode.class);
        String[] fields = {"projectionId", "externalId", "organizationInstanceId", "bindingGeneration", "rootDepartmentId",
                "applicationId", "manifestVersion", "manifestDigest", "contributionPresent", "contributions",
                "approvedRules", "desiredRules", "targetDepartmentIds"};
        for (String field : fields) {
            if (!Objects.equals(persistedOld.get(field), persistedFresh.get(field))) {
                throw LicenseHttpException.conflict(RoleProjectionConstant.STALE_PLAN);
            }
        }
    }

    private static void requireDesired(ObjectNode desired, RoleProjectionPort.Role actual) {
        boolean rulesMatch = desired.path("contributionPresent").asBoolean()
                ? rulesEqual(actual.getRulesJson(), desired.get("desiredRules")) : actual.getRulesJson() == null;
        if (!rulesMatch || !ids(desired.get("targetDepartmentIds")).equals(actual.getDepartmentIds())) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.UNSAFE_ROLE);
        }
    }

    private static void acknowledge(RoleProjectionEntity projection, RoleProjectionPort.Role role) {
        projection.acknowledge(role.getRulesJson(), StrictJson.mapper().valueToTree(role.getDepartmentIds()).toString(),
                role.getRevision());
    }

    private static void complete(RoleProjectionEntity projection, IntegrationPlanEntity plan) {
        projection.mark(IntegrationState.VERIFIED.getCode());
        plan.execution(RoleProjectionConstant.AUTO, IntegrationState.VERIFIED, null);
    }

    private static void stop(RoleProjectionEntity projection, IntegrationPlanEntity plan, IntegrationState state, String reason) {
        projection.mark(state.getCode());
        plan.execution(RoleProjectionConstant.AUTO, state, reason);
    }

    private RoleProjectionPort port() {
        RoleProjectionPort port = ports.getIfAvailable();
        if (port == null) { throw LicenseHttpException.dependencyUnavailable(RoleProjectionConstant.MISSING_PORT); }
        return port;
    }

    private void scheduleEvent(RoleProjectionEntity projection, IntegrationPlanEntity plan) {
        String reason = plan.getFailureReason();
        // 执行占位令牌只用于本地协调，事件和HTTP原因均使用固定状态码。
        if (reason != null && reason.matches(RoleProjectionConstant.EXECUTION_TOKEN_PATTERN)) {
            reason = RoleProjectionConstant.EXECUTION_IN_PROGRESS;
        }
        RoleProjectionChanged event = new RoleProjectionChanged(UUID.randomUUID().toString(),
                io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents.EVENT_VERSION, Instant.now(),
                projection.getProjectionId(), plan.getCommandId(), plan.getDesiredRevision(),
                plan.getExecutionMode(), plan.getState(), reason);
        Runnable publish = () -> {
            try { events.publishEvent(event); }
            catch (RuntimeException failure) {
                log.warn("角色协作提交事件消费失败：commandId={}，state={}，exceptionType={}",
                        event.getCommandId(), event.getState(), failure.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else { publish.run(); }
    }

    private IntegrationPlanEntity replay(String requestDigest, String inputDigest) {
        if (requestDigest == null) { return null; }
        IntegrationRequestEntity prior = requests.findById(requestDigest).orElse(null);
        if (prior == null) { return null; }
        if (!Objects.equals(prior.getInputDigest(), inputDigest)) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.INVALID_RULES);
        }
        return plans.findById(prior.getPlanId()).orElseThrow(LicenseHttpException::notFound);
    }

    private void remember(String requestDigest, String inputDigest, IntegrationPlanEntity plan) {
        if (requestDigest == null) { return; }
        IntegrationRequestEntity stored = requests.findById(requestDigest).orElse(null);
        if (stored == null) { stored = new IntegrationRequestEntity(requestDigest, inputDigest, plan.getPlanId()); }
        else {
            if (!Objects.equals(stored.getInputDigest(), inputDigest)) {
                throw LicenseHttpException.conflict(RoleProjectionConstant.INVALID_RULES);
            }
            stored.result(plan.getPlanId());
        }
        requests.saveAndFlush(stored);
    }

    private static ObjectNode snapshot(IntegrationPlanEntity plan) {
        if (plan.getProjectionSnapshot() == null) { throw LicenseHttpException.conflict(RoleProjectionConstant.STALE_PLAN); }
        return StrictJson.parse(plan.getProjectionSnapshot(), ObjectNode.class);
    }

    private static ObjectNode bindingRules(ProductEntity product, long applicationId) {
        if (product.getApplicationBindings() == null) { throw LicenseHttpException.conflict(RoleProjectionConstant.INVALID_BINDING); }
        ArrayNode bindings = StrictJson.parse(product.getApplicationBindings(), ArrayNode.class);
        ObjectNode matched = null;
        for (JsonNode binding : bindings) {
            JsonNode id = binding.get("applicationId");
            if (id != null && positiveId(id.asText()) == applicationId) {
                if (!binding.isObject() || matched != null) { throw LicenseHttpException.conflict(RoleProjectionConstant.INVALID_BINDING); }
                matched = (ObjectNode) binding;
            }
        }
        if (matched == null) { throw LicenseHttpException.conflict(RoleProjectionConstant.INVALID_BINDING); }
        return matched;
    }

    private static ObjectNode normalize(ObjectNode rules) {
        if (rules == null) { throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES); }
        rules.fieldNames().forEachRemaining(field -> {
            if (!"pagePermissions".equals(field) && !"apiPermissions".equals(field) && !"dataGrantTemplate".equals(field)) {
                throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES);
            }
        });
        ObjectNode result = emptyRules();
        result.set("pagePermissions", StrictJson.mapper().valueToTree(strings(rules.get("pagePermissions"))));
        result.set("apiPermissions", StrictJson.mapper().valueToTree(strings(rules.get("apiPermissions"))));
        JsonNode data = rules.get("dataGrantTemplate");
        if (data != null && !data.isNull()) {
            if (!data.isObject() || data.size() == 0) { throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES); }
            try {
                result.set("dataGrantTemplate", StrictJson.mapper().valueToTree(DataGrantDocumentClaimMapper.toClaim(
                        DataGrantDocumentClaimMapper.fromClaim(StrictJson.mapper().convertValue(data, Object.class)))));
            } catch (DataPermissionValidationException invalidTemplate) {
                throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES);
            }
        }
        return result;
    }

    private static ObjectNode emptyRules() {
        ObjectNode result = StrictJson.mapper().createObjectNode();
        result.putArray("pagePermissions"); result.putArray("apiPermissions"); result.putNull("dataGrantTemplate");
        return result;
    }

    private static List<String> strings(JsonNode array) {
        if (array == null || !array.isArray()) { throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES); }
        TreeSet<String> values = new TreeSet<>();
        for (JsonNode item : array) {
            if (!item.isTextual() || item.textValue().trim().isEmpty() || !values.add(item.textValue())) {
                throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES);
            }
        }
        return new ArrayList<>(values);
    }

    private static boolean rulesEqual(String actual, JsonNode desired) {
        return actual == null ? desired == null || desired.isNull()
                : desired != null && normalize(StrictJson.parse(actual, ObjectNode.class)).equals(normalize(object(desired)));
    }

    private static ObjectNode object(JsonNode value) {
        return value != null && value.isObject() ? (ObjectNode) value : null;
    }

    private static List<Long> ids(JsonNode values) {
        List<Long> result = new ArrayList<>();
        if (values == null || !values.isArray()) { throw LicenseHttpException.conflict(RoleProjectionConstant.STALE_PLAN); }
        for (JsonNode value : values) { result.add(value.longValue()); }
        Collections.sort(result);
        return result;
    }

    /** HTTP 字符串编号必须能无损转换为 IAM 正整数编号。 */
    public static long positiveId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) { throw new NumberFormatException(); }
            return id;
        } catch (NumberFormatException failure) {
            throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_BINDING);
        }
    }

    /** 已持久化的治理冻结必须提交；其他业务异常仍回滚本次全部业务变更。 */
    public static final class GovernanceException extends LicenseHttpException {
        public GovernanceException(String message) { super(409, message); }
    }
}
