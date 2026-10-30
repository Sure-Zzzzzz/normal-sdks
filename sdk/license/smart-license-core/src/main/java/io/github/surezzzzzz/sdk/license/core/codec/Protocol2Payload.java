package io.github.surezzzzzz.sdk.license.core.codec;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.ClockPolicy;
import io.github.surezzzzzz.sdk.license.core.model.CredentialKind;
import io.github.surezzzzzz.sdk.license.core.model.DeviceBinding;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.Lease;
import io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference;
import io.github.surezzzzzz.sdk.license.core.model.OrganizationPolicy;
import io.github.surezzzzzz.sdk.license.core.model.RevocationPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectBinding;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.core.support.ProtocolValueHelper;
import java.util.Collections;
import java.util.List;

/**
 * 协议 2 完整凭据载荷（离线/混合）：全部策略字段类型化组装。
 *
 * <p>构造期完成全部字段约束校验（编号、时间关系、联合组合、预算）；未知顶层字段由
 * StrictJson 拒绝。纯 ONLINE 不构造此载荷。</p>
 *
 * @author surezzzzzz
 */
public final class Protocol2Payload {

    private final int protocolVersion;
    private final CredentialKind credentialKind;
    private final String jti;
    private final String issuerId;
    private final String productId;
    private final String customerId;
    private final String grantId;
    private final long grantRevision;
    private final long grantStateVersion;
    private final EligibilityPolicy eligibilityPolicy;
    private final SubjectBinding subjectBinding;
    private final long issuedAt;
    private final long notBefore;
    private final Long expiresAt;
    private final ValidityPolicy validity;
    private final ClockPolicy clockPolicy;
    private final DeviceBinding deviceBinding;
    private final OrganizationPolicy organizationPolicy;
    private final RevocationPolicy revocationPolicy;
    private final Lease lease;
    private final java.util.Map<String, Object> modelSnapshot;
    private final java.util.Map<String, Object> rights;
    private final List<NamedSeatReference> namedSeatReferences;
    private final List<String> requiredCapabilities;
    /** 额度政策快照，按协议保留定义/约束/窗口锚点。 */
    private final java.util.Map<String, Object> quotaPolicy;
    /** 当前部署的额度分配快照；人员/服务协议禁止携带分配项。 */
    private final List<java.util.Map<String, Object>> quotaAllocations;

    private Protocol2Payload(Builder builder) {
        this.protocolVersion = SmartLicenseCoreConstant.PROTOCOL_VERSION;
        this.credentialKind = builder.credentialKind;
        this.jti = builder.jti;
        this.issuerId = builder.issuerId;
        this.productId = builder.productId;
        this.customerId = builder.customerId;
        this.grantId = builder.grantId;
        this.grantRevision = builder.grantRevision;
        this.grantStateVersion = builder.grantStateVersion;
        this.eligibilityPolicy = builder.eligibilityPolicy;
        this.subjectBinding = builder.subjectBinding;
        this.issuedAt = builder.issuedAt;
        this.notBefore = builder.notBefore;
        this.expiresAt = builder.expiresAt;
        this.validity = builder.validity;
        this.clockPolicy = builder.clockPolicy;
        this.deviceBinding = builder.deviceBinding;
        this.organizationPolicy = builder.organizationPolicy;
        this.revocationPolicy = builder.revocationPolicy;
        this.lease = builder.lease;
        this.modelSnapshot = builder.modelSnapshot == null
                ? java.util.Collections.<String, Object>emptyMap()
                : ProtocolValueHelper.immutableObject(builder.modelSnapshot);
        this.rights = builder.rights == null
                ? java.util.Collections.<String, Object>emptyMap()
                : ProtocolValueHelper.immutableObject(builder.rights);
        this.namedSeatReferences = builder.namedSeatReferences == null
                ? Collections.<NamedSeatReference>emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<NamedSeatReference>(builder.namedSeatReferences));
        this.requiredCapabilities = builder.requiredCapabilities == null
                ? Collections.<String>emptyList()
                : ProtocolValueHelper.immutableList(builder.requiredCapabilities);
        this.quotaPolicy = builder.quotaPolicy == null
                ? java.util.Collections.<String, Object>emptyMap()
                : ProtocolValueHelper.immutableObject(builder.quotaPolicy);
        this.quotaAllocations = builder.quotaAllocations == null
                ? Collections.<java.util.Map<String, Object>>emptyList()
                : ProtocolValueHelper.immutableList(builder.quotaAllocations);
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        Protocol2Credential.validateId(jti, "jti");
        Protocol2Credential.validateId(issuerId, "iss");
        Protocol2Credential.validateId(productId, "aud");
        Protocol2Credential.validateId(customerId, "customerId");
        Protocol2Credential.validateId(grantId, "grantId");
        Protocol2Credential.validateEpoch(issuedAt, "iat");
        Protocol2Credential.validateEpoch(notBefore, "nbf");
        if (credentialKind == null || grantRevision < 1 || grantStateVersion < 1
                || grantRevision > ProtocolValueHelper.SAFE_INTEGER_MAX
                || grantStateVersion > ProtocolValueHelper.SAFE_INTEGER_MAX) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (eligibilityPolicy == null || subjectBinding == null || validity == null || clockPolicy == null
                || deviceBinding == null || organizationPolicy == null || revocationPolicy == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        validateBindingPolicy();
        Protocol2Credential.validateEpoch(validity.getStartsAt(), "startsAt");
        if (validity.getServiceUntil() != null) {
            Protocol2Credential.validateEpoch(validity.getServiceUntil(), "serviceUntil");
            Protocol2Credential.validateEpoch(validity.getCommercialGraceUntil(), "commercialGraceUntil");
        }
        if (validity.getMaintenanceUntil() != null) {
            Protocol2Credential.validateEpoch(validity.getMaintenanceUntil(), "maintenanceUntil");
            if (validity.getMaintenanceUntil() < validity.getStartsAt()) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
            }
        }
        if (expiresAt != null) {
            Protocol2Credential.validateEpoch(expiresAt, "exp");
            if (issuedAt >= expiresAt) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
            }
        }
        if (!modelSnapshot.isEmpty()) {
            requirePositive(modelSnapshot.get("version"));
        }
        // 联合约束：subjectBinding kind 与 eligibilityPolicy 匹配。
        // 部署三种设备档位都合法（NONE 是模型显式声明的"无绑定"产品形态，POLICIES §4）；
        // 人员/服务只能 NONE，其本地真实身份由独立可信身份能力验证。
        if (subjectBinding.getKind() == io.github.surezzzzzz.sdk.license.core.model.SubjectKind.DEPLOYMENT) {
            if (!namedSeatReferences.isEmpty()) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
            }
        } else {
            if (deviceBinding.getMode() != DeviceBinding.Mode.NONE) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
            }
        }
        // OFFLINE 的 nbf=startsAt、exp=commercialGraceUntil（FIXED）或省略（PERPETUAL）
        if (credentialKind == CredentialKind.OFFLINE) {
            if (lease != null) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            if (validity.getKind() == ValidityPolicy.Kind.FIXED) {
                if (expiresAt == null
                        || expiresAt.longValue() != validity.getCommercialGraceUntil().longValue()) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
                }
            } else if (expiresAt != null) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
            }
        }
        // HYBRID 必须有 lease 且 exp=outageUntil
        if (credentialKind == CredentialKind.HYBRID) {
            if (lease == null) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            if (expiresAt == null || expiresAt.longValue() != lease.getOutageUntil()) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
            }
            if (validity.getKind() == ValidityPolicy.Kind.FIXED
                    && lease.getOutageUntil() > validity.getCommercialGraceUntil()) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
            }
        }
        // 必需能力集合：无重复、每项受控 ASCII 且长度 1..128（PROTOCOL.2 §3）
        if (requiredCapabilities.size() > SmartLicenseCoreConstant.CAPABILITIES_MAX) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        validateQuotaPolicy();
        validateQuotaAllocations();
        java.util.Set<String> capabilitySeen = new java.util.HashSet<String>();
        for (String capability : requiredCapabilities) {
            if (capability == null || capability.isEmpty()
                    || capability.length() > SmartLicenseCoreConstant.CAPABILITY_MAX_LENGTH
                    || !isControlledAscii(capability)
                    || !capabilitySeen.add(capability)) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
        }
        if (namedSeatReferences.size() > SmartLicenseCoreConstant.NAMED_SEAT_REFERENCES_MAX) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        // 实名引用指标不得重复（PROTOCOL.2 §3"重复指标拒绝"）
        java.util.Set<String> seatMetrics = new java.util.HashSet<String>();
        for (io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference seat : namedSeatReferences) {
            if (seat == null || !seatMetrics.add(seat.getMetric())
                    || !subjectBinding.assignmentId().isPresent()
                    || !subjectBinding.assignmentId().get().equals(seat.getAssignmentId())
                    || !subjectBinding.assignmentGeneration().isPresent()
                    || subjectBinding.assignmentGeneration().getAsLong() != seat.getAssignmentGeneration()) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            if (seat.getRetainUntil().isPresent()
                    && (expiresAt == null || seat.getRetainUntil().getAsLong() < expiresAt)) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
            }
        }
        // 时间关系链（PROTOCOL.2 §3）：nbf 不早于 startsAt；HYBRID 的 nbf=max(iat,startsAt)。
        // OFFLINE 的 nbf=startsAt 且 iat 允许晚于 startsAt（实际签发时刻），故 OFFLINE 不约束 iat<=nbf。
        if (notBefore < validity.getStartsAt()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
        }
        if (credentialKind == CredentialKind.OFFLINE && notBefore != validity.getStartsAt()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
        }
        // HYBRID 租约链（PROTOCOL.2 §3/§4）：nbf==max(iat,startsAt)、iat<leaseUntil、nbf<leaseUntil、
        // refreshAfter>=iat；有限授权 outageUntil<=commercialGraceUntil
        if (credentialKind == CredentialKind.HYBRID && lease != null) {
            long hybridNbf = Math.max(issuedAt, validity.getStartsAt());
            if (notBefore != hybridNbf
                    || issuedAt >= lease.getLeaseUntil() || notBefore >= lease.getLeaseUntil()
                    || lease.getRefreshAfter() < issuedAt
                    || lease.getLeaseUntil() > lease.getOutageUntil()) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
            }
            if (lease.getReservationReferences().size() > SmartLicenseCoreConstant.RESERVATION_REFERENCES_MAX) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            Protocol2Credential.validateEpoch(lease.getRefreshAfter(), "refreshAfter");
            Protocol2Credential.validateEpoch(lease.getLeaseUntil(), "leaseUntil");
            Protocol2Credential.validateEpoch(lease.getOutageUntil(), "outageUntil");
            java.util.Set<String> reservations = new java.util.HashSet<String>();
            for (Lease.ReservationReference reference : lease.getReservationReferences()) {
                if (reference == null || !reservations.add(reference.getReservationId())
                        || reference.getQuantity() < 1 || reference.getFence() < 1
                        || reference.getQuantity() > ProtocolValueHelper.SAFE_INTEGER_MAX
                        || reference.getFence() > ProtocolValueHelper.SAFE_INTEGER_MAX
                        || reference.getValidUntil() < lease.getOutageUntil()) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
                Protocol2Credential.validateId(reference.getReservationId(), "reservationId");
                Protocol2Credential.validateEpoch(reference.getValidUntil(), "validUntil");
                validateName(reference.getMetric(), SmartLicenseCoreConstant.FIELD_NAME_MAX_LENGTH);
                java.util.Map<String, Object> definitions = quotaObject(quotaPolicy.get("definitions"));
                java.util.Map<String, Object> definition = quotaObject(definitions.get(reference.getMetric()));
                if (!"CONCURRENT".equals(definition.get("kind"))
                        || !"ONLINE".equals(definition.get("enforcement"))) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
            }
        }
    }

    /** 受控 ASCII：仅字母/数字/点/连字符/下划线/斜杠（能力编号形态 license-condition/1 等）。 */
    private static boolean isControlledAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_' || c == '/';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** 额度政策若存在必须是完整的三段快照，避免把任意 JSON 当作协议能力。 */
    private void validateQuotaPolicy() {
        if (quotaPolicy.isEmpty()) {
            if (!quotaAllocations.isEmpty() || !namedSeatReferences.isEmpty()) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            return;
        }
        java.util.Set<String> allowed = new java.util.HashSet<String>(java.util.Arrays.asList(
                "definitions", "constraints", "windowAnchors"));
        if (!quotaPolicy.keySet().equals(allowed)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        for (String key : allowed) {
            Object value = quotaPolicy.get(key);
            if (!(value instanceof java.util.Map)) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
        }
        java.util.Map<String, Object> definitions = quotaObject(quotaPolicy.get("definitions"));
        java.util.Map<String, Object> constraints = quotaObject(quotaPolicy.get("constraints"));
        java.util.Map<String, Object> anchors = quotaObject(quotaPolicy.get("windowAnchors"));
        if (definitions.size() > SmartLicenseCoreConstant.QUOTA_ALLOCATIONS_MAX) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        java.util.Set<String> expectedAnchors = new java.util.HashSet<String>();
        for (java.util.Map.Entry<String, Object> entry : definitions.entrySet()) {
            String metric = entry.getKey();
            validateName(metric, SmartLicenseCoreConstant.FIELD_NAME_MAX_LENGTH);
            java.util.Map<String, Object> definition = quotaObject(entry.getValue());
            java.util.Set<String> keys = new java.util.HashSet<String>(java.util.Arrays.asList(
                    "limitRight", "kind", "enforcement"));
            String kind = quotaText(definition.get("kind"));
            String enforcement = quotaText(definition.get("enforcement"));
            String limitRight = quotaText(definition.get("limitRight"));
            validateName(limitRight, SmartLicenseCoreConstant.FIELD_NAME_MAX_LENGTH);
            if (!("ONLINE".equals(enforcement) || "LOCAL".equals(enforcement))) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            if ("LOCAL".equals(enforcement) && subjectBinding.getKind()
                    != io.github.surezzzzzz.sdk.license.core.model.SubjectKind.DEPLOYMENT) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
            }
            if ("NAMED_SEAT".equals(kind)) {
                if (!"ONLINE".equals(enforcement)
                        || eligibilityPolicy.getKind() != EligibilityPolicy.Kind.ASSIGNED_SUBJECTS) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
            } else if ("CONCURRENT".equals(kind)) {
                if ("ONLINE".equals(enforcement)) {
                    keys.add("maxReservationSeconds");
                    requirePositive(definition.get("maxReservationSeconds"));
                }
            } else if ("CONSUMPTION".equals(kind)) {
                keys.add("window");
                String window = quotaText(definition.get("window"));
                if ("FIXED_WINDOW".equals(window)) {
                    keys.add("periodSeconds");
                    requirePositive(definition.get("periodSeconds"));
                    expectedAnchors.add(metric);
                } else if (!"TOTAL".equals(window)) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
            } else {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            if (!keys.equals(definition.keySet()) || ProtocolValueHelper.integer(rights.get(limitRight)) < 0) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            if (!modelSnapshot.isEmpty()) {
                java.util.Map<String, Object> fields = quotaObject(modelSnapshot.get("fields"));
                java.util.Map<String, Object> modelRights = quotaObject(fields.get("rights"));
                if (!"INTEGER".equals(quotaObject(modelRights.get(limitRight)).get("type"))) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
            }
        }
        if (!anchors.keySet().equals(expectedAnchors)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        for (Object anchor : anchors.values()) {
            Protocol2Credential.validateEpoch(ProtocolValueHelper.integer(anchor), "windowAnchorAt");
        }
        java.util.Map<String, Object> actions = modelSnapshot.isEmpty()
                ? java.util.Collections.<String, Object>emptyMap() : quotaObject(modelSnapshot.get("actions"));
        for (java.util.Map.Entry<String, Object> constraint : constraints.entrySet()) {
            validateName(constraint.getKey(), SmartLicenseCoreConstant.ACTION_NAME_MAX_LENGTH);
            if (!actions.containsKey(constraint.getKey()) || !(constraint.getValue() instanceof List)) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (Object metric : (List<?>) constraint.getValue()) {
                if (!(metric instanceof String) || !definitions.containsKey(metric) || !seen.add((String) metric)) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
            }
        }
        for (NamedSeatReference seat : namedSeatReferences) {
            if (seat == null || !definitions.containsKey(seat.getMetric())
                    || !"NAMED_SEAT".equals(quotaObject(definitions.get(seat.getMetric())).get("kind"))) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
        }
    }

    /** 校验对象、资格与组织政策的交集，不能只校验各个对象自身字段。 */
    private void validateBindingPolicy() {
        io.github.surezzzzzz.sdk.license.core.model.SubjectKind kind = subjectBinding.getKind();
        EligibilityPolicy.Kind eligibility = eligibilityPolicy.getKind();
        if (kind == io.github.surezzzzzz.sdk.license.core.model.SubjectKind.DEPLOYMENT) {
            if (eligibility != EligibilityPolicy.Kind.ALLOCATED_DEPLOYMENTS || organizationPolicy.isRequired()
                    || subjectBinding.activationGeneration() > ProtocolValueHelper.SAFE_INTEGER_MAX) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
            }
            return;
        }
        if (eligibility == EligibilityPolicy.Kind.ALLOCATED_DEPLOYMENTS
                || kind == io.github.surezzzzzz.sdk.license.core.model.SubjectKind.SERVICE
                && (eligibility != EligibilityPolicy.Kind.ASSIGNED_SUBJECTS || organizationPolicy.isRequired())
                || eligibility == EligibilityPolicy.Kind.CUSTOMER_MEMBERS
                && (credentialKind == CredentialKind.OFFLINE || !organizationPolicy.isRequired())) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
        }
        if (eligibility == EligibilityPolicy.Kind.ASSIGNED_SUBJECTS) {
            String profile = eligibilityPolicy.getOfflineProofProfile();
            if (profile == null || profile.isEmpty() || profile.length() > 96 || !isControlledAscii(profile)
                    || !subjectBinding.assignmentId().isPresent()
                    || !subjectBinding.assignmentGeneration().isPresent()
                    || subjectBinding.assignmentGeneration().getAsLong() > ProtocolValueHelper.SAFE_INTEGER_MAX) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
            }
        } else if (subjectBinding.assignmentId().isPresent()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
        }
        if (credentialKind == CredentialKind.OFFLINE && organizationPolicy.isRequired()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
        }
    }

    private static void validateName(String name, int maximum) {
        if (name == null || name.isEmpty() || name.length() > maximum || !isControlledAscii(name)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> quotaObject(Object value) {
        if (!(value instanceof java.util.Map)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        return (java.util.Map<String, Object>) value;
    }

    private static String quotaText(Object value) {
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        return (String) value;
    }

    private static long requirePositive(Object value) {
        long number = ProtocolValueHelper.integer(value);
        if (number < 1) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        return number;
    }

    /** 模型快照版本（Decision.modelVersion 的正确语义；快照缺 version 字段时为 0 表示未知）。 */
    public long getModelSnapshotVersion() {
        Object version = modelSnapshot == null ? null : modelSnapshot.get("version");
        return version instanceof Number ? ((Number) version).longValue() : 0L;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public CredentialKind getCredentialKind() {
        return credentialKind;
    }

    public String getJti() {
        return jti;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public String getProductId() {
        return productId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getGrantId() {
        return grantId;
    }

    public long getGrantRevision() {
        return grantRevision;
    }

    public long getGrantStateVersion() {
        return grantStateVersion;
    }

    public EligibilityPolicy getEligibilityPolicy() {
        return eligibilityPolicy;
    }

    public SubjectBinding getSubjectBinding() {
        return subjectBinding;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public long getNotBefore() {
        return notBefore;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public ValidityPolicy getValidity() {
        return validity;
    }

    public ClockPolicy getClockPolicy() {
        return clockPolicy;
    }

    public DeviceBinding getDeviceBinding() {
        return deviceBinding;
    }

    public OrganizationPolicy getOrganizationPolicy() {
        return organizationPolicy;
    }

    public RevocationPolicy getRevocationPolicy() {
        return revocationPolicy;
    }

    public Lease getLease() {
        return lease;
    }

    /** 模型快照（fields/actions 原始 JSON 树）。 */
    public java.util.Map<String, Object> getModelSnapshot() {
        return modelSnapshot;
    }

    /** 展开的权益值。 */
    public java.util.Map<String, Object> getRights() {
        return rights;
    }

    public List<NamedSeatReference> getNamedSeatReferences() {
        return namedSeatReferences;
    }

    public List<String> getRequiredCapabilities() {
        return requiredCapabilities;
    }

    public java.util.Map<String, Object> getQuotaPolicy() {
        return quotaPolicy;
    }

    public List<java.util.Map<String, Object>> getQuotaAllocations() {
        return quotaAllocations;
    }

    /** Builder。 */
    public static final class Builder {

        private CredentialKind credentialKind;
        private String jti;
        private String issuerId;
        private String productId;
        private String customerId;
        private String grantId;
        private long grantRevision;
        private long grantStateVersion;
        private EligibilityPolicy eligibilityPolicy;
        private SubjectBinding subjectBinding;
        private long issuedAt;
        private long notBefore;
        private Long expiresAt;
        private ValidityPolicy validity;
        private ClockPolicy clockPolicy;
        private DeviceBinding deviceBinding;
        private OrganizationPolicy organizationPolicy;
        private RevocationPolicy revocationPolicy;
        private Lease lease;
        private java.util.Map<String, Object> modelSnapshot;
        private java.util.Map<String, Object> rights;
        private List<NamedSeatReference> namedSeatReferences;
        private List<String> requiredCapabilities;
        private java.util.Map<String, Object> quotaPolicy;
        private List<java.util.Map<String, Object>> quotaAllocations;

        public Builder credentialKind(CredentialKind kind) {
            this.credentialKind = kind;
            return this;
        }

        public Builder jti(String value) {
            this.jti = value;
            return this;
        }

        public Builder issuerId(String value) {
            this.issuerId = value;
            return this;
        }

        public Builder productId(String value) {
            this.productId = value;
            return this;
        }

        public Builder customerId(String value) {
            this.customerId = value;
            return this;
        }

        public Builder grantId(String value) {
            this.grantId = value;
            return this;
        }

        public Builder grantRevision(long value) {
            this.grantRevision = value;
            return this;
        }

        public Builder grantStateVersion(long value) {
            this.grantStateVersion = value;
            return this;
        }

        public Builder eligibilityPolicy(EligibilityPolicy value) {
            this.eligibilityPolicy = value;
            return this;
        }

        public Builder subjectBinding(SubjectBinding value) {
            this.subjectBinding = value;
            return this;
        }

        public Builder issuedAt(long value) {
            this.issuedAt = value;
            return this;
        }

        public Builder notBefore(long value) {
            this.notBefore = value;
            return this;
        }

        public Builder expiresAt(Long value) {
            this.expiresAt = value;
            return this;
        }

        public Builder validity(ValidityPolicy value) {
            this.validity = value;
            return this;
        }

        public Builder clockPolicy(ClockPolicy value) {
            this.clockPolicy = value;
            return this;
        }

        public Builder deviceBinding(DeviceBinding value) {
            this.deviceBinding = value;
            return this;
        }

        public Builder organizationPolicy(OrganizationPolicy value) {
            this.organizationPolicy = value;
            return this;
        }

        public Builder revocationPolicy(RevocationPolicy value) {
            this.revocationPolicy = value;
            return this;
        }

        public Builder lease(Lease value) {
            this.lease = value;
            return this;
        }

        public Builder modelSnapshot(java.util.Map<String, Object> value) {
            this.modelSnapshot = value;
            return this;
        }

        public Builder rights(java.util.Map<String, Object> value) {
            this.rights = value;
            return this;
        }

        public Builder namedSeatReferences(List<NamedSeatReference> value) {
            this.namedSeatReferences = value;
            return this;
        }

        public Builder requiredCapabilities(List<String> value) {
            this.requiredCapabilities = value;
            return this;
        }

        public Builder quotaPolicy(java.util.Map<String, Object> value) {
            this.quotaPolicy = value;
            return this;
        }

        public Builder quotaAllocations(List<java.util.Map<String, Object>> value) {
            this.quotaAllocations = value;
            return this;
        }

        public Protocol2Payload build() {
            return new Protocol2Payload(this);
        }
    }

    /** 额度分配是签发时冻结的事实；只允许部署对象携带，且每项字段集合固定。 */
    private void validateQuotaAllocations() {
        if (quotaAllocations.size() > SmartLicenseCoreConstant.QUOTA_ALLOCATIONS_MAX) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (subjectBinding.getKind() != io.github.surezzzzzz.sdk.license.core.model.SubjectKind.DEPLOYMENT
                && !quotaAllocations.isEmpty()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
        }
        java.util.Set<String> seen = new java.util.HashSet<String>();
        java.util.Set<String> allocationIds = new java.util.HashSet<String>();
        for (java.util.Map<String, Object> allocation : quotaAllocations) {
            if (allocation == null) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            java.util.Set<String> allowed = new java.util.HashSet<String>(java.util.Arrays.asList(
                    "allocationId", "metric", "quantity", "grantRevision", "retention", "retainUntil"));
            for (String key : allocation.keySet()) {
                if (!allowed.contains(key)) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
            }
            Object allocationId = allocation.get("allocationId");
            Object metric = allocation.get("metric");
            Object quantity = allocation.get("quantity");
            Object revision = allocation.get("grantRevision");
            Object retention = allocation.get("retention");
            if (!(allocationId instanceof String) || ((String) allocationId).isEmpty()
                    || !(metric instanceof String) || ((String) metric).isEmpty()
                    || requirePositive(quantity) <= 0L || requirePositive(revision) != grantRevision
                    || !(retention instanceof String)
                    || !("FIXED".equals(retention) || "PERPETUAL".equals(retention))
                    || !seen.add((String) metric) || !allocationIds.add((String) allocationId)) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            Protocol2Credential.validateId((String) allocationId, "allocationId");
            java.util.Map<String, Object> definitions = quotaObject(quotaPolicy.get("definitions"));
            java.util.Map<String, Object> definition = quotaObject(definitions.get(metric));
            if (!"LOCAL".equals(definition.get("enforcement"))) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            if (requirePositive(quantity) > ProtocolValueHelper.integer(rights.get(definition.get("limitRight")))) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            if ("FIXED".equals(retention)) {
                Object retainUntil = allocation.get("retainUntil");
                long until = requirePositive(retainUntil);
                Protocol2Credential.validateEpoch(until, "retainUntil");
                if (expiresAt == null || until < expiresAt) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
                }
            } else if (allocation.containsKey("retainUntil")) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
        }
        if (!quotaPolicy.isEmpty()) {
            java.util.Map<String, Object> definitions = quotaObject(quotaPolicy.get("definitions"));
            for (java.util.Map.Entry<String, Object> entry : definitions.entrySet()) {
                if ("LOCAL".equals(quotaObject(entry.getValue()).get("enforcement"))
                        && !seen.contains(entry.getKey())) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
            }
        }
    }
}
