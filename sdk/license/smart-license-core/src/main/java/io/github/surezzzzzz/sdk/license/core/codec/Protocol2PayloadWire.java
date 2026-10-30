package io.github.surezzzzzz.sdk.license.core.codec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.ClockPolicy;
import io.github.surezzzzzz.sdk.license.core.model.CredentialKind;
import io.github.surezzzzzz.sdk.license.core.model.DeviceBinding;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.Lease;
import io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference;
import io.github.surezzzzzz.sdk.license.core.model.OrganizationPolicy;
import io.github.surezzzzzz.sdk.license.core.model.RevocationPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.SubjectRef;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.core.support.ProtocolValueHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 协议 2 载荷 JSON 线形态：把严格 JSON 树接线为类型化 Protocol2Payload。
 *
 * <p>未知字段由 StrictJson 拒绝；嵌套结构逐字段转换，联合组合由 Payload 构造期校验。</p>
 *
 * @author surezzzzzz
 */
public final class Protocol2PayloadWire {

    @JsonProperty("protocolVersion")
    public int protocolVersion;

    @JsonProperty("credentialKind")
    public String credentialKind;

    @JsonProperty("jti")
    public String jti;

    @JsonProperty("iss")
    public String iss;

    @JsonProperty("aud")
    public String aud;

    @JsonProperty("customerId")
    public String customerId;

    @JsonProperty("grantId")
    public String grantId;

    @JsonProperty("grantRevision")
    public long grantRevision;

    @JsonProperty("grantStateVersion")
    public long grantStateVersion;

    @JsonProperty("eligibilityPolicy")
    public Map<String, Object> eligibilityPolicy;

    @JsonProperty("subjectBinding")
    public Map<String, Object> subjectBinding;

    @JsonProperty("iat")
    public long iat;

    @JsonProperty("nbf")
    public long nbf;

    @JsonProperty("exp")
    @JsonSetter(nulls = Nulls.FAIL)
    public Long exp;

    @JsonProperty("validity")
    public Map<String, Object> validity;

    @JsonProperty("clockPolicy")
    public Map<String, Object> clockPolicy;

    @JsonProperty("deviceBinding")
    public Map<String, Object> deviceBinding;

    @JsonProperty("organizationPolicy")
    public Map<String, Object> organizationPolicy;

    @JsonProperty("revocationPolicy")
    public Map<String, Object> revocationPolicy;

    @JsonProperty("lease")
    public Map<String, Object> lease;

    @JsonProperty("namedSeatReferences")
    public List<Map<String, Object>> namedSeatReferences;

    @JsonProperty("model")
    public Map<String, Object> model;

    @JsonProperty("rights")
    public Map<String, Object> rights;

    @JsonProperty("requiredCapabilities")
    public List<String> requiredCapabilities;

    @JsonProperty("quotaPolicy")
    public Map<String, Object> quotaPolicy;

    @JsonProperty("quotaAllocations")
    public List<Map<String, Object>> quotaAllocations;

    @JsonCreator
    public Protocol2PayloadWire() {
    }

    /** 转为类型化载荷（全部联合约束校验在构造期）。 */
    public Protocol2Payload toPayload() {
        if (protocolVersion != 2 || credentialKind == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        Protocol2Payload.Builder builder = Protocol2Payload.builder()
                .credentialKind(CredentialKind.fromCode(credentialKind))
                .jti(jti).issuerId(iss).productId(aud).customerId(customerId).grantId(grantId)
                .grantRevision(grantRevision).grantStateVersion(grantStateVersion)
                .issuedAt(iat).notBefore(nbf).expiresAt(exp)
                .eligibilityPolicy(wireEligibility())
                .subjectBinding(wireBinding())
                .validity(wireValidity())
                .clockPolicy(wireClock())
                .deviceBinding(wireDevice())
                .organizationPolicy(wireOrganization())
                .revocationPolicy(wireRevocation())
                .requiredCapabilities(requiredCapabilities == null
                        ? new ArrayList<String>() : requiredCapabilities)
                .quotaPolicy(quotaPolicy == null ? new HashMap<String, Object>() : quotaPolicy);

        if (lease != null) {
            builder.lease(wireLease());
        }
        builder.modelSnapshot(model == null ? new java.util.HashMap<String, Object>() : model)
                .rights(rights == null ? new java.util.HashMap<String, Object>() : rights);
        builder.quotaAllocations(quotaAllocations == null ? new ArrayList<Map<String, Object>>() : quotaAllocations);
        builder.namedSeatReferences(wireNamedSeats());
        return builder.build();
    }

    private EligibilityPolicy wireEligibility() {
        String kind = text(eligibilityPolicy, "kind");
        Object rawProfile = eligibilityPolicy.get("offlineProofProfile");
        if (rawProfile != null && !(rawProfile instanceof String)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        String profile = (String) rawProfile;
        if ("ALLOCATED_DEPLOYMENTS".equals(kind)) {
            // 部署资格不携带离线身份能力档案（PROTOCOL.2 §3）
            requireExactKeys(eligibilityPolicy, "kind");
            return EligibilityPolicy.allocatedDeployments();
        }
        if ("ASSIGNED_SUBJECTS".equals(kind)) {
            requireExactKeys(eligibilityPolicy, "kind", "offlineProofProfile");
            return EligibilityPolicy.assignedSubjects(profile);
        }
        if ("CUSTOMER_MEMBERS".equals(kind)) {
            // 动态组织型无固定离线档案（仅 HYBRID）
            requireExactKeys(eligibilityPolicy, "kind");
            return EligibilityPolicy.customerMembers();
        }
        throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
    }

    private io.github.surezzzzzz.sdk.license.core.model.SubjectBinding wireBinding() {
        String kind = text(subjectBinding, "kind");
        if ("DEPLOYMENT".equals(kind)) {
            // DEPLOYMENT 结构恰为 kind/activationId/activationGeneration，禁止人员字段
            requireExactKeys(subjectBinding, "kind", "activationId", "activationGeneration");
            return io.github.surezzzzzz.sdk.license.core.model.SubjectBinding.deployment(
                    text(subjectBinding, "activationId"), number(subjectBinding, "activationGeneration"));
        }
        // PERSON/SERVICE 的身份必须嵌在 subjectRef 中；禁止把 sourceId/subjectId
        // 平铺到 subjectBinding，避免与部署联合结构混用。
        SubjectKind subjectKind = SubjectKind.fromCode(kind);
        if (!subjectKind.isSubjectRefKind()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
        }
        Object rawSubjectRef = subjectBinding.get("subjectRef");
        Map<String, Object> subjectRef = asMap(rawSubjectRef);
        requireExactKeys(subjectRef, "kind", "sourceId", "subjectId");
        if (!kind.equals(text(subjectRef, "kind"))) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
        }
        SubjectRef ref = SubjectRef.of(subjectKind, text(subjectRef, "sourceId"),
                text(subjectRef, "subjectId"));

        String assignmentId = subjectBinding.get("assignmentId") instanceof String
                ? (String) subjectBinding.get("assignmentId") : null;
        String eligibilityKind = text(eligibilityPolicy, "kind");
        if ("ASSIGNED_SUBJECTS".equals(eligibilityKind)) {
            // 固定资格必须完整携带 assignmentId/assignmentGeneration。
            requireExactKeys(subjectBinding, "kind", "subjectRef", "assignmentId", "assignmentGeneration");
            if (assignmentId == null || assignmentId.isEmpty()) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
            }
            return io.github.surezzzzzz.sdk.license.core.model.SubjectBinding.personService(
                    ref, assignmentId, number(subjectBinding, "assignmentGeneration"));
        }
        if ("CUSTOMER_MEMBERS".equals(eligibilityKind)) {
            // 动态成员资格不得伪造固定资格字段。
            requireExactKeys(subjectBinding, "kind", "subjectRef");
            return io.github.surezzzzzz.sdk.license.core.model.SubjectBinding.personService(ref);
        }
        throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
    }

    private ValidityPolicy wireValidity() {
        String kind = text(validity, "kind");
        long startsAt = number(validity, "startsAt");
        List<String> grace = strings(validity.get("graceActions"));
        if ("FIXED".equals(kind)) {
            // FIXED 恰含 serviceUntil/commercialGraceUntil；maintenanceUntil 可选且保留（维护期动作约束）
            requireExactKeys(validity, "kind", "startsAt", "serviceUntil", "commercialGraceUntil",
                    "graceActions", "maintenanceUntil");
            Long maintenance = nullableLong(validity.get("maintenanceUntil"));
            ValidityPolicy fixed = ValidityPolicy.fixed(startsAt,
                    number(validity, "serviceUntil"), number(validity, "commercialGraceUntil"), grace);
            if (maintenance != null) {
                return ValidityPolicy.of(startsAt, number(validity, "serviceUntil"),
                        number(validity, "commercialGraceUntil"), maintenance, grace);
            }
            return fixed;
        }
        if ("PERPETUAL".equals(kind)) {
            // PERPETUAL 禁止 serviceUntil/commercialGraceUntil（PROTOCOL.2 §3）
            requireExactKeys(validity, "kind", "startsAt", "graceActions", "maintenanceUntil");
            return ValidityPolicy.perpetual(startsAt, nullableLong(validity.get("maintenanceUntil")), grace);
        }
        throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
    }

    private ClockPolicy wireClock() {
        requireExactKeys(clockPolicy, "skewSeconds", "rollbackToleranceSeconds");
        return ClockPolicy.of(intField(clockPolicy, "skewSeconds"),
                intField(clockPolicy, "rollbackToleranceSeconds"));
    }

    private DeviceBinding wireDevice() {
        String mode = text(deviceBinding, "mode");
        if ("NONE".equals(mode)) {
            requireExactKeys(deviceBinding, "mode");
            return DeviceBinding.none();
        }
        if ("WEIGHTED".equals(mode)) {
            requireExactKeys(deviceBinding, "mode", "profileVersion", "componentDigests", "componentWeights",
                    "minimumAvailableWeight", "maximumMismatchWeight");
            Map<String, String> digests = new HashMap<String, String>();
            Map<String, Integer> weights = new HashMap<String, Integer>();
            Map<String, Object> rawDigests = asMap(deviceBinding.get("componentDigests"));
            for (Map.Entry<String, Object> entry : rawDigests.entrySet()) {
                if (!(entry.getValue() instanceof String)) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
                digests.put(entry.getKey(), (String) entry.getValue());
            }
            Map<String, Object> rawWeights = asMap(deviceBinding.get("componentWeights"));
            for (Map.Entry<String, Object> entry : rawWeights.entrySet()) {
                weights.put(entry.getKey(), intField(rawWeights, entry.getKey()));
            }
            if (!digests.keySet().equals(weights.keySet())) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            for (String digest : digests.values()) {
                if (!digest.matches("[0-9a-fA-F]{64}")) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
            }
            return DeviceBinding.weighted(text(deviceBinding, "profileVersion"), digests, weights,
                    intField(deviceBinding, "minimumAvailableWeight"),
                    intField(deviceBinding, "maximumMismatchWeight"));
        }
        if ("DEVICE_KEY".equals(mode)) {
            requireExactKeys(deviceBinding, "mode", "spkiFingerprintHex");
            String hex = text(deviceBinding, "spkiFingerprintHex");
            // SPKI SHA-256 指纹恰 64 个十六进制字符；长度/字符集违规确定拒绝，不抛数组越界
            if (hex.length() != 64) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            byte[] fingerprint = new byte[32];
            for (int i = 0; i < 32; i++) {
                int high = Character.digit(hex.charAt(i * 2), 16);
                int low = Character.digit(hex.charAt(i * 2 + 1), 16);
                if (high < 0 || low < 0) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
                fingerprint[i] = (byte) ((high << 4) | low);
            }
            return DeviceBinding.deviceKey(fingerprint);
        }
        throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
    }

    private OrganizationPolicy wireOrganization() {
        Object rawRequired = organizationPolicy == null ? null : organizationPolicy.get("required");
        if (!(rawRequired instanceof Boolean)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        boolean required = (Boolean) rawRequired;
        if (!required) {
            // required=false 时不含其他属性（PROTOCOL.2 §3）
            requireExactKeys(organizationPolicy, "required");
            return OrganizationPolicy.notRequired();
        }
        requireExactKeys(organizationPolicy, "required", "maxObservationAgeSeconds", "membershipLeaseSeconds");
        return OrganizationPolicy.required(
                intField(organizationPolicy, "maxObservationAgeSeconds"),
                intField(organizationPolicy, "membershipLeaseSeconds"));
    }

    private RevocationPolicy wireRevocation() {
        String mode = text(revocationPolicy, "mode");
        if ("REQUIRED".equals(mode)) {
            requireExactKeys(revocationPolicy, "mode", "maxAgeSeconds");
            return RevocationPolicy.required(number(revocationPolicy, "maxAgeSeconds"));
        }
        if (!"OPTIONAL".equals(mode)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        // OPTIONAL 不含伪造的无限秒数
        requireExactKeys(revocationPolicy, "mode");
        return RevocationPolicy.optional();
    }

    private Lease wireLease() {
        requireExactKeys(lease, "leaseId", "refreshAfter", "leaseUntil", "outageUntil", "outageActions",
                "requesterKind", "requesterSourceId", "requesterSubjectId", "reservationReferences");
        SubjectRef requester = SubjectRef.of(SubjectKind.fromCode(text(lease, "requesterKind")),
                text(lease, "requesterSourceId"), text(lease, "requesterSubjectId"));
        List<Lease.ReservationReference> references = new ArrayList<Lease.ReservationReference>();
        Object raw = lease.get("reservationReferences");
        if (raw != null && !(raw instanceof List)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                Map<String, Object> map = asMap(item);
                requireExactKeys(map, "reservationId", "metric", "quantity", "fence", "validUntil");
                references.add(new Lease.ReservationReference(text(map, "reservationId"),
                        text(map, "metric"), number(map, "quantity"), number(map, "fence"),
                        number(map, "validUntil")));
            }
        }
        return Lease.of(text(lease, "leaseId"), number(lease, "refreshAfter"),
                number(lease, "leaseUntil"), number(lease, "outageUntil"),
                strings(lease.get("outageActions")), requester, references);
    }

    private List<NamedSeatReference> wireNamedSeats() {
        List<NamedSeatReference> seats = new ArrayList<NamedSeatReference>();
        if (namedSeatReferences != null) {
            for (Map<String, Object> seat : namedSeatReferences) {
                // 引用项恰为 assignmentId/assignmentGeneration/metric/quantity/retention，
                // FIXED 才另含 retainUntil；PERPETUAL 禁止伪造该字段。
                String retention = text(seat, "retention");
                if ("PERPETUAL".equals(retention)) {
                    requireExactKeys(seat, "assignmentId", "assignmentGeneration", "metric", "quantity",
                            "retention");
                } else if ("FIXED".equals(retention)) {
                    requireExactKeys(seat, "assignmentId", "assignmentGeneration", "metric", "quantity",
                            "retention", "retainUntil");
                } else {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
                if (number(seat, "quantity") != 1L) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
                NamedSeatReference reference = "PERPETUAL".equals(retention)
                        ? NamedSeatReference.perpetual(text(seat, "assignmentId"),
                        number(seat, "assignmentGeneration"), text(seat, "metric"))
                        : NamedSeatReference.fixed(text(seat, "assignmentId"),
                        number(seat, "assignmentGeneration"), text(seat, "metric"),
                        number(seat, "retainUntil"));
                seats.add(reference);
            }
        }
        return seats;
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        return (String) value;
    }

    /**
     * 联合结构"恰含"校验：嵌套策略对象的键集合必须精确等于该形态的白名单。
     * PROTOCOL.2 §2/§3 要求拒绝未知字段；多出的键（如 PERSON 绑定混入 activationId）
     * 一律确定拒绝，不静默忽略。
     *
     * @param map 待校验对象
     * @param allowedKeys 该形态允许出现的全部键；可 null 表示禁止任何键
     */
    private static void requireExactKeys(Map<String, Object> map, String... allowedKeys) {
        if (map == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        java.util.Set<String> allowed = new java.util.HashSet<String>(java.util.Arrays.asList(allowedKeys));
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
        }
    }

    /** 整型字段受检读取：拒绝 long→int 截断（如 2^32+120 必须越界拒绝而非静默变 120）。 */
    private static int intField(Map<String, Object> map, String key) {
        long value = number(map, key);
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        return (int) value;
    }

    private static long number(Map<String, Object> map, String key) {
        return ProtocolValueHelper.integer(map == null ? null : map.get(key));
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ProtocolValueHelper.integer(value);
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private static List<String> strings(Object value) {
        List<String> result = new ArrayList<String>();
        if (!(value instanceof List)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        java.util.Set<String> seen = new java.util.HashSet<String>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (!(item instanceof String) || ((String) item).isEmpty() || !seen.add((String) item)) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
                result.add((String) item);
            }
        }
        return result;
    }
}
