package io.github.surezzzzzz.sdk.license.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.support.GrantValidityHelper;
import io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OFFLINE 凭据载荷组装器：把商业授权展开为协议 2 载荷 JSON（与
 * {@link io.github.surezzzzzz.sdk.license.core.codec.Protocol2PayloadWire} 契约逐键对齐）。
 *
 * <p>期限只取授权行持久事实（缺失/非法即拒绝，不按签发时刻补造）；模型快照取授权关联的
 * 已发布模型原文，政策节点直接展开（没有模型或模型未发布即拒绝）；权益取授权行权益快照。
 * subjectBinding 联合形态：DEPLOYMENT=kind/activationId/activationGeneration，
 * PERSON/SERVICE=kind/subjectRef（固定资格另含 assignment 两键）。</p>
 *
 * @author surezzzzzz
 */
public final class OfflineCredentialComposer {

    private OfflineCredentialComposer() {
    }

    /**
     * 组装协议 2 载荷。
     *
     * @param grant            授权行（身份/权益/期限持久事实）
     * @param product          产品行（声明动作与能力）
     * @param modelSnapshotText 授权关联的已发布模型快照原文（必须为完整发布政策）
     * @param subjectRefJson   对象绑定 JSON（DEPLOYMENT 含 activationId/activationGeneration；
     *                         PERSON/SERVICE 含 sourceId/subjectId，固定资格另含 assignment 两键）
     * @param jti              凭据编号
     * @param issuedAt         签发时刻（epoch 秒）
     */
    public static String compose(GrantRevisionEntity grant, ProductEntity product,
                                 String modelSnapshotText, String subjectRefJson,
                                 String jti, long issuedAt) {
        if (modelSnapshotText == null || modelSnapshotText.isEmpty()) {
            throw new IllegalArgumentException("签发必须关联完整已发布模型");
        }
        return compose(grant, product, ModelPolicyWire.parse(modelSnapshotText), subjectRefJson,
                jti, issuedAt, Collections.<NamedSeatReference>emptyList(),
                Collections.<Map<String, Object>>emptyList(), Collections.<String, Long>emptyMap());
    }

    /**
     * 使用已发布模型和真实实名快照组装凭据；额度/实名引用不从请求或产品声明猜造。
     */
    public static String compose(GrantRevisionEntity grant, ProductEntity product,
                                 ModelVersionEntity model, String subjectRefJson,
                                 String jti, long issuedAt, List<NamedSeatReference> namedSeats) {
        if (model == null || !"PUBLISHED".equals(model.getStatus())) {
            throw new IllegalArgumentException("签发必须使用已发布模型");
        }
        return compose(grant, product, ModelPolicyWire.parse(model.getSnapshot()), subjectRefJson,
                jti, issuedAt, namedSeats, Collections.<Map<String, Object>>emptyList(),
                Collections.<String, Long>emptyMap());
    }

    /**
     * 使用已发布模型、实名资格和真实部署额度分配快照组装凭据。
     *
     * <p>额度分配不是模型声明；它必须由部署/额度事务端口提供，调用方不能用请求体或权益值猜造。
     * 分配项采用协议 2 的稳定字段，方法会在写入前做形态、版本、指标和保留窗口校验。</p>
     */
    public static String compose(GrantRevisionEntity grant, ProductEntity product,
                                 ModelVersionEntity model, String subjectRefJson,
                                 String jti, long issuedAt, List<NamedSeatReference> namedSeats,
                                 List<Map<String, Object>> quotaAllocations) {
        if (model == null || !"PUBLISHED".equals(model.getStatus())) {
            throw new IllegalArgumentException("签发必须使用已发布模型");
        }
        return compose(grant, product, ModelPolicyWire.parse(model.getSnapshot()), subjectRefJson,
                jti, issuedAt, namedSeats, quotaAllocations, Collections.<String, Long>emptyMap());
    }

    /**
     * 使用已发布模型、实名资格、部署额度和授权级固定窗口锚组装凭据。
     *
     * <p>固定窗口锚是授权指标的持久事实，不能在 Composer 内按签发时刻推导；未提供时
     * 对应固定窗口签发会失败关闭。</p>
     */
    public static String compose(GrantRevisionEntity grant, ProductEntity product,
                                 ModelVersionEntity model, String subjectRefJson,
                                 String jti, long issuedAt, List<NamedSeatReference> namedSeats,
                                 List<Map<String, Object>> quotaAllocations,
                                 Map<String, Long> windowAnchors) {
        if (model == null || !"PUBLISHED".equals(model.getStatus())) {
            throw new IllegalArgumentException("签发必须使用已发布模型");
        }
        return compose(grant, product, ModelPolicyWire.parse(model.getSnapshot()), subjectRefJson,
                jti, issuedAt, namedSeats, quotaAllocations, windowAnchors);
    }

    private static String compose(GrantRevisionEntity grant, ProductEntity product,
                                  ObjectNode policy, String subjectRefJson,
                                  String jti, long issuedAt, List<NamedSeatReference> namedSeats,
                                  List<Map<String, Object>> quotaAllocations,
                                  Map<String, Long> windowAnchors) {
        JsonNode ref = parseRef(subjectRefJson);
        ValidityPolicy validity = GrantValidityHelper.fromGrant(grant);
        ObjectNode payload = StrictJson.mapper().createObjectNode();
        payload.put("protocolVersion", 2);
        payload.put("credentialKind", "OFFLINE");
        payload.put("jti", jti);
        // 协议 2 的 iss 是稳定签发方标识，必须与验证上下文中的 issuerId 原值一致。
        payload.put("iss", product.getIssuerId());
        payload.put("aud", product.getProductId());
        payload.put("customerId", grant.getCustomerId());
        payload.put("grantId", grant.getGrantId());
        payload.put("grantRevision", grant.getGrantRevision());
        payload.put("grantStateVersion", grant.getGrantStateVersion());
        payload.putPOJO("eligibilityPolicy", eligibility(grant));
        payload.set("subjectBinding", subjectBinding(grant, ref));
        payload.put("iat", issuedAt);
        payload.put("nbf", validity.getStartsAt());
        if (validity.getKind() == ValidityPolicy.Kind.FIXED) {
            payload.put("exp", validity.getCommercialGraceUntil());
        }
        payload.set("validity", validityNode(validity, policy));
        payload.set("clockPolicy", copyObject(policy, "clockPolicy"));
        payload.set("deviceBinding", deviceBinding(policy));
        payload.set("organizationPolicy", copyObject(policy, "organizationPolicy"));
        payload.set("model", modelSnapshot(policy));
        payload.set("rights", rightsNode(grant));
        payload.set("quotaPolicy", quotaPolicy(grant, policy, namedSeats, quotaAllocations, windowAnchors));
        payload.set("quotaAllocations", quotaAllocationNodes(quotaAllocations));
        payload.set("namedSeatReferences", namedSeatNodes(namedSeats));
        payload.set("revocationPolicy", copyObject(policy, "revocationPolicy"));
        payload.set("requiredCapabilities", copyArray(policy, "requiredCapabilities"));
        return payload.toString();
    }

    private static ObjectNode eligibility(GrantRevisionEntity grant) {
        ObjectNode node = StrictJson.mapper().createObjectNode();
        node.put("kind", grant.getEligibilityKind());
        if (grant.getOfflineProofProfile() != null) {
            node.put("offlineProofProfile", grant.getOfflineProofProfile());
        }
        return node;
    }

    /** 对象绑定联合结构；PERSON/SERVICE 的可信身份位于嵌套 subjectRef。 */
    private static ObjectNode subjectBinding(GrantRevisionEntity grant, JsonNode ref) {
        ObjectNode node = StrictJson.mapper().createObjectNode();
        node.put("kind", grant.getSubjectKind());
        if ("DEPLOYMENT".equals(grant.getSubjectKind())) {
            if (!ref.isObject() || (ref.has("kind") && !grant.getSubjectKind().equals(ref.path("kind").asText()))
                    || !ref.path("activationId").isTextual() || ref.path("activationId").asText().isEmpty()
                    || !ref.path("activationGeneration").canConvertToLong()
                    || ref.path("activationGeneration").asLong() < 1) {
                throw new IllegalArgumentException("部署绑定缺少真实激活事实");
            }
            if (ref.has("sourceId") || ref.has("subjectId") || ref.has("assignmentId")
                    || ref.has("assignmentGeneration")) {
                throw new IllegalArgumentException("部署绑定混入人员或资格字段");
            }
            node.put("activationId", ref.path("activationId").asText());
            node.put("activationGeneration", ref.path("activationGeneration").asLong());
            return node;
        }
        if (!ref.isObject() || (ref.has("kind") && !grant.getSubjectKind().equals(ref.path("kind").asText()))
                || !ref.path("sourceId").isTextual() || ref.path("sourceId").asText().isEmpty()
                || !ref.path("subjectId").isTextual() || ref.path("subjectId").asText().isEmpty()) {
            throw new IllegalArgumentException("人员或服务绑定缺少可信身份事实");
        }
        boolean assigned = "ASSIGNED_SUBJECTS".equals(grant.getEligibilityKind());
        boolean customerMembers = "CUSTOMER_MEMBERS".equals(grant.getEligibilityKind());
        if (!assigned && !customerMembers) {
            throw new IllegalArgumentException("人员或服务资格政策无效");
        }
        if (assigned != ref.hasNonNull("assignmentId") || customerMembers
                && (ref.has("assignmentId") || ref.has("assignmentGeneration"))) {
            throw new IllegalArgumentException("固定资格绑定与资格政策不一致");
        }
        if (!ref.hasNonNull("assignmentId") && ref.has("assignmentGeneration")) {
            throw new IllegalArgumentException("固定资格绑定缺少资格编号");
        }
        ObjectNode subject = node.putObject("subjectRef");
        subject.put("kind", grant.getSubjectKind());
        subject.put("sourceId", ref.path("sourceId").asText());
        subject.put("subjectId", ref.path("subjectId").asText());
        if (ref.hasNonNull("assignmentId")) {
            if (!ref.path("assignmentId").isTextual() || ref.path("assignmentId").asText().isEmpty()
                    || !ref.path("assignmentGeneration").canConvertToLong()
                    || ref.path("assignmentGeneration").asLong() < 1) {
                throw new IllegalArgumentException("固定资格绑定缺少真实代数");
            }
            node.put("assignmentId", ref.path("assignmentId").asText());
            node.put("assignmentGeneration", ref.path("assignmentGeneration").asLong());
        }
        return node;
    }

    /** 期限节点；实际授权期限来自持久行，动作集合来自已发布模型政策。 */
    private static ObjectNode validityNode(ValidityPolicy validity, ObjectNode policy) {
        ObjectNode node = StrictJson.mapper().createObjectNode();
        boolean perpetual = validity.getKind() == ValidityPolicy.Kind.PERPETUAL;
        node.put("kind", validity.getKind().getCode());
        node.put("startsAt", validity.getStartsAt());
        ObjectNode validityPolicy = copyObject(policy, "validityPolicy");
        JsonNode graceActions = validityPolicy.get("graceActions");
        if (graceActions == null || !graceActions.isArray()) {
            throw new IllegalArgumentException("模型期限政策缺少宽限动作");
        }
        if (perpetual) {
            node.putNull("maintenanceUntil");
            node.set("graceActions", graceActions.deepCopy());
            return node;
        }
        node.put("serviceUntil", validity.getServiceUntil());
        node.put("commercialGraceUntil", validity.getCommercialGraceUntil());
        node.putNull("maintenanceUntil");
        node.set("graceActions", graceActions.deepCopy());
        return node;
    }

    private static JsonNode parseRef(String json) {
        try {
            JsonNode result = StrictJson.mapper().readTree(json);
            if (result == null || !result.isObject()) {
                throw new IllegalArgumentException("对象绑定引用形态非法");
            }
            return result;
        } catch (Exception failure) {
            throw new IllegalArgumentException("对象绑定引用形态非法");
        }
    }

    /** 从完整政策快照复制规则模型，不重复嵌入顶层政策。 */
    private static JsonNode modelSnapshot(ObjectNode policy) {
        ObjectNode model = StrictJson.mapper().createObjectNode();
        for (String key : new String[] {"id", "version", "ruleProtocol", "ruleVersion", "fields", "actions"}) {
            JsonNode node = policy.get(key);
            if (node == null) {
                throw new IllegalArgumentException("模型规则快照缺少 " + key);
            }
            model.set(key, node.deepCopy());
        }
        return model;
    }

    /** 权益：授权行权益快照原样入载荷（缺省空对象）。 */
    private static JsonNode rightsNode(GrantRevisionEntity grant) {
        try {
            JsonNode result = grant.getRightsJson() == null || grant.getRightsJson().isEmpty()
                    ? StrictJson.mapper().createObjectNode()
                    : StrictJson.mapper().readTree(grant.getRightsJson());
            if (result == null || !result.isObject()) {
                throw new IllegalArgumentException("权益快照必须为对象");
            }
            return result;
        } catch (Exception failure) {
            throw new IllegalArgumentException("权益快照不可解析");
        }
    }

    private static ObjectNode deviceBinding(ObjectNode policy) {
        ObjectNode device = copyObject(policy, "devicePolicy");
        if (!"NONE".equals(device.path("mode").asText())) {
            // 设备采集/设备密钥持有性属于宿主可信 Provider；不能用空摘要或配置开关冒充。
            throw new IllegalArgumentException("当前未提供可信设备 Provider，拒绝签发设备绑定凭据");
        }
        return device;
    }

    private static ObjectNode quotaPolicy(GrantRevisionEntity grant, ObjectNode policy,
                                          List<NamedSeatReference> namedSeats,
                                          List<Map<String, Object>> quotaAllocations,
                                          Map<String, Long> windowAnchors) {
        ObjectNode definitions = copyObject(policy, "quotaDefinitions");
        ObjectNode constraints = copyObject(policy, "quotaConstraints");
        // 真实额度分配由宿主 SPI 提供；不能把声明直接变成可用余额。
        validateQuotaInputs(grant, definitions, namedSeats, quotaAllocations, windowAnchors);
        ObjectNode quota = StrictJson.mapper().createObjectNode();
        quota.set("definitions", definitions);
        quota.set("constraints", constraints);
        ObjectNode anchors = StrictJson.mapper().createObjectNode();
        if (windowAnchors != null) {
            for (Map.Entry<String, Long> entry : windowAnchors.entrySet()) {
                anchors.put(entry.getKey(), entry.getValue());
            }
        }
        quota.set("windowAnchors", anchors);
        return quota;
    }

    /** 校验模型声明与真实分配快照的关系，缺事实时失败关闭。 */
    private static void validateQuotaInputs(GrantRevisionEntity grant, ObjectNode definitions,
                                            List<NamedSeatReference> namedSeats,
                                            List<Map<String, Object>> allocations,
                                            Map<String, Long> windowAnchors) {
        Set<String> definitionMetrics = new HashSet<String>();
        Set<String> localMetrics = new HashSet<String>();
        Set<String> fixedWindowMetrics = new HashSet<String>();
        Set<String> namedSeatMetrics = new HashSet<String>();
        boolean localAllocationRequired = false;
        for (Map.Entry<String, JsonNode> entry : iterable(definitions)) {
            String metric = entry.getKey();
            JsonNode definition = entry.getValue();
            definitionMetrics.add(metric);
            if ("LOCAL".equals(definition.path("enforcement").asText())) {
                localAllocationRequired = true;
                localMetrics.add(metric);
            }
            if ("FIXED_WINDOW".equals(definition.path("window").asText())) {
                fixedWindowMetrics.add(metric);
            }
            if ("NAMED_SEAT".equals(definition.path("kind").asText())) {
                namedSeatMetrics.add(metric);
            }
        }
        Map<String, Long> anchors = windowAnchors == null
                ? Collections.<String, Long>emptyMap() : windowAnchors;
        if (!anchors.keySet().equals(fixedWindowMetrics)) {
            // windowAnchorAt 是授权级不可变事实，不能从签发时刻或启动时间推导。
            throw new IllegalArgumentException("固定窗口额度缺少或多出真实窗口锚点");
        }
        for (Map.Entry<String, Long> anchor : anchors.entrySet()) {
            if (anchor.getKey() == null || anchor.getKey().isEmpty()
                    || anchor.getValue() == null || anchor.getValue() <= 0L) {
                throw new IllegalArgumentException("固定窗口锚点无效");
            }
        }
        List<Map<String, Object>> values = allocations == null
                ? Collections.<Map<String, Object>>emptyList() : allocations;
        if (values.size() > 128) {
            throw new IllegalArgumentException("额度分配快照超过协议上限");
        }
        if (!"DEPLOYMENT".equals(grant.getSubjectKind()) && !values.isEmpty()) {
            throw new IllegalArgumentException("人员或服务凭据不得携带部署额度分配");
        }
        if (localAllocationRequired && values.isEmpty()) {
            throw new IllegalArgumentException("本地额度需要真实部署分配快照");
        }
        Set<String> allocationIds = new HashSet<String>();
        Set<String> allocationMetrics = new HashSet<String>();
        for (Map<String, Object> allocation : values) {
            if (allocation == null) {
                throw new IllegalArgumentException("额度分配快照不可为空");
            }
            requireAllocationKeys(allocation);
            String allocationId = text(allocation, "allocationId");
            String metric = text(allocation, "metric");
            long quantity = number(allocation, "quantity");
            long revision = number(allocation, "grantRevision");
            String retention = text(allocation, "retention");
            if (!allocationIds.add(allocationId) || !allocationMetrics.add(metric)
                    || !localMetrics.contains(metric) || !definitionMetrics.contains(metric) || quantity < 1
                    || revision != grant.getGrantRevision()
                    || !"FIXED".equals(retention) && !"PERPETUAL".equals(retention)) {
                throw new IllegalArgumentException("额度分配快照无效");
            }
            if ("FIXED".equals(retention)) {
                long retainUntil = number(allocation, "retainUntil");
                if (retainUntil <= 0) {
                    throw new IllegalArgumentException("额度分配保留截止无效");
                }
            } else if (allocation.containsKey("retainUntil")) {
                throw new IllegalArgumentException("永久额度分配不得携带保留截止");
            }
        }
        if (!allocationMetrics.containsAll(localMetrics)) {
            throw new IllegalArgumentException("本地额度缺少真实指标分配");
        }
        List<NamedSeatReference> seats = namedSeats == null
                ? Collections.<NamedSeatReference>emptyList() : namedSeats;
        if (seats.size() > 128 || (!namedSeatMetrics.isEmpty() && seats.isEmpty())) {
            throw new IllegalArgumentException("模型实名额度需要真实资格快照");
        }
        Set<String> seatSeen = new HashSet<String>();
        for (NamedSeatReference seat : seats) {
            if (!namedSeatMetrics.contains(seat.getMetric()) || !seatSeen.add(seat.getMetric())) {
                throw new IllegalArgumentException("实名资格快照与模型额度不一致");
            }
        }
        if (!seats.isEmpty() && !"ASSIGNED_SUBJECTS".equals(grant.getEligibilityKind())) {
            throw new IllegalArgumentException("动态成员凭据不得携带实名资格快照");
        }
    }

    /** 将已核验的分配快照原样输出；不从模型权益推导 quantity。 */
    private static ArrayNode quotaAllocationNodes(List<Map<String, Object>> allocations) {
        List<Map<String, Object>> values = allocations == null
                ? Collections.<Map<String, Object>>emptyList() : allocations;
        ArrayNode result = StrictJson.mapper().createArrayNode();
        for (Map<String, Object> allocation : values) {
            ObjectNode node = result.addObject();
            node.put("allocationId", text(allocation, "allocationId"));
            node.put("metric", text(allocation, "metric"));
            node.put("quantity", number(allocation, "quantity"));
            node.put("grantRevision", number(allocation, "grantRevision"));
            node.put("retention", text(allocation, "retention"));
            if (allocation.containsKey("retainUntil")) {
                node.put("retainUntil", number(allocation, "retainUntil"));
            }
        }
        return result;
    }

    private static Iterable<Map.Entry<String, JsonNode>> iterable(ObjectNode object) {
        List<Map.Entry<String, JsonNode>> entries = new java.util.ArrayList<Map.Entry<String, JsonNode>>();
        java.util.Iterator<Map.Entry<String, JsonNode>> iterator = object.fields();
        while (iterator.hasNext()) {
            entries.add(iterator.next());
        }
        return entries;
    }

    private static void requireAllocationKeys(Map<String, Object> allocation) {
        Set<String> allowed = new HashSet<String>(java.util.Arrays.asList(
                "allocationId", "metric", "quantity", "grantRevision", "retention"));
        if ("FIXED".equals(allocation.get("retention"))) {
            allowed.add("retainUntil");
        }
        if (!allowed.equals(allocation.keySet())) {
            throw new IllegalArgumentException("额度分配快照字段不完整");
        }
    }

    private static String text(Map<String, Object> value, String key) {
        Object result = value.get(key);
        if (!(result instanceof String) || ((String) result).isEmpty()) {
            throw new IllegalArgumentException("额度分配字段无效");
        }
        return (String) result;
    }

    private static long number(Map<String, Object> value, String key) {
        Object result = value.get(key);
        if (!(result instanceof Number)) {
            throw new IllegalArgumentException("额度分配字段无效");
        }
        return ((Number) result).longValue();
    }

    private static ArrayNode namedSeatNodes(List<NamedSeatReference> references) {
        ArrayNode result = StrictJson.mapper().createArrayNode();
        if (references == null) {
            return result;
        }
        for (NamedSeatReference reference : references) {
            ObjectNode value = result.addObject();
            value.put("assignmentId", reference.getAssignmentId());
            value.put("assignmentGeneration", reference.getAssignmentGeneration());
            value.put("metric", reference.getMetric());
            value.put("quantity", 1);
            value.put("retention", reference.getRetention().getCode());
            if (reference.getRetainUntil().isPresent()) {
                value.put("retainUntil", reference.getRetainUntil().getAsLong());
            }
        }
        return result;
    }

    private static ObjectNode copyObject(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("模型政策缺少 " + field);
        }
        return (ObjectNode) value.deepCopy();
    }

    private static ArrayNode copyArray(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("模型政策缺少 " + field);
        }
        return (ArrayNode) value.deepCopy();
    }
}
