package io.github.surezzzzzz.sdk.license.server.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.RuleModelWire;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.ClockPolicy;
import io.github.surezzzzzz.sdk.license.core.model.OrganizationPolicy;
import io.github.surezzzzzz.sdk.license.core.model.RevocationPolicy;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 模型快照中的业务政策接线与发布级校验。
 *
 * <p>规则条件仍由 {@link RuleModelWire} 负责；本类只校验规则之外的政策，避免签发器
 * 根据启动配置补出时钟、设备、组织或吊销语义。</p>
 *
 * @author surezzzzzz
 */
public final class ModelPolicyWire {

    private static final Set<String> TOP_LEVEL = set("id", "version", "ruleProtocol", "ruleVersion",
            "fields", "actions", "subjectPolicy", "validityPolicy", "connectivityPolicy", "clockPolicy",
            "devicePolicy", "organizationPolicy", "quotaDefinitions", "quotaConstraints", "revocationPolicy",
            "requiredCapabilities");
    private static final Set<String> KINDS = set("DEPLOYMENT", "PERSON", "SERVICE");
    private static final Set<String> ELIGIBILITY = set("ALLOCATED_DEPLOYMENTS", "CUSTOMER_MEMBERS",
            "ASSIGNED_SUBJECTS");
    private static final Set<String> MODES = set("OFFLINE", "ONLINE", "HYBRID");
    private static final Set<String> VALIDITY = set("FIXED", "PERPETUAL");
    private static final Set<String> QUOTA_KINDS = set("NAMED_SEAT", "CONCURRENT", "CONSUMPTION");
    private static final Set<String> ENFORCEMENT = set("LOCAL", "ONLINE");
    private static final Set<String> WINDOWS = set("TOTAL", "FIXED_WINDOW");
    private static final Set<String> CAPABILITIES = set("license-condition/1", "clock-state/1",
            "subject-binding/1", "hybrid-lease/1", "organization-lease/1", "revocation-batch/1",
            "quota-local/1", "named-seat/1", "device-weighted/hardware/1", "device-key/p256/1");

    private ModelPolicyWire() {
    }

    /** 校验并返回规范 JSON；不会修改调用方树。 */
    public static String validate(JsonNode input, String modelId, long version,
                                  String productCapabilities) {
        if (input == null || !input.isObject()) {
            throw invalid("模型快照必须为 JSON 对象");
        }
        ObjectNode snapshot = (ObjectNode) input.deepCopy();
        // id/version/rule 身份由服务端按模型行写入；草稿输入不得自报身份，未知字段仍立即拒绝。
        requireNoUnknown(snapshot, TOP_LEVEL);
        putIdentity(snapshot, modelId, version);
        requireKeys(snapshot, TOP_LEVEL);
        validateRules(snapshot);
        validateSubject(snapshot.get("subjectPolicy"));
        validateValidity(snapshot.get("validityPolicy"), snapshot.path("actions"));
        validateConnectivity(snapshot.get("connectivityPolicy"), snapshot.path("actions"));
        validateClock(snapshot.get("clockPolicy"));
        validateDevice(snapshot.get("devicePolicy"), snapshot.get("subjectPolicy"));
        validateOrganization(snapshot.get("organizationPolicy"));
        validateQuota(snapshot.get("quotaDefinitions"), snapshot.get("quotaConstraints"),
                snapshot.path("fields").path("rights"), snapshot.path("actions"), snapshot.get("subjectPolicy"));
        validateRevocation(snapshot.get("revocationPolicy"));
        validateCapabilities(snapshot.get("requiredCapabilities"), productCapabilities, snapshot);
        return snapshot.toString();
    }

    /** 解析已发布快照，供签发器复制政策节点。 */
    public static ObjectNode parse(String snapshot) {
        try {
            ObjectNode value = StrictJson.parse(snapshot, ObjectNode.class);
            validate(value, value.path("id").asText(null), value.path("version").asLong(0), null);
            return value;
        } catch (LicenseHttpException failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalid("模型政策快照不可解析");
        }
    }

    private static void validateRules(ObjectNode snapshot) {
        try {
            RuleModelWire.fromSnapshot(snapshot);
        } catch (Exception failure) {
            throw invalid("模型规则未通过结构校验");
        }
        String id = text(snapshot, "id");
        long version = integer(snapshot, "version");
        if (id.length() == 0 || id.length() > 64 || version < 1
                || !"license-condition".equals(text(snapshot, "ruleProtocol"))
                || integer(snapshot, "ruleVersion") != 1) {
            throw invalid("模型标识或规则协议无效");
        }
    }

    private static void validateSubject(JsonNode node) {
        ObjectNode value = object(node, "subjectPolicy");
        requireKeys(value, set("allowedKinds", "allowedEligibilityKinds", "offlineProofProfiles"));
        strings(value.get("allowedKinds"), KINDS, 16, "允许对象种类");
        strings(value.get("allowedEligibilityKinds"), ELIGIBILITY, 32, "允许资格种类");
        strings(value.get("offlineProofProfiles"), (Set<String>) null, 96, "离线身份能力档案");
        if (value.get("allowedKinds").size() == 0 || value.get("allowedEligibilityKinds").size() == 0) {
            throw invalid("对象或资格政策不能为空");
        }
        for (JsonNode kind : value.get("allowedKinds")) {
            if ("DEPLOYMENT".equals(kind.asText())
                    && !contains(value.get("allowedEligibilityKinds"), "ALLOCATED_DEPLOYMENTS")) {
                throw invalid("部署必须使用部署分配资格");
            }
            if ("SERVICE".equals(kind.asText())
                    && contains(value.get("allowedEligibilityKinds"), "CUSTOMER_MEMBERS")) {
                throw invalid("服务不得使用客户成员资格");
            }
        }
    }

    private static void validateValidity(JsonNode node, JsonNode actions) {
        ObjectNode value = object(node, "validityPolicy");
        requireKeys(value, set("allowedKinds", "minimumStartsAt", "maximumStartsAt",
                "minimumDurationSeconds", "maximumDurationSeconds", "maximumGraceSeconds", "graceActions"));
        strings(value.get("allowedKinds"), VALIDITY, 16, "期限种类");
        long minStart = integer(value, "minimumStartsAt");
        long maxStart = integer(value, "maximumStartsAt");
        long minDuration = integer(value, "minimumDurationSeconds");
        long maxDuration = integer(value, "maximumDurationSeconds");
        long maxGrace = integer(value, "maximumGraceSeconds");
        if (minStart < 0 || maxStart < minStart || minDuration < 1 || maxDuration < minDuration || maxGrace < 0) {
            throw invalid("期限政策数值范围无效");
        }
        strings(value.get("graceActions"), (Set<String>) null, 128, "宽限动作");
        ensureActions(value.get("graceActions"), actions, "宽限动作");
    }

    private static void validateConnectivity(JsonNode node, JsonNode actions) {
        ObjectNode value = object(node, "connectivityPolicy");
        requireNoUnknown(value, set("allowedModes", "refreshIntervalSeconds", "leaseDurationSeconds",
                "outageDurationSeconds", "outageActions"));
        if (!value.has("allowedModes")) {
            throw invalid("连接模式缺失");
        }
        strings(value.get("allowedModes"), MODES, 16, "连接模式");
        boolean hybrid = contains(value.get("allowedModes"), "HYBRID");
        boolean anyLeaseField = value.has("refreshIntervalSeconds") || value.has("leaseDurationSeconds")
                || value.has("outageDurationSeconds") || value.has("outageActions");
        if (hybrid) {
            long refresh = integer(value, "refreshIntervalSeconds");
            long lease = integer(value, "leaseDurationSeconds");
            long outage = integer(value, "outageDurationSeconds");
            strings(value.get("outageActions"), (Set<String>) null, 128, "断网动作");
            ensureActions(value.get("outageActions"), actions, "断网动作");
            if (refresh < 1 || lease < refresh || outage < 0) {
                throw invalid("混合连接政策的租约参数无效");
            }
        } else if (anyLeaseField) {
            throw invalid("非混合连接模式不得携带租约参数");
        }
    }

    private static void validateClock(JsonNode node) {
        ObjectNode value = object(node, "clockPolicy");
        requireKeys(value, set("skewSeconds", "rollbackToleranceSeconds"));
        try {
            ClockPolicy.of((int) integer(value, "skewSeconds"), (int) integer(value, "rollbackToleranceSeconds"));
        } catch (RuntimeException failure) {
            throw invalid("时钟政策无效");
        }
    }

    private static void validateDevice(JsonNode node, JsonNode subjectNode) {
        ObjectNode value = object(node, "devicePolicy");
        String mode = text(value, "mode");
        if ("NONE".equals(mode)) {
            requireKeys(value, set("mode"));
        } else if ("WEIGHTED".equals(mode)) {
            requireKeys(value, set("mode", "profileVersion", "components", "minimumAvailableWeight",
                    "maximumMismatchWeight"));
            if (!"hardware/1".equals(text(value, "profileVersion")) || !value.get("components").isObject()) {
                throw invalid("加权设备政策档案无效");
            }
            int total = 0;
            Set<String> names = set("systemUuid", "boardSn", "systemDiskSn", "primaryNic");
            ObjectNode components = object(value.get("components"), "components");
            for (Iterator<Map.Entry<String, JsonNode>> it = components.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (!names.contains(entry.getKey()) || !entry.getValue().canConvertToInt()
                        || entry.getValue().intValue() < 1 || entry.getValue().intValue() > 100) {
                    throw invalid("设备分量或权重无效");
                }
                total += entry.getValue().intValue();
            }
            int minimum = intValue(value, "minimumAvailableWeight");
            int mismatch = intValue(value, "maximumMismatchWeight");
            if (components.size() == 0 || minimum < 1 || mismatch < 0 || mismatch >= minimum || minimum > total) {
                throw invalid("设备权重门槛无效");
            }
        } else if ("DEVICE_KEY".equals(mode)) {
            requireKeys(value, set("mode", "requireHardwareBacked"));
            if (!value.get("requireHardwareBacked").isBoolean()) {
                throw invalid("设备密钥政策无效");
            }
        } else {
            throw invalid("设备政策模式无效");
        }
        if (!"NONE".equals(mode)) {
            ArrayNode kinds = (ArrayNode) object(subjectNode, "subjectPolicy").get("allowedKinds");
            for (JsonNode kind : kinds) {
                if (!"DEPLOYMENT".equals(kind.asText())) {
                    throw invalid("非部署对象不得使用设备绑定政策");
                }
            }
        }
    }

    private static void validateOrganization(JsonNode node) {
        ObjectNode value = object(node, "organizationPolicy");
        JsonNode requiredNode = value.get("required");
        if (requiredNode == null || !requiredNode.isBoolean()) {
            throw invalid("组织政策 required 必须为布尔值");
        }
        boolean required = requiredNode.booleanValue();
        if (!required) {
            requireKeys(value, set("required"));
            return;
        }
        requireKeys(value, set("required", "maxObservationAgeSeconds", "membershipLeaseSeconds"));
        try {
            OrganizationPolicy.required(intValue(value, "maxObservationAgeSeconds"),
                    intValue(value, "membershipLeaseSeconds"));
        } catch (RuntimeException failure) {
            throw invalid("组织成员政策无效");
        }
    }

    private static void validateQuota(JsonNode definitionsNode, JsonNode constraintsNode, JsonNode rightsNode,
                                      JsonNode actions, JsonNode subjectNode) {
        ObjectNode definitions = object(definitionsNode, "quotaDefinitions");
        ObjectNode constraints = object(constraintsNode, "quotaConstraints");
        ObjectNode subject = object(subjectNode, "subjectPolicy");
        JsonNode allowedKinds = subject.get("allowedKinds");
        JsonNode allowedEligibilityKinds = subject.get("allowedEligibilityKinds");
        for (Iterator<Map.Entry<String, JsonNode>> it = definitions.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> entry = it.next();
            ObjectNode definition = object(entry.getValue(), "quotaDefinition");
            requireNoUnknown(definition, set("limitRight", "kind", "enforcement", "window", "periodSeconds",
                    "maxReservationSeconds"));
            if (!definition.has("limitRight") || !definition.has("kind") || !definition.has("enforcement")) {
                throw invalid("额度定义缺少字段");
            }
            String limitRight = text(definition, "limitRight");
            String kind = text(definition, "kind");
            String enforcement = text(definition, "enforcement");
            JsonNode right = rightsNode.get(limitRight);
            if (limitRight.length() == 0 || limitRight.length() > 64 || !isAscii(limitRight)
                    || right == null || !right.isObject()
                    || !"INTEGER".equals(right.path("type").asText())
                    || !QUOTA_KINDS.contains(kind) || !ENFORCEMENT.contains(enforcement)) {
                throw invalid("额度定义无效");
            }
            boolean hasWindow = definition.has("window");
            boolean hasPeriod = definition.has("periodSeconds");
            boolean hasMaxReservation = definition.has("maxReservationSeconds");
            if ("NAMED_SEAT".equals(kind) && (hasWindow || hasPeriod || hasMaxReservation)) {
                throw invalid("实名席位不得携带窗口参数");
            }
            if ("NAMED_SEAT".equals(kind)
                    && (!"ONLINE".equals(enforcement)
                    || !contains(allowedEligibilityKinds, "ASSIGNED_SUBJECTS")
                    || (!contains(allowedKinds, "PERSON") && !contains(allowedKinds, "SERVICE")))) {
                throw invalid("实名席位必须绑定在线固定资格");
            }
            if ("CONCURRENT".equals(kind) && (hasWindow || hasPeriod)) {
                throw invalid("并发额度不得携带消费窗口");
            }
            if (hasMaxReservation
                    && !("CONCURRENT".equals(kind) && "ONLINE".equals(enforcement))) {
                throw invalid("保留时长只适用于在线并发额度");
            }
            if ("CONCURRENT".equals(kind) && "ONLINE".equals(enforcement)
                    && (!hasMaxReservation || !isNumber(definition, "maxReservationSeconds")
                    || intValue(definition, "maxReservationSeconds") < 1)) {
                throw invalid("在线并发额度必须声明保留时长");
            }
            if ("LOCAL".equals(enforcement)
                    && (!contains(allowedKinds, "DEPLOYMENT")
                    || !contains(allowedEligibilityKinds, "ALLOCATED_DEPLOYMENTS"))) {
                throw invalid("本地额度必须绑定部署分配资格");
            }
            if ("CONSUMPTION".equals(kind)) {
                if (!hasWindow || !WINDOWS.contains(text(definition, "window"))) {
                    throw invalid("消费额度必须声明窗口");
                }
                if ("FIXED_WINDOW".equals(text(definition, "window"))
                        && (!hasPeriod || !isNumber(definition, "periodSeconds")
                        || integer(definition, "periodSeconds") < 1)) {
                    throw invalid("固定消费窗口必须声明周期");
                }
                if ("TOTAL".equals(text(definition, "window")) && hasPeriod) {
                    throw invalid("总消费额度不得声明周期");
                }
            }
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = constraints.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> entry = it.next();
            if (!actions.isObject() || !actions.has(entry.getKey())) {
                throw invalid("额度约束引用未声明动作");
            }
            strings(entry.getValue(), definitions.fieldNames(), 128, "动作额度约束");
        }
    }

    private static void validateRevocation(JsonNode node) {
        ObjectNode value = object(node, "revocationPolicy");
        String mode = text(value, "mode");
        if ("OPTIONAL".equals(mode)) {
            requireKeys(value, set("mode"));
        } else if ("REQUIRED".equals(mode)) {
            requireKeys(value, set("mode", "maxAgeSeconds"));
            if (integer(value, "maxAgeSeconds") < 1) {
                throw invalid("吊销资料新鲜度无效");
            }
        } else {
            throw invalid("吊销政策模式无效");
        }
        try {
            if ("REQUIRED".equals(mode)) {
                RevocationPolicy.required(integer(value, "maxAgeSeconds"));
            } else {
                RevocationPolicy.optional();
            }
        } catch (RuntimeException failure) {
            throw invalid("吊销政策无效");
        }
    }

    private static void validateCapabilities(JsonNode node, String productCapabilities, ObjectNode snapshot) {
        Set<String> values = strings(node, (Set<String>) null, 128, "必需能力");
        for (String capability : values) {
            if ((!CAPABILITIES.contains(capability) && !capability.startsWith("subject-proof/"))
                    || !isAscii(capability)) {
                throw invalid("必需能力未知");
            }
        }
        if (!values.contains("license-condition/1") || !values.contains("subject-binding/1")) {
            throw invalid("模型缺少基础能力");
        }
        JsonNode modes = snapshot.get("connectivityPolicy").get("allowedModes");
        if ((contains(modes, "OFFLINE") || contains(modes, "HYBRID"))
                && !values.contains("clock-state/1")) {
            throw invalid("离线或混合连接政策缺少时钟状态能力");
        }
        if (contains(snapshot.get("connectivityPolicy").get("allowedModes"), "HYBRID")
                && !values.contains("hybrid-lease/1")) {
            throw invalid("混合连接政策缺少租约能力");
        }
        if (contains(snapshot.get("organizationPolicy").get("required"), true)
                && !values.contains("organization-lease/1")) {
            throw invalid("组织政策缺少组织租约能力");
        }
        String deviceMode = snapshot.get("devicePolicy").path("mode").asText();
        if ("WEIGHTED".equals(deviceMode) && !values.contains("device-weighted/hardware/1")) {
            throw invalid("加权设备政策缺少设备能力");
        }
        if ("DEVICE_KEY".equals(deviceMode) && !values.contains("device-key/p256/1")) {
            throw invalid("设备密钥政策缺少设备能力");
        }
        if ("REQUIRED".equals(snapshot.get("revocationPolicy").path("mode").asText())
                && !values.contains("revocation-batch/1")) {
            throw invalid("强制吊销政策缺少批次能力");
        }
        JsonNode definitions = snapshot.get("quotaDefinitions");
        boolean localQuota = false;
        boolean namedSeat = false;
        for (JsonNode definition : definitions) {
            localQuota |= "LOCAL".equals(definition.path("enforcement").asText());
            namedSeat |= "NAMED_SEAT".equals(definition.path("kind").asText());
        }
        if (localQuota && !values.contains("quota-local/1")) {
            throw invalid("本地额度政策缺少额度能力");
        }
        if (namedSeat && !values.contains("named-seat/1")) {
            throw invalid("实名席位政策缺少席位能力");
        }
        JsonNode subject = snapshot.get("subjectPolicy");
        if ((contains(modes, "OFFLINE") || contains(modes, "HYBRID"))
                && (contains(subject.get("allowedKinds"), "PERSON")
                || contains(subject.get("allowedKinds"), "SERVICE"))) {
            JsonNode profiles = subject.get("offlineProofProfiles");
            if (profiles.size() == 0) {
                throw invalid("人员或服务的离线政策缺少身份能力档案");
            }
            for (JsonNode profile : profiles) {
                if (!values.contains("subject-proof/" + profile.asText())) {
                    throw invalid("离线身份档案缺少对应能力");
                }
            }
        }
        if (productCapabilities != null && !productCapabilities.trim().isEmpty()) {
            Set<String> supported = parseArray(productCapabilities);
            if (!supported.containsAll(values)) {
                throw invalid("产品未登记模型所需能力");
            }
        }
    }

    private static Set<String> parseArray(String json) {
        try {
            JsonNode node = StrictJson.mapper().readTree(json);
            return strings(node, (Set<String>) null, 128, "产品能力");
        } catch (Exception failure) {
            throw invalid("产品能力清单损坏");
        }
    }

    private static Set<String> strings(JsonNode node, Set<String> allowed, int maxLength, String label) {
        if (node == null || !node.isArray() || node.size() > 256) {
            throw invalid(label + "必须为数组");
        }
        Set<String> values = new LinkedHashSet<String>();
        for (JsonNode value : node) {
            if (!value.isTextual() || value.textValue().length() == 0 || value.textValue().length() > maxLength
                    || !values.add(value.textValue()) || allowed != null && !allowed.contains(value.textValue())) {
                throw invalid(label + "包含非法值");
            }
        }
        return values;
    }

    private static Set<String> strings(JsonNode node, Iterator<String> allowedIterator, int maxLength, String label) {
        Set<String> allowed = null;
        if (allowedIterator != null) {
            allowed = new HashSet<String>();
            while (allowedIterator.hasNext()) {
                allowed.add(allowedIterator.next());
            }
        }
        return strings(node, allowed, maxLength, label);
    }

    private static boolean contains(JsonNode array, String value) {
        if (array == null || !array.isArray()) {
            return false;
        }
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(JsonNode value, boolean expected) {
        return value != null && value.isBoolean() && value.booleanValue() == expected;
    }

    private static void ensureActions(JsonNode selected, JsonNode actions, String label) {
        if (selected == null || actions == null || !actions.isObject()) {
            throw invalid(label + "引用动作不可用");
        }
        for (JsonNode action : selected) {
            if (!actions.has(action.asText())) {
                throw invalid(label + "含未声明动作");
            }
        }
    }

    private static ObjectNode object(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw invalid(label + "必须为对象");
        }
        return (ObjectNode) node;
    }

    private static String text(ObjectNode object, String name) {
        JsonNode node = object.get(name);
        if (node == null || !node.isTextual()) {
            throw invalid(name + "必须为字符串");
        }
        return node.textValue();
    }

    private static long integer(ObjectNode object, String name) {
        JsonNode node = object.get(name);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw invalid(name + "必须为整数");
        }
        return node.longValue();
    }

    private static int intValue(ObjectNode object, String name) {
        long value = integer(object, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid(name + "超出整数范围");
        }
        return (int) value;
    }

    private static void requireKeys(ObjectNode object, Set<String> allowed) {
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw invalid("政策含未声明字段");
            }
        }
        for (String key : allowed) {
            if (!object.has(key)) {
                throw invalid("政策缺少字段");
            }
        }
    }

    private static void requireNoUnknown(ObjectNode object, Set<String> allowed) {
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw invalid("政策含未声明字段");
            }
        }
    }

    private static boolean isNumber(ObjectNode object, String name) {
        JsonNode node = object.get(name);
        return node != null && node.isIntegralNumber();
    }

    private static void putIdentity(ObjectNode snapshot, String modelId, long version) {
        if (modelId == null || modelId.length() == 0 || version < 1) {
            throw invalid("模型身份无效");
        }
        snapshot.put("id", modelId);
        snapshot.put("version", version);
        snapshot.put("ruleProtocol", "license-condition");
        snapshot.put("ruleVersion", 1);
    }

    private static LicenseHttpException invalid(String message) {
        return LicenseHttpException.unprocessable(message);
    }

    private static Set<String> set(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    private static boolean isAscii(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < 0x21 || current > 0x7e) {
                return false;
            }
        }
        return true;
    }
}
