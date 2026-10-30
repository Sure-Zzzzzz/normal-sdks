package io.github.surezzzzzz.sdk.license.core.verify;

import io.github.surezzzzzz.sdk.license.core.codec.CompactJws;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload;
import io.github.surezzzzzz.sdk.license.core.codec.ProtocolHeader;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.ConditionEvaluator;
import io.github.surezzzzzz.sdk.license.core.model.CredentialKind;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.FactValues;
import io.github.surezzzzzz.sdk.license.core.model.SubjectBinding;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.SubjectRef;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import io.github.surezzzzzz.sdk.license.core.model.VerifiedCredential;
import io.github.surezzzzzz.sdk.license.core.spi.ClockProvider;
import io.github.surezzzzzz.sdk.license.core.spi.StateStore;
import io.github.surezzzzzz.sdk.license.core.spi.TrustStore;
import io.github.surezzzzzz.sdk.license.core.spi.TrustedSubjectProvider;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Verifier 核心流程：Compact JWS → 验签 → 结构/绑定/吊销/时间/阶段 → 规则求值（协议 §6 十二步骨架）。
 *
 * <p>设备分量匹配与人员本地身份核验由宿主通过端口先行完成并经 {@link FileVerificationInputs}
 * 提供；本类不采集设备证据。基础无效优先于商业宽限；已知否定优先于网络宽限。</p>
 *
 * @author surezzzzzz
 */
public final class DefaultLicenseVerifier {

    private final TrustStore trustStore;
    private final RevocationStore revocationStore;
    private final ClockProvider clock;
    private final StateStore stateStore;
    /** 宿主实际安装的离线身份档案；凭据声明本身不能反向注册能力。 */
    private final Set<String> supportedOfflineProofProfiles;
    /** 受控离线身份提供方；人员/服务授权不能只依赖请求输入中的主体引用。 */
    private final TrustedSubjectProvider trustedSubjectProvider;
    /** 句柄必须由当前 verifier 实例签发；公开模型工厂不能伪造受控安装上下文。 */
    private final Set<VerifiedCredential> issuedHandles = Collections.newSetFromMap(
            new WeakHashMap<VerifiedCredential, Boolean>());

    public DefaultLicenseVerifier(TrustStore trustStore, RevocationStore revocationStore,
                                  ClockProvider clock, StateStore stateStore) {
        this(trustStore, revocationStore, clock, stateStore, Collections.<String>emptySet(), null);
    }

    /**
     * 构造带受控离线身份能力注册的验证器。
     *
     * <p>profile 是宿主安装的可信能力版本，不是待验凭据可自报的能力；未注册 profile
     * 即使凭据同时声明同名 requiredCapabilities 也必须拒绝。</p>
     */
    public DefaultLicenseVerifier(TrustStore trustStore, RevocationStore revocationStore,
                                  ClockProvider clock, StateStore stateStore,
                                  Set<String> supportedOfflineProofProfiles) {
        this(trustStore, revocationStore, clock, stateStore, supportedOfflineProofProfiles, null);
    }

    /**
     * 构造带受控离线身份能力和当前身份提供方的验证器。
     *
     * <p>提供方缺失、profile 不匹配、证明过期或提供方异常均按能力缺失处理；
     * 这条边界只在人员/服务 authorize 时执行，纯在线宿主仍可装配同一核心。</p>
     */
    public DefaultLicenseVerifier(TrustStore trustStore, RevocationStore revocationStore,
                                  ClockProvider clock, StateStore stateStore,
                                  Set<String> supportedOfflineProofProfiles,
                                  TrustedSubjectProvider trustedSubjectProvider) {
        this.trustStore = trustStore;
        this.revocationStore = revocationStore;
        this.clock = clock;
        this.stateStore = stateStore;
        this.trustedSubjectProvider = trustedSubjectProvider;
        if (supportedOfflineProofProfiles == null) {
            throw new IllegalArgumentException("离线身份能力注册不能为空");
        }
        Set<String> profiles = new HashSet<String>();
        for (String profile : supportedOfflineProofProfiles) {
            if (profile == null || profile.isEmpty()) {
                throw new IllegalArgumentException("离线身份能力档案不能为空");
            }
            profiles.add(profile);
        }
        this.supportedOfflineProofProfiles = Collections.unmodifiableSet(profiles);
    }

    /**
     * 文件验证入口：三段解析、选钥验签、结构装载、目标绑定核对、吊销与新鲜度、时间阶段。
     *
     * @return 已验证句柄（后续 authorize 每次重查）
     */
    public VerifiedCredential verify(byte[] credentialBytes, VerificationContext context) {
        if (credentialBytes == null || context == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        // 1-2. 三段与预算
        String compactJws = new String(credentialBytes, StandardCharsets.UTF_8);
        CompactJws jws = CompactJws.parse(compactJws);
        byte[] headerBytes = io.github.surezzzzzz.sdk.license.core.codec.Base64Url.decode(jws.getHeaderSegment());
        if (headerBytes.length > SmartLicenseCoreConstant.HEADER_MAX_BYTES) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_ENCODING, ErrorMessage.PROTOCOL_ENCODING_INVALID);
        }
        // 3. header 恰三字段
        ProtocolHeader header = StrictJson.parse(new String(headerBytes, StandardCharsets.UTF_8),
                ProtocolHeader.class);
        // 4-5. 预定信任域选钥 + 原文验签（typ 用途与档案一致）
        TrustedPublicKey.Purpose purpose = header.getTyp().equals(ProtocolHeader.TYP_CREDENTIAL)
                ? TrustedPublicKey.Purpose.CREDENTIAL
                : header.getTyp().equals(ProtocolHeader.TYP_LEASE)
                ? TrustedPublicKey.Purpose.CREDENTIAL
                : null;
        if (purpose == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        Optional<TrustedPublicKey> trusted = trustStore.findKey(
                context.getIssuerId(), context.getProductId(), header.getKid(), purpose);
        if (!trusted.isPresent()) {
            throw new LicenseCoreException(ErrorCode.CRYPTO_SIGNATURE, ErrorMessage.CRYPTO_SIGNATURE_INVALID);
        }
        long trustFloor = context.getInstalledTrustVersionFloor();
        if (trusted.get().getTrustVersion() < trustFloor
                || trustStore.currentTrustVersion(context.getIssuerId(), context.getProductId(), purpose) < trustFloor) {
            throw new LicenseCoreException(ErrorCode.CRYPTO_SIGNATURE, ErrorMessage.CRYPTO_SIGNATURE_INVALID);
        }
        if (!Es256Primitives.verify(jws.signingInput(), jws.signatureBytes(),
                Es256Primitives.parseSpkiPublicKey(trusted.get().getSpki()))) {
            throw new LicenseCoreException(ErrorCode.CRYPTO_SIGNATURE, ErrorMessage.CRYPTO_SIGNATURE_INVALID);
        }
        // 6. payload 结构装载（全部联合约束构造期校验）
        byte[] payloadBytes = io.github.surezzzzzz.sdk.license.core.codec.Base64Url.decode(jws.getPayloadSegment());
        io.github.surezzzzzz.sdk.license.core.codec.Protocol2PayloadWire wire =
                StrictJson.parse(new String(payloadBytes, StandardCharsets.UTF_8),
                        io.github.surezzzzzz.sdk.license.core.codec.Protocol2PayloadWire.class);
        Protocol2Payload payload = wire.toPayload();
        validateCapabilities(payload);
        // 未来签发拒绝：iat 超出本机时间+skew 容差的凭据不进入后续校验（skew 只作用于签发时刻误差，
        // 不对商业/租约截止加秒——PROTOCOL.2 §3/POLICIES §3）
        long effectiveNow = effectiveNow(payload);
        if (payload.getIssuedAt() > addSeconds(effectiveNow, payload.getClockPolicy().getSkewSeconds())) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
        }
        // typ 用途与载荷形态交叉核验（PROTOCOL.2 §2"期待的种类由调用接口确定"）：
        // license+jwt 恰为 OFFLINE 凭据（无租约）；license-lease+jwt 恰为 HYBRID 租约（有租约）
        if (ProtocolHeader.TYP_CREDENTIAL.equals(header.getTyp())
                != (payload.getCredentialKind() == CredentialKind.OFFLINE && payload.getLease() == null)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (ProtocolHeader.TYP_LEASE.equals(header.getTyp())
                != (payload.getCredentialKind() == CredentialKind.HYBRID && payload.getLease() != null)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        // 7. 目标绑定核对：iss/aud/customer/grant/subjectBinding 与受控上下文一致
        if (!payload.getIssuerId().equals(context.getIssuerId())
                || !payload.getProductId().equals(context.getProductId())
                || !payload.getCustomerId().equals(context.getCustomerId())
                || !payload.getGrantId().equals(context.getGrantId())
                || !bindingMatches(payload.getSubjectBinding(), context.getExpectedBinding())) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_BINDING, ErrorMessage.PROTOCOL_BINDING_INVALID);
        }
        // 8. 五类已知不可撤回吊销；签名钥按业务 kid 阻断其历史文件。
        if (revocationStore.isRevoked(payload.getIssuerId(), payload.getProductId(), "SIGNING_KEY", header.getKid())
                || revokedNow(payload)) {
            throw new LicenseCoreException(ErrorCode.CREDENTIAL_REVOKED, ErrorMessage.CREDENTIAL_REVOKED);
        }
        // 9. 吊销新鲜度（REQUIRED 模式）
        if (payload.getRevocationPolicy().getMode()
                .equals(io.github.surezzzzzz.sdk.license.core.model.RevocationPolicy.Mode.REQUIRED)) {
            Optional<Long> issuedAt = revocationStore.installedIssuedAt(
                    payload.getIssuerId(), payload.getProductId());
            boolean fresh = issuedAt.isPresent() && issuedAt.get() <= addSeconds(effectiveNow,
                    payload.getClockPolicy().getSkewSeconds())
                    && addSeconds(issuedAt.get(), payload.getRevocationPolicy().getMaxAgeSeconds()) > effectiveNow;
            if (!fresh) {
                throw new LicenseCoreException(ErrorCode.REVOCATION_STALE,
                        ErrorMessage.REVOCATION_STALE);
            }
        }
        // 10. 可信时间：CLOCK_SUSPECT 与 nbf/exp 边界（左闭右开）
        long now = effectiveNow;
        if (clock.isClockSuspect() || now < payload.getNotBefore()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
        }
        if (payload.getExpiresAt() != null && now >= payload.getExpiresAt()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
        }
        // 11. 推进 T_max（仅完整通过基础校验的使用事实推进水位）
        String stateKey = stateStore.buildStateKey(payload.getIssuerId(), payload.getProductId(),
                payload.getCustomerId(), payload.getGrantId(), stableObjectKey(payload));
        stateStore.persistMaxTrustedTime(stateKey, now);
        // 12. 交付句柄
        VerifiedCredential handle = VerifiedCredential.issued(payload, credentialBytes, context);
        synchronized (issuedHandles) {
            issuedHandles.add(handle);
        }
        return handle;
    }

    /** 仅允许当前 verifier 签发的句柄进入重验证；公开工厂句柄不能升级为可信句柄。 */
    public boolean isIssued(VerifiedCredential handle) {
        if (handle == null) {
            return false;
        }
        synchronized (issuedHandles) {
            return issuedHandles.contains(handle);
        }
    }

    /**
     * 文件 authorize：阶段动作集与规则求值（每次重查，不复用导入结论）。
     *
     * @param action 目标动作
     * @param inputs 设备分量匹配结果/人员身份核验结果（由宿主端口先行完成）
     * @param facts 本次可信运行事实
     */
    public Decision authorize(VerifiedCredential handle, String action,
                              FileVerificationInputs inputs, TrustedFacts facts) {
        if (handle == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        synchronized (issuedHandles) {
            if (!issuedHandles.contains(handle)) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
        }
        Protocol2Payload payload = handle.getPayload();
        long now = clock.currentEffectiveNow();
        // 每次判定复查当前吊销：verify 之后新发生的吊销必须即时阻断（不复用导入时结论）
        if (revokedNow(payload)) {
            return Decision.deny(action, payload.getModelSnapshotVersion(),
                    payload.getValidity().commercialPhaseAt(now), connectivity(payload, now), now,
                    Decision.ReasonCategory.REVOKED);
        }
        // 句柄不是长期安全证明：当前信任、原文验签、钥吊销、资料新鲜度和基础时间逐次复查。
        try {
            payload = handle.revalidate(this).getPayload();
        } catch (LicenseCoreException invalid) {
            if (invalid.getErrorCode() != ErrorCode.CREDENTIAL_REVOKED) {
                throw invalid;
            }
            return Decision.deny(action, payload.getModelSnapshotVersion(),
                    payload.getValidity().commercialPhaseAt(now), connectivity(payload, now), now,
                    Decision.ReasonCategory.REVOKED);
        }
        now = effectiveNow(payload);
        // 商业阶段
        Decision.CommercialPhase commercial = payload.getValidity().commercialPhaseAt(now);
        if (commercial == Decision.CommercialPhase.NOT_STARTED
                || commercial == Decision.CommercialPhase.ENDED) {
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial,
                    connectivity(payload, now), now, Decision.ReasonCategory.COMMERCIAL_PHASE);
        }
        // 连接阶段（HYBRID）
        Decision.ConnectivityPhase connectivity = connectivity(payload, now);
        if (connectivity == Decision.ConnectivityPhase.ENDED) {
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                    Decision.ReasonCategory.CONNECTIVITY_PHASE);
        }
        // 阶段动作白名单交集
        if (!actionAllowedInPhase(action, payload, commercial, connectivity)) {
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                    Decision.ReasonCategory.COMMERCIAL_PHASE);
        }
        if (payload.getValidity().getMaintenanceUntil() != null) {
            // 维护动作白名单尚未进入类型化协议，不能把有维护截止的文件当作全部动作无限可用。
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                    Decision.ReasonCategory.CAPABILITY_MISSING);
        }
        // 设备/人员核验结果（宿主已完成；未通过即拒）
        if (inputs == null || !inputs.isSubjectAndDeviceAcceptable()) {
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                    Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        }
        // 人员/服务离线身份必须由受控 profile 提供真实 SubjectRef；布尔通过标记不能冒充主体证明。
        if (payload.getSubjectBinding().getKind() != SubjectKind.DEPLOYMENT) {
            String profile = payload.getEligibilityPolicy().getOfflineProofProfile();
            java.util.Optional<SubjectRef> trustedSubject = inputs.authenticatedSubject(profile);
            java.util.Optional<SubjectRef> providerSubject = currentTrustedSubject(profile, now);
            if (!trustedSubject.isPresent() || !providerSubject.isPresent()
                    || !payload.getSubjectBinding().subjectRef().equals(trustedSubject.get())
                    || !payload.getSubjectBinding().subjectRef().equals(providerSubject.get())) {
                return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                        Decision.ReasonCategory.SUBJECT_INELIGIBLE);
            }
        }
        // HYBRID 租约的 requester 是领取租约的真实服务调用者，不能由文件或布尔核验结果代替。
        if (payload.getLease() != null) {
            java.util.Optional<SubjectRef> requester = inputs.authenticatedRequester();
            if (!requester.isPresent() || !payload.getLease().getRequester().equals(requester.get())) {
                return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                        Decision.ReasonCategory.SUBJECT_INELIGIBLE);
            }
        }
        // 规则允许不能替代额度事实。当前入口没有业务操作编号与原子台账事务，
        // LOCAL/实时累计扣减不得在此静默成功；实名/已签并发引用仅校验真实冻结事实。
        Decision.ReasonCategory quotaFailure = quotaFailure(payload, action, now);
        if (quotaFailure != null) {
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                    quotaFailure);
        }
        // 规则求值：错误即 DENY/异常，不被 not 翻转。
        // FactValues.of 对类型不符/超范围也抛裸 IllegalArgumentException（业务拒绝语义），
        // 这里统一归一为 DENY，不允许未分类异常逃逸到调用方（RULES.1：类型不符是 DENY 不是系统异常）。
        io.github.surezzzzzz.sdk.license.core.model.RuleModel model;
        FactValues values;
        if (facts == null) {
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                    Decision.ReasonCategory.FACT_MISSING);
        }
        try {
            model = io.github.surezzzzzz.sdk.license.core.codec.RuleModelWire.fromSnapshot(payload);
            values = FactValues.of(model, payload.getRights(), facts.asMap(), false);
        } catch (LicenseCoreException | IllegalArgumentException invalid) {
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                    Decision.ReasonCategory.FACT_MISSING);
        }
        boolean allowed;
        try {
            allowed = ConditionEvaluator.evaluate(model, action, values);
        } catch (FactValues.MissingFactException missing) {
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                    Decision.ReasonCategory.FACT_MISSING);
        } catch (LicenseCoreException | IllegalArgumentException invalid) {
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                    Decision.ReasonCategory.FACT_MISSING);
        }
        if (!allowed) {
            return Decision.deny(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                    Decision.ReasonCategory.RULE_DENIED);
        }
        return Decision.allow(action, payload.getModelSnapshotVersion(), commercial, connectivity, now,
                earliestRecheck(payload, now), reservationFenceReferences(payload));
    }

    /** HYBRID 的在线并发额度引用必须随 ALLOW 回传，供业务提交继续绑定同一围栏。 */
    private java.util.List<String> reservationFenceReferences(Protocol2Payload payload) {
        if (payload.getLease() == null) {
            return java.util.Collections.emptyList();
        }
        java.util.List<String> references = new java.util.ArrayList<String>();
        for (io.github.surezzzzzz.sdk.license.core.model.Lease.ReservationReference reference
                : payload.getLease().getReservationReferences()) {
            references.add(reference.getReservationId());
        }
        return references;
    }

    private Decision.ConnectivityPhase connectivity(Protocol2Payload payload, long now) {
        if (payload.getCredentialKind() == CredentialKind.OFFLINE) {
            return Decision.ConnectivityPhase.NOT_APPLICABLE;
        }
        return payload.getLease().connectivityPhaseAt(now);
    }

    /**
     * 当前吊销状态复查：覆盖 PROTOCOL.2 §7 的凭据/授权/激活/固定资格/签名钥五类目标。
     * verify 第 8 步与 authorize 每次判定共用，保证导入后新吊销即时阻断。
     */
    private boolean revokedNow(Protocol2Payload payload) {
        if (revocationStore.isRevoked(payload.getIssuerId(), payload.getProductId(), "CREDENTIAL", payload.getJti())
                || revocationStore.isRevoked(payload.getIssuerId(), payload.getProductId(), "GRANT", payload.getGrantId())) {
            return true;
        }
        if (payload.getSubjectBinding().getKind() == SubjectKind.DEPLOYMENT) {
            return revocationStore.isRevoked(payload.getIssuerId(), payload.getProductId(), "ACTIVATION",
                    payload.getSubjectBinding().activationId());
        }
        // 人员/服务固定资格：资格吊销阻断旧文件（PROTOCOL.2 §3）
        java.util.Optional<String> assignmentId = payload.getSubjectBinding().assignmentId();
        return assignmentId.isPresent() && revocationStore.isRevoked(payload.getIssuerId(), payload.getProductId(),
                "ASSIGNMENT", assignmentId.get());
    }

    private boolean actionAllowedInPhase(String action, Protocol2Payload payload,
                                         Decision.CommercialPhase commercial,
                                         Decision.ConnectivityPhase connectivity) {
        // 宽限期仅 graceActions；断网期仅 outageActions；正常期全模型动作
        if (commercial == Decision.CommercialPhase.COMMERCIAL_GRACE
                && !payload.getValidity().getGraceActions().contains(action)) {
            return false;
        }
        if (connectivity == Decision.ConnectivityPhase.OUTAGE
                && !payload.getLease().getOutageActions().contains(action)) {
            return false;
        }
        return true;
    }

    private Long earliestRecheck(Protocol2Payload payload, long now) {
        Long candidate = payload.getExpiresAt();
        candidate = earliestFuture(candidate, payload.getValidity().getServiceUntil(), now);
        candidate = earliestFuture(candidate, payload.getValidity().getCommercialGraceUntil(), now);
        candidate = earliestFuture(candidate, payload.getValidity().getMaintenanceUntil(), now);
        for (io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference reference
                : payload.getNamedSeatReferences()) {
            if (reference.getRetainUntil().isPresent()) {
                candidate = earliestFuture(candidate, reference.getRetainUntil().getAsLong(), now);
            }
        }
        if (payload.getCredentialKind() == CredentialKind.HYBRID && payload.getLease() != null) {
            candidate = earliestFuture(candidate, payload.getLease().getLeaseUntil(), now);
            candidate = earliestFuture(candidate, payload.getLease().getOutageUntil(), now);
            for (io.github.surezzzzzz.sdk.license.core.model.Lease.ReservationReference reservation
                    : payload.getLease().getReservationReferences()) {
                candidate = earliestFuture(candidate, reservation.getValidUntil(), now);
            }
        }
        if (payload.getRevocationPolicy().getMode()
                == io.github.surezzzzzz.sdk.license.core.model.RevocationPolicy.Mode.REQUIRED) {
            Optional<Long> issuedAt = revocationStore.installedIssuedAt(payload.getIssuerId(), payload.getProductId());
            if (issuedAt.isPresent()) {
                candidate = earliestFuture(candidate,
                        addSeconds(issuedAt.get(), payload.getRevocationPolicy().getMaxAgeSeconds()), now);
            }
        }
        return candidate;
    }

    /** 声明未知能力不能继续授权；组织签名租约尚无成员观察字段，明确拒绝而不猜造。 */
    private void validateCapabilities(Protocol2Payload payload) {
        java.util.Set<String> supported = new java.util.HashSet<String>(java.util.Arrays.asList(
                "license-condition/1", "clock-state/1", "subject-binding/1", "named-seat/1",
                "device-weighted/hardware/1", "device-key/p256/1", "quota-local/1", "hybrid-lease/1",
                "revocation-batch/1"));
        String profile = payload.getEligibilityPolicy().getOfflineProofProfile();
        if (profile != null && supportedOfflineProofProfiles.contains(profile)) {
            // 可信身份与设备证据仍由 FileVerificationInputs 的宿主端口逐次提供；
            // profile 只能来自宿主注册能力，不能由凭据声明后自动加入 supported。
            supported.add("subject-proof/" + profile);
        }
        java.util.Set<String> declared = new java.util.HashSet<String>(payload.getRequiredCapabilities());
        requireCapability(declared, "license-condition/1");
        requireCapability(declared, "clock-state/1");
        requireCapability(declared, "subject-binding/1");
        if (profile != null) {
            requireCapability(declared, "subject-proof/" + profile);
        }
        if (payload.getCredentialKind() == CredentialKind.HYBRID) {
            requireCapability(declared, "hybrid-lease/1");
        }
        if (payload.getOrganizationPolicy().isRequired()) {
            requireCapability(declared, "organization-lease/1");
        }
        if (payload.getDeviceBinding().getMode() == io.github.surezzzzzz.sdk.license.core.model.DeviceBinding.Mode.WEIGHTED) {
            requireCapability(declared, "device-weighted/hardware/1");
        } else if (payload.getDeviceBinding().getMode()
                == io.github.surezzzzzz.sdk.license.core.model.DeviceBinding.Mode.DEVICE_KEY) {
            requireCapability(declared, "device-key/p256/1");
        }
        if (!payload.getNamedSeatReferences().isEmpty()) {
            requireCapability(declared, "named-seat/1");
        }
        if (payload.getRevocationPolicy().getMode()
                == io.github.surezzzzzz.sdk.license.core.model.RevocationPolicy.Mode.REQUIRED) {
            requireCapability(declared, "revocation-batch/1");
        }
        quotaCapabilityRequirements(payload, declared);
        for (String capability : payload.getRequiredCapabilities()) {
            if (!supported.contains(capability)) {
                throw new LicenseCoreException(ErrorCode.CAPABILITY_UNSUPPORTED, ErrorMessage.CAPABILITY_UNSUPPORTED);
            }
        }
        if (payload.getOrganizationPolicy().isRequired()) {
            throw new LicenseCoreException(ErrorCode.CAPABILITY_UNSUPPORTED, ErrorMessage.CAPABILITY_UNSUPPORTED);
        }
    }

    private void requireCapability(java.util.Set<String> declared, String capability) {
        if (!declared.contains(capability)) {
            throw new LicenseCoreException(ErrorCode.CAPABILITY_UNSUPPORTED, ErrorMessage.CAPABILITY_UNSUPPORTED);
        }
    }

    /** 每次授权重新读取可信主体，并验证档案版本与证明硬截止；任何提供方异常均拒绝。 */
    private java.util.Optional<SubjectRef> currentTrustedSubject(String profile, long now) {
        if (trustedSubjectProvider == null || profile == null || profile.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            if (!profile.equals(trustedSubjectProvider.profileVersion())
                    || trustedSubjectProvider.proofValidUntil() <= now) {
                return java.util.Optional.empty();
            }
            java.util.Optional<SubjectRef> subject = trustedSubjectProvider.currentTrustedSubject(profile);
            return subject == null ? java.util.Optional.empty() : subject;
        } catch (RuntimeException unavailable) {
            return java.util.Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private void quotaCapabilityRequirements(Protocol2Payload payload, java.util.Set<String> declared) {
        if (payload.getQuotaPolicy().isEmpty()) {
            return;
        }
        Object rawDefinitions = payload.getQuotaPolicy().get("definitions");
        if (!(rawDefinitions instanceof java.util.Map)) {
            return;
        }
        for (Object rawDefinition : ((java.util.Map<?, ?>) rawDefinitions).values()) {
            if (!(rawDefinition instanceof java.util.Map)) {
                continue;
            }
            java.util.Map<String, Object> definition = (java.util.Map<String, Object>) rawDefinition;
            if ("LOCAL".equals(definition.get("enforcement"))) {
                requireCapability(declared, "quota-local/1");
            }
            if ("NAMED_SEAT".equals(definition.get("kind"))) {
                requireCapability(declared, "named-seat/1");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Decision.ReasonCategory quotaFailure(Protocol2Payload payload, String action, long now) {
        if (payload.getQuotaPolicy().isEmpty()) {
            return null;
        }
        java.util.Map<String, Object> definitions = (java.util.Map<String, Object>)
                payload.getQuotaPolicy().get("definitions");
        java.util.Map<String, Object> constraints = (java.util.Map<String, Object>)
                payload.getQuotaPolicy().get("constraints");
        java.util.List<String> required = (java.util.List<String>) constraints.get(action);
        if (required == null) {
            return null;
        }
        for (String metric : required) {
            java.util.Map<String, Object> definition = (java.util.Map<String, Object>) definitions.get(metric);
            String kind = (String) definition.get("kind");
            if ("LOCAL".equals(definition.get("enforcement")) || "CONSUMPTION".equals(kind)) {
                return Decision.ReasonCategory.CAPABILITY_MISSING;
            }
            if ("NAMED_SEAT".equals(kind)) {
                boolean assigned = false;
                for (io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference seat
                        : payload.getNamedSeatReferences()) {
                    if (metric.equals(seat.getMetric()) && (!seat.getRetainUntil().isPresent()
                            || now < seat.getRetainUntil().getAsLong())) {
                        assigned = true;
                    }
                }
                if (!assigned) {
                    return Decision.ReasonCategory.SUBJECT_INELIGIBLE;
                }
            } else if ("CONCURRENT".equals(kind)) {
                boolean reserved = false;
                if (payload.getLease() != null) {
                    for (io.github.surezzzzzz.sdk.license.core.model.Lease.ReservationReference reference
                            : payload.getLease().getReservationReferences()) {
                        if (metric.equals(reference.getMetric()) && reference.getQuantity() > 0
                                && reference.getFence() > 0 && now < reference.getValidUntil()) {
                            reserved = true;
                        }
                    }
                }
                if (!reserved) {
                    return Decision.ReasonCategory.SUBJECT_INELIGIBLE;
                }
            }
        }
        return null;
    }

    /** 只选尚未到达的阶段边界，避免将已过去的重查点返回为可缓存许可。 */
    private Long earliestFuture(Long candidate, Long boundary, long now) {
        return boundary != null && boundary > now && (candidate == null || boundary < candidate)
                ? boundary : candidate;
    }

    /** 时间容差仅用于观测误差，饱和加法防止极值溢出反转安全判断。 */
    private long addSeconds(long instant, long seconds) {
        return instant > Long.MAX_VALUE - seconds ? Long.MAX_VALUE : instant + seconds;
    }

    /** 重启仍核对持久化时间水位；小幅回退使用较晚时刻，不延长商业或租约截止。 */
    private long effectiveNow(Protocol2Payload payload) {
        long now = clock.currentEffectiveNow();
        String stateKey = stateStore.buildStateKey(payload.getIssuerId(), payload.getProductId(),
                payload.getCustomerId(), payload.getGrantId(), stableObjectKey(payload));
        java.util.OptionalLong maximum = stateStore.loadMaxTrustedTime(stateKey);
        if (clock.isClockSuspect() || (maximum.isPresent()
                && maximum.getAsLong() > addSeconds(now, payload.getClockPolicy().getRollbackToleranceSeconds()))) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
        }
        return maximum.isPresent() ? Math.max(now, maximum.getAsLong()) : now;
    }

    private boolean bindingMatches(SubjectBinding actual, SubjectBinding expected) {
        return actual.equals(expected);
    }

    private String stableObjectKey(Protocol2Payload payload) {
        SubjectBinding binding = payload.getSubjectBinding();
        if (binding.getKind() == SubjectKind.DEPLOYMENT) {
            return "activation:" + binding.activationId();
        }
        io.github.surezzzzzz.sdk.license.core.model.SubjectRef ref = binding.subjectRef();
        return "subject:" + ref.getSourceId() + ":" + ref.getSubjectId();
    }

    /** 设备/人员核验结果输入（宿主先行完成）。 */
    public interface FileVerificationInputs {

        /** 设备分量匹配与人员本地身份核验是否全部通过。 */
        boolean isSubjectAndDeviceAcceptable();

        /**
         * 受控离线身份档案核验后的真实主体；人员/服务凭据必须返回与签名绑定完全一致的引用。
         * 默认空值用于保留接口兼容，但会使对应动作确定拒绝。
         */
        default java.util.Optional<SubjectRef> authenticatedSubject(String offlineProofProfile) {
            return java.util.Optional.empty();
        }

        /** 当前动作实际认证的租约领取服务；OFFLINE 文件返回空即可，HYBRID 必须提供。 */
        default java.util.Optional<SubjectRef> authenticatedRequester() {
            return java.util.Optional.empty();
        }
    }
}
