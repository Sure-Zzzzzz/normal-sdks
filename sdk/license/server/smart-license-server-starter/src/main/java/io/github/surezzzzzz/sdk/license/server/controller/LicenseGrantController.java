package io.github.surezzzzzz.sdk.license.server.controller;

import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.CompactJws;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.constant.SigningOperationConstant;
import io.github.surezzzzzz.sdk.license.server.constant.LicenseValidationConstant;
import io.github.surezzzzzz.sdk.license.server.support.ModelRightsHelper;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.service.CredentialIssuanceService;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.OfflineCredentialComposer;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.time.Instant;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商业授权与签发链 HTTP 面：/grants（CRUD+状态+修订）、
 * /grants/{id}/credentials（签发任务）、/credentials（元数据/交付）。
 *
 * <p>签发链复用既有状态机：受理（预算预检+冻结输入落库）→ KMS ES256 签名 → 本地验签 →
 * READY 交付（no-store）。凭据载荷由 {@link OfflineCredentialComposer} 从授权行展开。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@Slf4j
@RestController
@RequestMapping("/api/license/v1")
public class LicenseGrantController {

    private static final Set<String> SUBJECT_KINDS = new HashSet<>(Arrays.asList(
            "DEPLOYMENT", "PERSON", "SERVICE"));
    private static final Set<String> MODES = new HashSet<>(Arrays.asList(
            "OFFLINE", "ONLINE", "HYBRID"));
    private static final Set<String> LIMITER_UNITS = new HashSet<>(Arrays.asList(
            "SECONDS", "MINUTES", "HOURS", "DAYS"));
    private static final Pattern LIMITER_STABLE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final GrantRepository grants;
    private final ProductRepository products;
    private final SigningProfileRepository profiles;
    private final SigningOperationRepository operations;
    private final CredentialIssuanceService issuance;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository models;
    private final io.github.surezzzzzz.sdk.license.server.service.JpaQuotaLedgerService quotaLedger;
    private final LicenseRevocationGuard revocationGuard;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.surezzzzzz.sdk.license.server.service.NamedSeatSnapshotPort namedSeatSnapshotPort;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.surezzzzzz.sdk.license.server.repository.ActivationRepository activationRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.surezzzzzz.sdk.license.server.repository.ActivationQuotaAllocationRepository
            activationQuotaAllocationRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.surezzzzzz.sdk.license.server.repository.QuotaBalanceRepository quotaBalanceRepository;

    /** 声明限频授权的 Grant 必须具备 License 自有履约台账与宿主适配端口。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.surezzzzzz.sdk.license.server.service.LimiterPolicyProjectionService
            limiterPolicyProjectionService;

    /** 签发链依赖签名服务+任务仓储同时齐备；未齐备部署中其余端点不受影响（签发端点明确拒绝）；
     * 模型仓储与额度台账同为可选依赖（缺件部署中授权创建走无模型形态，额度面明确拒绝）。 */
    public LicenseGrantController(GrantRepository grants, ProductRepository products,
                                  SigningProfileRepository profiles,
                                  SigningOperationRepository operations,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                  CredentialIssuanceService issuance,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                  io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository models,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                  io.github.surezzzzzz.sdk.license.server.service.JpaQuotaLedgerService quotaLedger,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                  org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this(grants, products, profiles, operations, issuance, models, quotaLedger, eventPublisher, null);
    }

    /** 生产装配必须有吊销围栏；现有构造用于隔离HTTP契约测试。 */
    @org.springframework.beans.factory.annotation.Autowired
    public LicenseGrantController(GrantRepository grants, ProductRepository products,
                                  SigningProfileRepository profiles, SigningOperationRepository operations,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                          CredentialIssuanceService issuance,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                          io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository models,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                          io.github.surezzzzzz.sdk.license.server.service.JpaQuotaLedgerService quotaLedger,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                          org.springframework.context.ApplicationEventPublisher eventPublisher,
                                  LicenseRevocationGuard revocationGuard) {
        this.grants = grants;
        this.products = products;
        this.profiles = profiles;
        this.operations = operations;
        this.issuance = issuance;
        this.models = models;
        this.quotaLedger = quotaLedger;
        this.eventPublisher = eventPublisher;
        this.revocationGuard = revocationGuard;
    }

    /** 审计只观察已提交事实；监听器失败不改变已提交的业务结果。 */
    private void publish(Object event) {
        if (eventPublisher == null) {
            return;
        }
        Runnable action = () -> {
            try {
                eventPublisher.publishEvent(event);
            } catch (RuntimeException failure) {
                log.warn("授权提交后审计发布失败：事件类型={}，异常类型={}",
                        event.getClass().getSimpleName(), failure.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    // ===== grants（受限 DATA：license-grant 资源 grantId 维度，列表/详情/写入/签发共用同一范围） =====

    @RequireApiPermission("license.grant.read")
    @GetMapping("/grants")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "read")
    public ResponseEntity<String> listGrants(HttpServletRequest request,
                                             @CurrentDataAccessPlan
                                                     DataAccessPlan plan,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(value = "customerId", required = false) String customerId) {
        LicenseRequestContext context = require(request, LicenseApi.GRANT_READ);
        LicenseAccessScope scope = LicenseAccessScope.from(plan, "grantId");
        // 产品筛选与客户筛选必须和 DATA 同时生效，不能在受限分支丢弃页面输入。
        String productId = request.getParameter("productId");
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(size, 100),
                Sort.by("grantId"));
        if (productId != null && !productId.isEmpty() || customerId != null && !customerId.isEmpty()) {
            return pageOf(grants.findVisiblePage(customerId, productId, scope.restrictedIdsOrNull(), pageable),
                    LicenseGrantController::grantView);
        }
        Page<GrantRevisionEntity> result = scope.resolve(
                () -> customerId == null || customerId.isEmpty()
                        ? grants.findAll(pageable)
                        : grants.findByCustomerId(pageable, customerId),
                ids -> grants.findByGrantIdIn(ids, pageable));
        return pageOf(result, LicenseGrantController::grantView);
    }

    @RequireApiPermission("license.grant.manage")
    @PostMapping("/grants")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    public ResponseEntity<String> createGrant(@RequestBody String body,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.GRANT_MANAGE);
        // 创建走全量围栏（受限管理员建权须先扩 DATA 授权；fail-closed 不给"建到自己看不见的行"）
        support.requireDataAll(context, LicenseApi.RESOURCE_GRANT, "manage");
        ObjectNode input = support.object(body, "customerId", "productId", "subjectKind",
                "eligibilityKind", "offlineProofProfile", "mode", "businessOrderRef",
                "modelVersionId", "rights", "validity", "limiterPolicies");
        String customerId = support.text(input, "customerId", true);
        String productId = support.text(input, "productId", true);
        ProductEntity product = products.findById(productId).orElseThrow(LicenseHttpException::notFound);
        String subjectKind = support.text(input, "subjectKind", true);
        if (!SUBJECT_KINDS.contains(subjectKind)) {
            throw LicenseHttpException.unprocessable("对象种类仅支持 DEPLOYMENT/PERSON/SERVICE");
        }
        // 部署型资格可缺省（ALLOCATED_DEPLOYMENTS 是部署授权唯一合法形态）
        String eligibilityKind = "DEPLOYMENT".equals(subjectKind) && input.path("eligibilityKind").asText("").isEmpty()
                ? "ALLOCATED_DEPLOYMENTS" : support.text(input, "eligibilityKind", true);
        String mode = support.text(input, "mode", true);
        if (!MODES.contains(mode)) {
            throw LicenseHttpException.unprocessable("模式仅支持 OFFLINE/ONLINE/HYBRID");
        }
        // 2.0 凭据必须冻结完整已发布模型；不再允许无模型的权益-only 授权。
        String modelVersionId = support.text(input, "modelVersionId", true);
        String rightsJson = null;
        io.github.surezzzzzz.sdk.license.core.model.RuleModel ruleModel = null;
        String modelSnapshotText = null;
        if (models == null) {
            throw LicenseHttpException.unprocessable("模型仓储未装配，无法关联模型版本");
        }
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity model =
                models.findById(modelVersionId).orElseThrow(LicenseHttpException::notFound);
        if (!model.getProductId().equals(productId)) {
            throw LicenseHttpException.unprocessable("模型版本不属于该产品");
        }
        if (!"PUBLISHED".equals(model.getStatus())) {
            throw LicenseHttpException.unprocessable("授权只能引用已发布模型版本");
        }
            com.fasterxml.jackson.databind.node.ObjectNode modelPolicy = parseModelPolicySnapshot(model.getSnapshot());
            ruleModel = io.github.surezzzzzz.sdk.license.core.codec.RuleModelWire.fromSnapshot(modelPolicy);
            validateGrantSelection(modelPolicy, subjectKind, eligibilityKind, mode,
                    support.text(input, "offlineProofProfile", false));
        rightsJson = validatedRightsAgainstModel(input.get("rights"), ruleModel);
        modelSnapshotText = model.getSnapshot();
        String limiterPoliciesJson = validatedLimiterPolicies(input.get("limiterPolicies"));
        // 期限是持久业务事实；未提交时由新建实体一次固化默认期限，读取和签发不续期。
        long[] validity = validatedValidity(input.get("validity"));
        GrantRevisionEntity entity = new GrantRevisionEntity(
                "gr-" + UUID.randomUUID(), customerId, productId, subjectKind, eligibilityKind,
                support.text(input, "offlineProofProfile", false), mode,
                support.text(input, "businessOrderRef", false), modelVersionId, rightsJson,
                limiterPoliciesJson);
        if (validity != null) {
            entity = entity.withValidity(
                    validity[0] == 1L ? "PERPETUAL" : "FIXED", validity[1],
                    validity[0] == 1L ? null : validity[2], validity[0] == 1L ? null : validity[3]);
        }
        GrantRevisionEntity created = grants.save(entity);
        initQuotaBalances(created.getGrantId(), modelSnapshotText, rightsJson, created.getStartsAt());
        publish(new io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents.GrantCreated(
                context.getPrincipalId(), context.getRequestId(), created.getGrantId(), customerId,
                productId, subjectKind, mode, modelVersionId));
        return ResponseEntity.status(201).body(grantView(created).toString());
    }

    /**
     * 期限校验：{kind: FIXED|PERPETUAL, startsAt, serviceUntil?, commercialGraceUntil?}。
     * FIXED 三时刻必填且 startsAt<serviceUntil≤graceUntil；PERPETUAL 禁止截止。
     *
     * @return null（未提交）或 [perpetualFlag, startsAt, serviceUntil(0=无), graceUntil(0=无)]
     */
    private static long[] validatedValidity(JsonNode validity) {
        if (validity == null || validity.isNull()) {
            return null;
        }
        if (!validity.isObject()) {
            throw LicenseHttpException.unprocessable("期限必须为 JSON 对象");
        }
        java.util.Set<String> fields = new java.util.HashSet<>(java.util.Arrays.asList(
                "kind", "startsAt", "serviceUntil", "commercialGraceUntil"));
        validity.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) {
                throw LicenseHttpException.unprocessable(LicenseValidationConstant.UNKNOWN_VALIDITY_FIELD);
            }
        });
        if (validity.has("kind") && !validity.get("kind").isTextual()) {
            throw LicenseHttpException.unprocessable(LicenseValidationConstant.VALIDITY_KIND_TYPE);
        }
        String kind = validity.path("kind").asText("FIXED");
        long startsAt = validitySecond(validity, "startsAt");
        if (startsAt <= 0L) {
            throw LicenseHttpException.unprocessable("期限 startsAt 必填（epoch 秒）");
        }
        if ("PERPETUAL".equals(kind)) {
            if (validity.hasNonNull("serviceUntil") || validity.hasNonNull("commercialGraceUntil")) {
                throw LicenseHttpException.unprocessable(LicenseValidationConstant.PERPETUAL_DEADLINE);
            }
            return new long[]{1L, startsAt, 0L, 0L};
        }
        if (!"FIXED".equals(kind)) {
            throw LicenseHttpException.unprocessable("期限 kind 仅支持 FIXED/PERPETUAL");
        }
        long serviceUntil = validitySecond(validity, "serviceUntil");
        long graceUntil = validitySecond(validity, "commercialGraceUntil");
        if (serviceUntil <= startsAt || graceUntil < serviceUntil) {
            throw LicenseHttpException.unprocessable("期限须满足 startsAt<serviceUntil≤graceUntil");
        }
        return new long[]{0L, startsAt, serviceUntil, graceUntil};
    }

    /** 时刻必须为可表示的整数秒，拒绝字符串、小数及溢出后被截断的值。 */
    private static long validitySecond(JsonNode validity, String field) {
        JsonNode value = validity.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw LicenseHttpException.unprocessable(LicenseValidationConstant.VALIDITY_SECOND_TYPE + field);
        }
        return value.longValue();
    }

    /** 模型快照→完整政策树（发布级校验链；坏快照确定拒绝）。 */
    private com.fasterxml.jackson.databind.node.ObjectNode parseModelPolicySnapshot(String snapshot) {
        try {
            return io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire.parse(snapshot);
        } catch (RuntimeException failure) {
            throw LicenseHttpException.unprocessable("模型快照不可解析（发布级校验未通过）");
        }
    }

    /** 授权选择必须是已发布模型声明的合法组合，不能只校验权益字段。 */
    private static void validateGrantSelection(ObjectNode policy, String subjectKind,
                                               String eligibilityKind, String mode,
                                               String offlineProofProfile) {
        com.fasterxml.jackson.databind.JsonNode subject = policy.path("subjectPolicy");
        if (!containsText(subject.path("allowedKinds"), subjectKind)
                || !containsText(subject.path("allowedEligibilityKinds"), eligibilityKind)) {
            throw LicenseHttpException.unprocessable("授权对象或资格政策不在模型允许范围");
        }
        if (!containsText(policy.path("connectivityPolicy").path("allowedModes"), mode)) {
            throw LicenseHttpException.unprocessable("授权连接模式不在模型允许范围");
        }
        if ("DEPLOYMENT".equals(subjectKind) && !"ALLOCATED_DEPLOYMENTS".equals(eligibilityKind)
                || !"DEPLOYMENT".equals(subjectKind) && "ALLOCATED_DEPLOYMENTS".equals(eligibilityKind)
                || "SERVICE".equals(subjectKind) && "CUSTOMER_MEMBERS".equals(eligibilityKind)) {
            throw LicenseHttpException.unprocessable("对象种类与资格政策组合无效");
        }
        boolean offlineLike = "OFFLINE".equals(mode) || "HYBRID".equals(mode);
        if (offlineLike && ("PERSON".equals(subjectKind) || "SERVICE".equals(subjectKind))) {
            if (offlineProofProfile == null || offlineProofProfile.isEmpty()
                    || !containsText(subject.path("offlineProofProfiles"), offlineProofProfile)
                    || !containsText(policy.path("requiredCapabilities"), "subject-proof/" + offlineProofProfile)) {
                throw LicenseHttpException.unprocessable("人员或服务的离线身份能力档案无效");
            }
        } else if (offlineProofProfile != null && !offlineProofProfile.isEmpty()) {
            throw LicenseHttpException.unprocessable("当前连接模式不得携带离线身份能力档案");
        }
        boolean organizationRequired = policy.path("organizationPolicy").path("required").asBoolean(false);
        if ("CUSTOMER_MEMBERS".equals(eligibilityKind)
                && (!"PERSON".equals(subjectKind) || "OFFLINE".equals(mode) || !organizationRequired)) {
            throw LicenseHttpException.unprocessable("客户成员资格必须使用人员在线或混合模式并校验当前组织");
        }
        if (organizationRequired && "OFFLINE".equals(mode)) {
            throw LicenseHttpException.unprocessable("离线模式不得要求实时组织成员资格");
        }
    }

    private static boolean containsText(com.fasterxml.jackson.databind.JsonNode array, String value) {
        if (array == null || !array.isArray() || value == null) {
            return false;
        }
        for (com.fasterxml.jackson.databind.JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 权益校验（多类型）：对象形态+键集⊆模型 rights 声明+按字段类型收值
     * （INTEGER 整数过范围；BOOLEAN 布尔；STRING 文本；STRING_SET 文本数组）。
     * 返回紧凑 JSON。
     */
    private String validatedRightsAgainstModel(JsonNode rights,
            io.github.surezzzzzz.sdk.license.core.model.RuleModel model) {
        if (rights == null || rights.isNull()) {
            return ModelRightsHelper.validate(rights, model, true);
        }
        if (!rights.isObject()) {
            throw LicenseHttpException.unprocessable("权益必须为 JSON 对象（字段名→类型化值）");
        }
        ObjectNode normalized = StrictJson.mapper().createObjectNode();
        java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = rights.fields();
        while (fields.hasNext()) {
            java.util.Map.Entry<String, JsonNode> entry = fields.next();
            io.github.surezzzzzz.sdk.license.core.model.FieldDefinition definition =
                    model.getRights().get(entry.getKey());
            if (definition == null) {
                throw LicenseHttpException.unprocessable("权益字段未在模型声明：" + entry.getKey());
            }
            JsonNode value = entry.getValue();
            switch (definition.getType()) {
                case INTEGER:
                    if (!value.isIntegralNumber() || !value.canConvertToLong()) {
                        throw LicenseHttpException.unprocessable("权益字段须为整数：" + entry.getKey());
                    }
                    long parsed = value.asLong();
                    if (definition.getMinimum() != null && parsed < definition.getMinimum()) {
                        throw LicenseHttpException.unprocessable("权益字段低于声明最小值：" + entry.getKey());
                    }
                    if (definition.getMaximum() != null && parsed > definition.getMaximum()) {
                        throw LicenseHttpException.unprocessable("权益字段超过声明最大值：" + entry.getKey());
                    }
                    normalized.put(entry.getKey(), parsed);
                    break;
                case BOOLEAN:
                    if (!value.isBoolean()) {
                        throw LicenseHttpException.unprocessable("权益字段须为布尔：" + entry.getKey());
                    }
                    normalized.put(entry.getKey(), value.asBoolean());
                    break;
                case STRING:
                    if (!value.isTextual()) {
                        throw LicenseHttpException.unprocessable("权益字段须为文本：" + entry.getKey());
                    }
                    normalized.put(entry.getKey(), value.asText());
                    break;
                case STRING_SET:
                    if (!value.isArray()) {
                        throw LicenseHttpException.unprocessable("权益字段须为文本数组：" + entry.getKey());
                    }
                    com.fasterxml.jackson.databind.node.ArrayNode set = normalized.putArray(entry.getKey());
                    for (JsonNode item : value) {
                        if (!item.isTextual()) {
                            throw LicenseHttpException.unprocessable("权益集合成员须为文本：" + entry.getKey());
                        }
                        set.add(item.asText());
                    }
                    break;
                default:
                    throw LicenseHttpException.unprocessable("未知字段类型：" + definition.getType());
            }
        }
        return ModelRightsHelper.validate(normalized, model, true);
    }

    /**
     * 校验客户 OpenAPI 限频授权快照；这里仅保存商业授权输入，不直接认识 limiter 实现类型。
     */
    private String validatedLimiterPolicies(JsonNode policies) {
        if (policies == null || policies.isNull()) {
            return "[]";
        }
        if (!policies.isArray() || policies.size() > 64) {
            throw LicenseHttpException.unprocessable("limiterPolicies 必须为不超过64项的数组");
        }
        ArrayNode normalized = StrictJson.mapper().createArrayNode();
        java.util.Set<String> resourceKeys = new java.util.HashSet<>();
        for (JsonNode policy : policies) {
            if (!policy.isObject()) {
                throw LicenseHttpException.unprocessable("限频授权项必须为对象");
            }
            ObjectNode input = (ObjectNode) policy;
            java.util.Set<String> fields = new java.util.HashSet<>(Arrays.asList(
                    "serviceCode", "resourceCode", "limits"));
            java.util.Iterator<String> names = input.fieldNames();
            while (names.hasNext()) {
                if (!fields.contains(names.next())) {
                    throw LicenseHttpException.unprocessable("限频授权项含未知字段");
                }
            }
            ObjectNode item = StrictJson.mapper().createObjectNode();
            item.put("serviceCode", limiterText(input, "serviceCode", 128));
            item.put("resourceCode", limiterText(input, "resourceCode", 128));
            String resourceKey = item.path("serviceCode").asText() + "\u0000"
                    + item.path("resourceCode").asText();
            if (!resourceKeys.add(resourceKey)) {
                throw LicenseHttpException.unprocessable("同一授权不得重复声明 serviceCode/resourceCode");
            }
            JsonNode limits = input.get("limits");
            if (limits == null || !limits.isArray() || limits.size() == 0 || limits.size() > 8) {
                throw LicenseHttpException.unprocessable("限频窗口必须为1至8项");
            }
            ArrayNode normalizedLimits = item.putArray("limits");
            for (JsonNode limit : limits) {
                if (!limit.isObject()) {
                    throw LicenseHttpException.unprocessable("限频窗口必须为对象");
                }
                ObjectNode limitInput = (ObjectNode) limit;
                java.util.Set<String> limitFields = new java.util.HashSet<>(Arrays.asList(
                        "count", "window", "unit"));
                java.util.Iterator<String> limitNames = limitInput.fieldNames();
                while (limitNames.hasNext()) {
                    if (!limitFields.contains(limitNames.next())) {
                        throw LicenseHttpException.unprocessable("限频窗口含未知字段");
                    }
                }
                JsonNode count = limitInput.get("count");
                JsonNode window = limitInput.get("window");
                if (count == null || !count.isIntegralNumber() || !count.canConvertToLong() || count.longValue() <= 0L
                        || window == null || !window.isIntegralNumber() || !window.canConvertToLong()
                        || window.longValue() <= 0L) {
                    throw LicenseHttpException.unprocessable("限频次数和窗口必须为正整数");
                }
                ObjectNode normalizedLimit = normalizedLimits.addObject();
                normalizedLimit.put("count", count.longValue());
                normalizedLimit.put("window", window.longValue());
                String unit = limiterText(limitInput, "unit", 16).toUpperCase(java.util.Locale.ROOT);
                if (!LIMITER_UNITS.contains(unit)) {
                    throw LicenseHttpException.unprocessable("限频时间单位仅支持SECONDS/MINUTES/HOURS/DAYS");
                }
                normalizedLimit.put("unit", unit);
            }
            normalized.add(item);
        }
        return normalized.toString();
    }

    private static String limiterText(ObjectNode input, String name, int maxLength) {
        JsonNode value = input.get(name);
        if (value == null || !value.isTextual()) {
            throw LicenseHttpException.unprocessable("限频字段无效：" + name);
        }
        String normalized = value.textValue().trim();
        if (normalized.isEmpty() || normalized.length() > maxLength
                || !LIMITER_STABLE_CODE.matcher(normalized).matches()) {
            throw LicenseHttpException.unprocessable("限频字段无效：" + name);
        }
        return normalized;
    }

    /**
     * 按quotaDefinitions对象初始化ONLINE并发/消费余额；固定窗口首次引入时固化商业锚。
     * LOCAL及NAMED_SEAT不使用在线余额；非法权益或台账故障失败关闭，不吞掉半成品初始化。
     */
    private void initQuotaBalances(String grantId, String modelSnapshotText, String rightsJson, Long startsAt) {
        if (modelSnapshotText == null || rightsJson == null) {
            return;
        }
        try {
            JsonNode quotaDefinitions = StrictJson.parse(modelSnapshotText, ObjectNode.class).path("quotaDefinitions");
            JsonNode rights = StrictJson.parse(rightsJson, ObjectNode.class);
            if (!quotaDefinitions.isObject()) {
                return;
            }
            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> definitions = quotaDefinitions.fields();
            while (definitions.hasNext()) {
                java.util.Map.Entry<String, JsonNode> entry = definitions.next();
                String metric = entry.getKey();
                JsonNode definition = entry.getValue();
                if (!"ONLINE".equals(definition.path("enforcement").asText())
                        || "NAMED_SEAT".equals(definition.path("kind").asText())) {
                    continue;
                }
                if (quotaLedger == null) {
                    throw LicenseHttpException.dependencyUnavailable("额度台账未装配");
                }
                String limitRight = definition.path("limitRight").asText(null);
                JsonNode limit = limitRight == null ? null : rights.get(limitRight);
                if (metric == null || limit == null || !limit.isIntegralNumber() || !limit.canConvertToLong()
                        || limit.longValue() < 0L) {
                    throw LicenseHttpException.unprocessable("额度限额必须为非负整数权益");
                }
                quotaLedger.initBalance(grantId, metric, limit.longValue(),
                        "FIXED_WINDOW".equals(definition.path("window").asText()) ? startsAt : null);
            }
        } catch (org.springframework.dao.DataAccessException unavailable) {
            throw LicenseHttpException.dependencyUnavailable("额度台账不可用");
        }
    }

    @RequireApiPermission("license.grant.read")
    @GetMapping("/grants/{grantId}")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "read")
    public ResponseEntity<String> getGrant(@PathVariable String grantId,
                                           @CurrentDataAccessPlan
                                                   DataAccessPlan plan,
                                           HttpServletRequest request) {
        require(request, LicenseApi.GRANT_READ);
        LicenseAccessScope.from(plan, "grantId").requireVisible(grantId);
        return ResponseEntity.ok(grantView(find(grantId)).toString());
    }

    @RequireApiPermission("license.grant.manage")
    @PatchMapping("/grants/{grantId}/state")
    @Transactional
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "manage")
    public ResponseEntity<String> patchState(@PathVariable String grantId, @RequestBody String body,
                                             @CurrentDataAccessPlan
                                                     DataAccessPlan plan,
                                             HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.GRANT_MANAGE);
        LicenseAccessScope.from(plan, "grantId").requireVisible(grantId);
        ObjectNode input = support.object(body, "state", "expectedRowVersion");
        GrantRevisionEntity entity = find(grantId);
        // 事务内优先取得数据库行锁，避免到期扫描与人工状态变更交叉覆盖。
        Optional<GrantRevisionEntity> locked = grants.findWithLockByGrantId(grantId);
        if (locked.isPresent()) {
            entity = locked.get();
        }
        JsonNode expectedVersion = input.get("expectedRowVersion");
        if (expectedVersion == null || !expectedVersion.isIntegralNumber() || !expectedVersion.canConvertToLong()
                || expectedVersion.longValue() < 0L) {
            throw LicenseHttpException.unprocessable(LicenseValidationConstant.GRANT_ROW_VERSION_INVALID);
        }
        if (expectedVersion.longValue() != entity.getRowVersion()) {
            throw LicenseHttpException.conflict("版本已变化，请回读后重试");
        }
        String state = support.text(input, "state", true);
        if (!"ENABLED".equals(state) && !"SUSPENDED".equals(state)) {
            throw LicenseHttpException.unprocessable("状态仅支持 ENABLED/SUSPENDED");
        }
        String fromState = entity.getState();
        // 草稿可首次启用；终态与未知状态不得通过通用状态入口复活。
        boolean allowed = "DRAFT".equals(fromState) && "ENABLED".equals(state)
                || "ENABLED".equals(fromState) && "SUSPENDED".equals(state)
                || "SUSPENDED".equals(fromState) && "ENABLED".equals(state);
        if (!allowed) {
            throw LicenseHttpException.conflict(LicenseValidationConstant.GRANT_STATE_CONFLICT);
        }
        if ("ENABLED".equals(state)) {
            if (entity.getServiceUntil() != null
                    && entity.getServiceUntil() <= Instant.now().getEpochSecond()) {
                throw LicenseHttpException.conflict("授权已超过服务截止时间，不能重新启用");
            }
            projectLimiterEnabled(entity);
        } else if ("SUSPENDED".equals(state)) {
            projectLimiterDisabled(entity);
        }
        entity.setState(state);
        entity.setGrantStateVersion(entity.getGrantStateVersion() + 1L);
        GrantRevisionEntity saved = grants.save(entity);
        publish(new io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents.GrantStateChanged(
                context.getPrincipalId(), context.getRequestId(), grantId, fromState, state));
        return ResponseEntity.ok(grantView(saved).toString());
    }

    private void projectLimiterEnabled(GrantRevisionEntity entity) {
        if (hasLimiterPolicies(entity) && limiterPolicyProjectionService == null) {
            throw LicenseHttpException.dependencyUnavailable("客户限频履约台账未装配");
        }
        if (hasLimiterPolicies(entity)) {
            limiterPolicyProjectionService.enable(entity);
        }
    }

    private void projectLimiterDisabled(GrantRevisionEntity entity) {
        if (limiterPolicyProjectionService != null) {
            limiterPolicyProjectionService.disable(entity);
        } else if (hasLimiterPolicies(entity)) {
            throw LicenseHttpException.dependencyUnavailable("客户限频履约台账未装配");
        }
    }

    private static boolean hasLimiterPolicies(GrantRevisionEntity entity) {
        return entity.getLimiterPoliciesJson() != null
                && !entity.getLimiterPoliciesJson().trim().isEmpty()
                && !"[]".equals(entity.getLimiterPoliciesJson().trim());
    }

    // ===== credentials 签发链 =====

    @RequireApiPermission("license.grant.issue")
    @PostMapping("/grants/{grantId}/credentials")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "issue")
    public ResponseEntity<String> requestCredential(@PathVariable String grantId, @RequestBody String body,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    @CurrentDataAccessPlan
                                                            DataAccessPlan plan,
                                                    HttpServletRequest request) {
        LicenseRequestContext issueContext = require(request, LicenseApi.GRANT_ISSUE);
        LicenseAccessScope.from(plan, "grantId").requireVisible(grantId);
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()
                || idempotencyKey.length() + grantId.length() + 1
                > io.github.surezzzzzz.sdk.license.server.constant.SigningOperationConstant.IDEMPOTENCY_MAX_LENGTH) {
            throw LicenseHttpException.unprocessable(
                    io.github.surezzzzzz.sdk.license.server.constant.SigningOperationConstant.INVALID_IDEMPOTENCY);
        }
        // 模式是当前商业授权事实，先于幂等回读核验；旧错误任务不能让 ONLINE/HYBRID 绕过边界。
        GrantRevisionEntity grant = find(grantId);
        if (!"OFFLINE".equals(grant.getMode())) {
            log.debug("离线签发拒绝授权模式：grantId={}，mode={}", grantId, grant.getMode());
            throw LicenseHttpException.unprocessable(LicenseValidationConstant.OFFLINE_ISSUANCE_ONLY);
        }
        // 真幂等：摘要=幂等键×授权（稳定，不含随机生成的凭据编号）；重试命中首任务原样返回
        // （先于签名服务判空——重试已完成任务不需要签名服务在场）
        String idempotencyDigest = idempotencyKey + ":" + grantId;
        SigningOperationEntity existing = operations.findByIdempotencyDigest(idempotencyDigest);
        if (existing != null) {
            return ResponseEntity.status(202).body(credentialMetaView(existing).toString());
        }
        if (issuance == null) {
            throw LicenseHttpException.dependencyUnavailable("签名服务未装配，签发不可用");
        }
        ProductEntity product = products.findById(grant.getProductId())
                .orElseThrow(LicenseHttpException::notFound);
        ObjectNode input = support.object(body, "subjectRef", "kid");
        String kid = support.text(input, "kid", true);
        SigningProfileEntity profile = findActiveProfile(product.getProductId(), kid);
        // subjectRef 双形态：对象（前端提交）序列化为 JSON 文本；字符串原样透传
        com.fasterxml.jackson.databind.JsonNode refNode = input.get("subjectRef");
        String subjectRef = refNode != null && refNode.isObject() ? refNode.toString()
                : support.text(input, "subjectRef", true);
        // 模型快照原文：授权关联的已发布模型；缺少模型是历史数据/配置错误，直接失败关闭。
        if (models == null || grant.getModelVersionId() == null || grant.getModelVersionId().isEmpty()) {
            throw LicenseHttpException.unprocessable("授权缺少已发布模型版本");
        }
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity model =
                models.findById(grant.getModelVersionId()).orElseThrow(LicenseHttpException::notFound);
        java.util.List<java.util.Map<String, Object>> quotaAllocations =
                java.util.Collections.<java.util.Map<String, Object>>emptyList();
        java.util.Map<String, Long> windowAnchors = new java.util.HashMap<String, Long>();
        if ("DEPLOYMENT".equals(grant.getSubjectKind())) {
            // 旧的纯 Composer 隔离测试没有 JPA 仓储；生产装配只要出现任一新仓储就必须全链齐备。
            if (activationRepository != null || activationQuotaAllocationRepository != null) {
                io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity activation =
                        resolveIssuanceActivation(grant, subjectRef);
                if (activationQuotaAllocationRepository == null) {
                    throw LicenseHttpException.dependencyUnavailable("额度分配台账未装配");
                }
                java.util.List<io.github.surezzzzzz.sdk.license.server.entity.ActivationQuotaAllocationEntity> rows =
                        activationQuotaAllocationRepository.findByActivationIdOrderByMetricAsc(
                                activation.getActivationId());
                quotaAllocations = allocationFacts(rows);
                for (io.github.surezzzzzz.sdk.license.server.entity.ActivationQuotaAllocationEntity row : rows) {
                    if (row.getWindowAnchorAt() != null) {
                        windowAnchors.put(row.getMetric(), row.getWindowAnchorAt());
                    }
                }
            }
        }
        completeWindowAnchors(grant, model, windowAnchors);
        String credentialId = "cred-" + UUID.randomUUID();
        String operationId = "op-" + UUID.randomUUID();
        long issuedAt = Instant.now().getEpochSecond();
        java.util.List<io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference> namedSeats =
                namedSeatSnapshotPort == null ? io.github.surezzzzzz.sdk.license.server.service.NamedSeatSnapshotPort.empty()
                        : namedSeatSnapshotPort.snapshot(grantId);
        final String payloadJson;
        try {
            payloadJson = OfflineCredentialComposer.compose(grant, product, model, subjectRef,
                    credentialId, issuedAt, namedSeats, quotaAllocations, windowAnchors);
        } catch (IllegalArgumentException invalidBinding) {
            // subjectRef 是业务输入；Composer 的联合约束失败属于 422，不应冒泡成 500。
            throw LicenseHttpException.unprocessable("离线凭据主体绑定与授权不匹配");
        }
        String headerSegment = CompactJws.encode(("{\"alg\":\"ES256\",\"kid\":\"" + profile.getKid()
                + "\",\"typ\":\"license+jwt\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String payloadSegment = CompactJws.encode(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            String acceptedOperationId = issuance.accept(operationId, credentialId, grantId, profile.getProfileId(),
                    headerSegment, payloadSegment, idempotencyDigest);
            if (!operationId.equals(acceptedOperationId)) {
                SigningOperationEntity winner = operations.findById(acceptedOperationId)
                        .orElseThrow(LicenseHttpException::notFound);
                return ResponseEntity.status(202).body(credentialMetaView(winner).toString());
            }
            // 同步执行（管理面小流量形态）：签名+验签+READY 一步到位；大流量改异步任务轮询。
            issuance.signAndCommit(operationId);
            SigningOperationEntity committed = operations.findById(operationId)
                    .orElseThrow(LicenseHttpException::notFound);
            if (revocationGuard != null) {
                revocationGuard.requireCredentialUsable(committed);
            }
            ObjectNode view = credentialMetaView(committed);
            publish(new io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents.CredentialIssued(
                    issueContext.getPrincipalId(), issueContext.getRequestId(), grantId, credentialId, profile.getKid()));
            return ResponseEntity.status(201).body(view.toString());
        } catch (LicenseHttpException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw LicenseHttpException.dependencyUnavailable("签名服务暂不可用");
        }
    }

    @RequireApiPermission("license.grant.read")
    @GetMapping("/credentials")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "read")
    public ResponseEntity<String> listCredentials(HttpServletRequest request,
                                                  @CurrentDataAccessPlan
                                                          DataAccessPlan plan,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(value = "grantId", required = false) String grantId,
                                                  @RequestParam(value = "activationId", required = false) String activationId) {
        require(request, LicenseApi.GRANT_READ);
        // 凭据经 grantId 关联授权 DATA；授权与激活筛选必须同时进入 SQL，再执行分页和计数。
        LicenseAccessScope scope = LicenseAccessScope.from(plan, "grantId");
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, SigningOperationConstant.OPERATION_ID));
        Page<SigningOperationEntity> result = scope.resolve(
                () -> operations.findCredentials(null, grantId, activationId, pageable),
                ids -> operations.findCredentials(ids, grantId, activationId, pageable));
        log.debug("凭据元数据查询完成：allData={}, grantFilter={}, activationFilter={}, page={}, size={}, total={}",
                scope.isAll(), grantId != null && !grantId.isEmpty(), activationId != null && !activationId.isEmpty(),
                result.getNumber() + 1, result.getSize(), result.getTotalElements());
        return pageOf(result, LicenseGrantController::credentialMetaView);
    }

    @RequireApiPermission("license.grant.read")
    @GetMapping("/credentials/{credentialId}")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "read")
    public ResponseEntity<String> getCredential(@PathVariable String credentialId,
                                                @CurrentDataAccessPlan
                                                        DataAccessPlan plan,
                                                HttpServletRequest request) {
        require(request, LicenseApi.GRANT_READ);
        SigningOperationEntity entity = findCredential(credentialId);
        LicenseAccessScope.from(plan, "grantId").requireVisible(entity.getGrantId());
        return ResponseEntity.ok(credentialMetaView(entity).toString());
    }

    @RequireApiPermission("license.grant.deliver")
    @GetMapping("/credentials/{credentialId}/content")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "deliver")
    public ResponseEntity<String> deliverContent(@PathVariable String credentialId,
                                                 @CurrentDataAccessPlan
                                                         DataAccessPlan plan,
                                                 HttpServletRequest request) {
        require(request, LicenseApi.GRANT_DELIVER);
        SigningOperationEntity entity = findCredential(credentialId);
        LicenseAccessScope.from(plan, "grantId").requireVisible(entity.getGrantId());
        if (!"READY".equals(entity.getState()) || entity.getCommittedSignature() == null) {
            throw LicenseHttpException.notFound();
        }
        if (revocationGuard != null) {
            revocationGuard.requireCredentialUsable(entity);
        }
        String compactJws = CompactJws.join(entity.getFrozenHeader(), entity.getFrozenPayload(),
                entity.getCommittedSignature());
        ObjectNode body = StrictJson.mapper().createObjectNode();
        body.put("credentialId", credentialId);
        body.put("compactJws", compactJws);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body.toString());
    }

    // ===== helpers =====

    /** 签发前回读真实激活，避免只凭请求中的 activationId 跨授权或跨代签发。 */
    private io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity resolveIssuanceActivation(
            GrantRevisionEntity grant, String subjectRef) {
        if (activationRepository == null) {
            throw LicenseHttpException.dependencyUnavailable("激活仓储未装配");
        }
        try {
            JsonNode reference = StrictJson.mapper().readTree(subjectRef);
            String activationId = reference == null ? null : reference.path("activationId").asText(null);
            long generation = reference == null ? 0L : reference.path("activationGeneration").asLong(0L);
            if (activationId == null || activationId.isEmpty() || generation < 1L) {
                throw LicenseHttpException.unprocessable("部署绑定缺少真实激活事实");
            }
            io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity activation =
                    activationRepository.findById(activationId).orElseThrow(LicenseHttpException::notFound);
            if (!grant.getGrantId().equals(activation.getGrantId())
                    || activation.getActivationGeneration() != generation) {
                throw LicenseHttpException.unprocessable("激活不属于当前授权或代数已变化");
            }
            if (!"ACTIVE".equals(activation.getState())) {
                throw LicenseHttpException.conflict("激活当前不允许新签发");
            }
            return activation;
        } catch (LicenseHttpException failure) {
            throw failure;
        } catch (Exception failure) {
            throw LicenseHttpException.unprocessable("部署绑定缺少合法激活事实");
        }
    }

    /** 将激活表中的持久事实转换为 Composer 要求的稳定字段集。 */
    private static java.util.List<java.util.Map<String, Object>> allocationFacts(
            java.util.List<io.github.surezzzzzz.sdk.license.server.entity.ActivationQuotaAllocationEntity> rows) {
        java.util.List<java.util.Map<String, Object>> values = new java.util.ArrayList<>();
        if (rows == null) {
            return values;
        }
        for (io.github.surezzzzzz.sdk.license.server.entity.ActivationQuotaAllocationEntity row : rows) {
            java.util.Map<String, Object> value = new java.util.LinkedHashMap<String, Object>();
            value.put("allocationId", row.getAllocationId());
            value.put("metric", row.getMetric());
            value.put("quantity", row.getQuantity());
            value.put("grantRevision", row.getGrantRevision());
            value.put("retention", row.getRetention());
            if (row.getRetainUntil() != null) {
                value.put("retainUntil", row.getRetainUntil());
            }
            values.add(value);
        }
        return values;
    }

    /** 固定窗口锚来自激活分配或在线余额事实，缺失时失败关闭，不从当前时间推导。 */
    private void completeWindowAnchors(GrantRevisionEntity grant,
                                       io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity model,
                                       java.util.Map<String, Long> anchors) {
        com.fasterxml.jackson.databind.node.ObjectNode policy =
                io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire.parse(model.getSnapshot());
        JsonNode definitions = policy.get("quotaDefinitions");
        if (definitions == null || !definitions.isObject()) {
            throw LicenseHttpException.unprocessable("模型缺少额度定义");
        }
        java.util.Iterator<java.util.Map.Entry<String, JsonNode>> iterator = definitions.fields();
        while (iterator.hasNext()) {
            java.util.Map.Entry<String, JsonNode> entry = iterator.next();
            JsonNode definition = entry.getValue();
            if (!"FIXED_WINDOW".equals(definition.path("window").asText())
                    || anchors.containsKey(entry.getKey())) {
                continue;
            }
            if (quotaBalanceRepository != null) {
                io.github.surezzzzzz.sdk.license.server.entity.QuotaBalanceEntity balance =
                        quotaBalanceRepository.findByGrantIdAndMetric(grant.getGrantId(), entry.getKey()).orElse(null);
                if (balance != null && balance.getWindowAnchorAt() != null) {
                    anchors.put(entry.getKey(), balance.getWindowAnchorAt());
                }
            }
            if (!anchors.containsKey(entry.getKey())) {
                throw LicenseHttpException.unprocessable("固定窗口额度缺少持久窗口锚");
            }
        }
    }

    private GrantRevisionEntity find(String grantId) {
        return grants.findById(grantId).orElseThrow(LicenseHttpException::notFound);
    }

    private SigningOperationEntity findCredential(String credentialId) {
        SigningOperationEntity entity = operations.findByCredentialId(credentialId);
        if (entity == null) {
            throw LicenseHttpException.notFound();
        }
        return entity;
    }

    private SigningProfileEntity findActiveProfile(String productId, String kid) {
        Optional<SigningProfileEntity> byPurpose = profiles
                .findByProductIdAndPurposeAndStatus(productId, "CREDENTIAL", "ACTIVE");
        if (byPurpose.isPresent() && byPurpose.get().getKid().equals(kid)) {
            return byPurpose.get();
        }
        throw LicenseHttpException.unprocessable("产品的凭据用途签名档案未登记或 kid 不匹配");
    }

    private LicenseRequestContext require(HttpServletRequest request, String permission) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return support.requireApiPermission(context, permission);
    }

    private static ObjectNode grantView(GrantRevisionEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("grantId", entity.getGrantId());
        view.put("customerId", entity.getCustomerId());
        view.put("productId", entity.getProductId());
        view.put("subjectKind", entity.getSubjectKind());
        view.put("eligibilityKind", entity.getEligibilityKind());
        view.put("mode", entity.getMode());
        view.put("state", entity.getState());
        view.put("grantRevision", entity.getGrantRevision());
        view.put("grantStateVersion", entity.getGrantStateVersion());
        if (entity.getBusinessOrderRef() != null) {
            view.put("businessOrderRef", entity.getBusinessOrderRef());
        } else {
            view.putNull("businessOrderRef");
        }
        if (entity.getModelVersionId() != null) {
            view.put("modelVersionId", entity.getModelVersionId());
            try {
                view.set("rights", StrictJson.mapper().readTree(
                        entity.getRightsJson() == null ? "{}" : entity.getRightsJson()));
            } catch (java.io.IOException parseFailure) {
                view.putObject("rights");
            }
        } else {
            view.putNull("modelVersionId");
        }
        try {
            view.set("limiterPolicies", StrictJson.mapper().readTree(
                    entity.getLimiterPoliciesJson() == null ? "[]" : entity.getLimiterPoliciesJson()));
        } catch (java.io.IOException parseFailure) {
            view.putArray("limiterPolicies");
        }
        if (entity.getValidityKind() != null) {
            ObjectNode validity = view.putObject("validity");
            validity.put("kind", entity.getValidityKind());
            validity.put("startsAt", entity.getStartsAt());
            if (entity.getServiceUntil() != null) {
                validity.put("serviceUntil", entity.getServiceUntil());
                validity.put("commercialGraceUntil", entity.getGraceUntil());
            }
        }
        view.put("rowVersion", entity.getRowVersion());
        return view;
    }

    private static ObjectNode credentialMetaView(SigningOperationEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("operationId", entity.getOperationId());
        view.put("credentialId", entity.getCredentialId());
        view.put("grantId", entity.getGrantId());
        view.put("state", entity.getState());
        view.put("rowVersion", entity.getRowVersion());
        view.put("status", entity.getState());
        view.put("executionGeneration", entity.getExecutionGeneration());
        view.put("reasonCategory", entity.getReasonCategory());
        view.put("resultResource", "READY".equals(entity.getState()) && entity.getCommittedSignature() != null
                ? "/api/license/v1/credentials/" + entity.getCredentialId() + "/content" : null);
        if (entity.getLastKmsCallAt() != null) {
            view.put("lastKmsCallAt", entity.getLastKmsCallAt().getEpochSecond());
        }
        String activationId = entity.getActivationId();
        if (activationId != null) {
            view.put("activationId", activationId);
        }
        return view;
    }

    private static ObjectNode operationView(SigningOperationEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("operationId", entity.getOperationId());
        view.put("status", entity.getState());
        view.put("state", entity.getState());
        view.put("rowVersion", entity.getRowVersion());
        view.put("reasonCategory", entity.getReasonCategory());
        view.put("resultResource", "READY".equals(entity.getState())
                ? "/api/license/v1/credentials/" + entity.getCredentialId() + "/content" : null);
        return view;
    }

    private static <T> ResponseEntity<String> pageOf(Page<T> page,
            java.util.function.Function<T, ObjectNode> viewer) {
        ObjectNode body = StrictJson.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        page.getContent().forEach(item -> data.add(viewer.apply(item)));
        body.put("total", page.getTotalElements());
        body.put("page", page.getNumber() + 1);
        body.put("size", page.getSize());
        body.put("totalPages", page.getTotalPages());
        return ResponseEntity.ok(body.toString());
    }
}
