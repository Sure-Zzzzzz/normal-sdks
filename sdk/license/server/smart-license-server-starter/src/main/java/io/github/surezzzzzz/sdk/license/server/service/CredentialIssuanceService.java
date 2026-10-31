package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.core.codec.CompactJws;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.model.SigningInput;
import io.github.surezzzzzz.sdk.license.core.spi.LicenseSigner;
import io.github.surezzzzzz.sdk.license.server.constant.OperationState;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationConstant;
import io.github.surezzzzzz.sdk.license.server.constant.SigningOperationConstant;
import io.github.surezzzzzz.sdk.license.server.configuration.LicenseServerProperties;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.state.SigningOperationStateMachine;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.UUID;

/**
 * 凭据签发服务：受理（先持久化冻结输入）→ 事务外签名 → 返回后核验并提交。
 *
 * <p>受理前完成 payload 预算预检（REJECTED 明确指明超预算）；签名调用异常一律 UNCERTAIN，
 * 不自动重发；恢复沿用原 operationId 受控代数递增（旧工作者无法提交）。
 * 本地验签直接使用签名档案持久化的公钥快照（登记期固化），签名路径不现场读取公钥。</p>
 *
 * @author surezzzzzz
 */
@lombok.extern.slf4j.Slf4j
@SimpleLicenseServerComponent
public final class CredentialIssuanceService {

    /** 任务仓储端口。 */
    private final OperationRepository repository;
    private final LicenseSigner signer;
    private final SigningProfileRepository profiles;
    private final LicenseRevocationGuard revocationGuard;
    private final LicenseServerProperties properties;

    public CredentialIssuanceService(OperationRepository repository, LicenseSigner signer,
                                     SigningProfileRepository profiles) {
        this(repository, signer, profiles, null);
    }

    /** 隔离测试构造：保留吊销围栏，但默认禁止接管SIGNING。 */
    public CredentialIssuanceService(OperationRepository repository, LicenseSigner signer,
                                     SigningProfileRepository profiles, LicenseRevocationGuard revocationGuard) {
        this(repository, signer, profiles, revocationGuard, new LicenseServerProperties());
    }

    /** 生产构造显式承接宿主的失联接管政策；未配置时保持失败关闭。 */
    @org.springframework.beans.factory.annotation.Autowired
    public CredentialIssuanceService(OperationRepository repository, LicenseSigner signer,
                                     SigningProfileRepository profiles, LicenseRevocationGuard revocationGuard,
                                     LicenseServerProperties properties) {
        this.repository = repository;
        this.signer = signer;
        this.profiles = profiles;
        this.revocationGuard = revocationGuard;
        this.properties = java.util.Objects.requireNonNull(properties, "properties");
    }

    /**
     * 受理签发：预算预检+冻结输入持久化（PENDING_SIGN），返回任务编号。
     *
     * @param payloadSegment 已构造完成并编码的冻结payload段
     * @return operationId（调用方凭此轮询/恢复）
     */
    public String accept(String operationId, String credentialId, String grantId, String signingProfileId,
                         String headerSegment, String payloadSegment, String idempotencyDigest) {
        if (idempotencyDigest == null || idempotencyDigest.isEmpty()
                || idempotencyDigest.length() > SigningOperationConstant.IDEMPOTENCY_MAX_LENGTH) {
            throw LicenseHttpException.unprocessable(SigningOperationConstant.INVALID_IDEMPOTENCY);
        }
        SigningOperationEntity existing = repository.findByIdempotencyDigest(idempotencyDigest);
        if (existing != null) {
            if (!grantId.equals(existing.getGrantId())) {
                throw LicenseHttpException.conflict(SigningOperationConstant.VERSION_CONFLICT);
            }
            return existing.getOperationId();
        }
        // 预算预检：header/payload/整体 Compact JWS 任一超协议预算即在受理期落 REJECTED，不进入签名。
        // 段预算针对解码原始字节；整体预算针对已编码ASCII段及64B JOSE签名，不能重复4/3膨胀。
        int headerBytes = decodedSegmentBytes(headerSegment);
        int payloadBytes = decodedSegmentBytes(payloadSegment);
        int signatureSegmentLength = (SmartLicenseCoreConstant.ES256_SIGNATURE_BYTES * 8 + 5) / 6;
        long jwsBytes = (long) headerSegment.length() + payloadSegment.length() + 2 + signatureSegmentLength;
        if (headerBytes > SmartLicenseCoreConstant.HEADER_MAX_BYTES
                || payloadBytes > SmartLicenseCoreConstant.PAYLOAD_MAX_BYTES
                || jwsBytes > SmartLicenseCoreConstant.JWS_MAX_BYTES) {
            SigningOperationEntity rejected = new SigningOperationEntity(
                    operationId, credentialId, grantId, signingProfileId,
                    headerSegment, payloadSegment, idempotencyDigest);
            rejected.setState(OperationState.REJECTED.getCode());
            SigningOperationEntity accepted = saveOrFindWinner(rejected);
            if (!operationId.equals(accepted.getOperationId())) {
                return accepted.getOperationId();
            }
            log.warn("签发受理拒绝：凭据规模超协议预算，operationId={}, headerBytes={}, payloadBytes={}",
                    operationId, headerBytes, payloadBytes);
            throw LicenseHttpException.unprocessable(SigningOperationConstant.PAYLOAD_BUDGET_EXCEEDED);
        }
        SigningOperationEntity entity = new SigningOperationEntity(
                operationId, credentialId, grantId, signingProfileId,
                headerSegment, payloadSegment, idempotencyDigest);
        requireUsable(entity);
        return saveOrFindWinner(entity).getOperationId();
    }

    /** 解码前后不改写冻结输入；非规范编码明确拒绝，不受理为可签名任务。 */
    private static int decodedSegmentBytes(String segment) {
        try {
            byte[] decoded = io.github.surezzzzzz.sdk.license.core.codec.Base64Url.decode(segment);
            if (!io.github.surezzzzzz.sdk.license.core.codec.Base64Url.encode(decoded).equals(segment)) {
                throw LicenseHttpException.unprocessable(SigningOperationConstant.INVALID_FROZEN_ENCODING);
            }
            return decoded.length;
        } catch (LicenseHttpException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw LicenseHttpException.unprocessable(SigningOperationConstant.INVALID_FROZEN_ENCODING);
        }
    }

    /** 唯一屏障在本地事务提交时生效；冲突事务已退出后才回读赢家，非同键故障原样透传。 */
    private SigningOperationEntity saveOrFindWinner(SigningOperationEntity entity) {
        try {
            repository.save(entity);
            return entity;
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            SigningOperationEntity winner = repository.findByIdempotencyDigest(entity.getIdempotencyDigest());
            if (winner == null || !entity.getGrantId().equals(winner.getGrantId())) {
                throw conflict;
            }
            log.debug("签发受理并发已回读首任务：operationId={}", winner.getOperationId());
            return winner;
        }
    }

    /**
     * 执行签名：PENDING_SIGN→SIGNING→READY；任何调用异常标记 UNCERTAIN（不盲重发）。
     *
     * @return READY 后的完整 Compact JWS 文本
     */
    public String signAndCommit(String operationId) {
        SigningOperationEntity entity = find(operationId);
        if (!OperationState.PENDING_SIGN.getCode().equals(entity.getState())) {
            throw LicenseHttpException.conflict(SigningOperationConstant.INITIAL_STATE_INVALID);
        }
        requireUsable(entity);
        SigningOperationStateMachine machine = new SigningOperationStateMachine(
                OperationState.fromCode(entity.getState()), entity.getRowVersion(),
                entity.getExecutionGeneration());
        if (!machine.transition(OperationState.SIGNING, entity.getRowVersion())) {
            throw new IllegalStateException("签发任务状态迁移失败：" + entity.getState());
        }
        entity.setState(machine.getState().getCode());
        entity.setExecutionGeneration(machine.getExecutionGeneration());
        // KMS 调用追踪：任务行先落追踪号与时刻（与 SIGNING 同次落库）——UNCERTAIN 时对账锚已在库
        String kmsCallRef = "kms-" + UUID.randomUUID();
        entity.setLastKmsCallRef(kmsCallRef);
        entity.setLastKmsCallAt(java.time.Instant.now());
        // SIGNING 状态先落库（签名期间任务可见性：看门狗可识别失联任务）
        repository.update(entity);
        return signClaimedAndCommit(entity);
    }

    /**
     * 管理员受控恢复同一冻结任务：原编号与输入保持，先CAS提升代数并认领后才真正执行签名。
     * 此兼容请求仅接受UNCERTAIN；SIGNING接管必须走带代数和理由的显式请求。
     */
    public String recoverAndCommit(String operationId, long expectedRowVersion) {
        return recoverAndCommit(operationId, expectedRowVersion, null, null);
    }

    /**
     * 受控恢复或人工接管失联签名。SIGNING接管默认禁用，窗口、代数、理由缺一不可。
     * 不现场修改冻结输入，也不查询或假设KMS有签名幂等能力。
     */
    public String recoverAndCommit(String operationId, long expectedRowVersion,
                                    Long expectedExecutionGeneration, String reasonCategory) {
        SigningOperationEntity entity = find(operationId);
        if (entity.getRowVersion() != expectedRowVersion) {
            throw LicenseHttpException.conflict(SigningOperationConstant.VERSION_CONFLICT);
        }
        boolean staleSigning = OperationState.SIGNING.getCode().equals(entity.getState());
        if (!staleSigning && !OperationState.UNCERTAIN.getCode().equals(entity.getState())) {
            throw LicenseHttpException.conflict(SigningOperationConstant.RECOVERY_STATE_INVALID);
        }
        if (expectedExecutionGeneration != null
                && entity.getExecutionGeneration() != expectedExecutionGeneration) {
            throw LicenseHttpException.conflict(SigningOperationConstant.VERSION_CONFLICT);
        }
        if (reasonCategory != null) {
            requireReason(reasonCategory);
        }
        if (staleSigning) {
            requireStaleTakeover(entity, expectedExecutionGeneration, reasonCategory);
        }
        requireUsable(entity);
        long expectedGeneration = entity.getExecutionGeneration();
        SigningOperationStateMachine machine = new SigningOperationStateMachine(
                OperationState.fromCode(entity.getState()), entity.getRowVersion(), expectedGeneration);
        boolean claimed = staleSigning
                ? machine.takeOverStaleSigning(expectedRowVersion, expectedGeneration)
                : machine.recover(expectedRowVersion);
        if (!claimed) {
            throw LicenseHttpException.conflict(SigningOperationConstant.RECOVERY_STATE_INVALID);
        }
        entity.setState(machine.getState().getCode());
        entity.setExecutionGeneration(machine.getExecutionGeneration());
        entity.setCommittedSignature(null);
        entity.setReasonCategory(null);
        entity.setLastKmsCallRef("kms-" + UUID.randomUUID());
        entity.setLastKmsCallAt(java.time.Instant.now());
        try {
            repository.update(entity, expectedGeneration);
        } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
            throw LicenseHttpException.conflict(SigningOperationConstant.VERSION_CONFLICT);
        }
        log.info("签发受控恢复已认领：operationId={}，executionGeneration={}，kmsCallRef={}，staleTakeover={}",
                operationId, entity.getExecutionGeneration(), entity.getLastKmsCallRef(), staleSigning);
        return signClaimedAndCommit(entity);
    }

    /** 人工接管只消费宿主显式窗口；没有调用时刻或时刻在未来均不可证明已失联。 */
    private void requireStaleTakeover(SigningOperationEntity entity, Long expectedGeneration, String reason) {
        Long seconds = properties.getStaleSigningAfterSeconds();
        if (seconds == null || seconds <= 0) {
            throw LicenseHttpException.conflict(SigningOperationConstant.STALE_SIGNING_DISABLED);
        }
        if (expectedGeneration == null || reason == null) {
            throw LicenseHttpException.unprocessable(SigningOperationConstant.TAKEOVER_EVIDENCE_REQUIRED);
        }
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant lastCall = entity.getLastKmsCallAt();
        if (lastCall == null || lastCall.isAfter(now)
                || java.time.Duration.between(lastCall, now).compareTo(java.time.Duration.ofSeconds(seconds)) < 0) {
            throw LicenseHttpException.conflict(SigningOperationConstant.STALE_SIGNING_NOT_EXPIRED);
        }
    }

    private static void requireReason(String reason) {
        if (reason.trim().isEmpty() || reason.length() > SigningOperationConstant.REASON_MAX_LENGTH) {
            throw LicenseHttpException.unprocessable(SigningOperationConstant.INVALID_REASON);
        }
    }

    /** 安全放弃同一任务：只接受状态机允许的非有效终态，用CAS阻止并发恢复或迟到覆盖。 */
    public void abandon(String operationId, long expectedRowVersion, String reasonCategory) {
        if (reasonCategory != null) {
            requireReason(reasonCategory);
        }
        SigningOperationEntity entity = find(operationId);
        SigningOperationStateMachine machine = new SigningOperationStateMachine(
                OperationState.fromCode(entity.getState()), entity.getRowVersion(), entity.getExecutionGeneration());
        if (entity.getRowVersion() != expectedRowVersion) {
            throw LicenseHttpException.conflict(SigningOperationConstant.VERSION_CONFLICT);
        }
        if (!machine.transition(OperationState.ABANDONED, expectedRowVersion)) {
            throw LicenseHttpException.conflict(SigningOperationConstant.ABANDON_STATE_INVALID);
        }
        entity.setState(OperationState.ABANDONED.getCode());
        entity.setReasonCategory(reasonCategory);
        try {
            repository.update(entity);
        } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
            throw LicenseHttpException.conflict(SigningOperationConstant.VERSION_CONFLICT);
        }
        log.info("签发任务已安全放弃：operationId={}，executionGeneration={}",
                operationId, entity.getExecutionGeneration());
    }

    private SigningOperationEntity find(String operationId) {
        SigningOperationEntity entity = repository.find(operationId);
        if (entity == null) {
            throw LicenseHttpException.notFound();
        }
        return entity;
    }

    /** 仅本次成功认领者可执行，entity携带认领后行版本与代数，最终提交不得重读放宽围栏。 */
    private String signClaimedAndCommit(SigningOperationEntity entity) {
        String operationId = entity.getOperationId();
        String kmsCallRef = entity.getLastKmsCallRef();

        byte[] signature;
        try {
            String jwsText = CompactJws.join(entity.getFrozenHeader(), entity.getFrozenPayload(), "");
            // 签名原文=前两段+点号（CompactJws.parse 校验三段，此处先拼再取）
            SigningInput input = SigningInput.ofCompactJws(jwsText + "sig");
            signature = signer.sign(entity.getSigningProfileId(), operationId, input);
        } catch (RuntimeException failure) {
            markUncertain(entity, "SIGN_CALL_FAILED");
            log.warn("签名调用失败进 UNCERTAIN：operationId={}, kmsCallRef={}, 异常类型={}",
                    operationId, kmsCallRef, failure.getClass().getSimpleName());
            throw new IllegalStateException("签名调用失败，任务已标记 UNCERTAIN", failure);
        }

        // 本地原文验签：防 KMS 返回与档案不符的结果（RFC 7515 原文=前两段+单个点号，无尾点）；
        // 公钥用档案登记期固化的持久化快照（RETIRED 档案在途任务同样可完成验签提交）
        byte[] signingInput = (entity.getFrozenHeader() + "." + entity.getFrozenPayload())
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        boolean verified;
        try {
            verified = Es256Primitives.verify(signingInput, signature,
                    Es256Primitives.parseSpkiPublicKey(snapshotSpki(entity.getSigningProfileId())));
        } catch (RuntimeException verifyFailure) {
            markUncertain(entity, "LOCAL_VERIFY_FAILED");
            throw new IllegalStateException("本地验签失败，任务已标记 UNCERTAIN", verifyFailure);
        }
        if (!verified) {
            markUncertain(entity, "LOCAL_VERIFY_FAILED");
            throw new IllegalStateException("本地验签不通过（签名与登记公钥不符），任务已标记 UNCERTAIN");
        }

        try {
            requireUsable(entity);
        } catch (LicenseHttpException denied) {
            if (denied.getStatus() != 409) {
                markUncertain(entity, RevocationConstant.GUARD_UNAVAILABLE_REASON);
                throw denied;
            }
            // 已证实对象失效才进入确定拒绝；无法复核不能冒充确定吊销。
            entity.setState(OperationState.REJECTED.getCode());
            entity.setReasonCategory(RevocationConstant.OBJECT_UNUSABLE_REASON);
            repository.update(entity);
            log.warn("签名结果提交已拒绝：operationId={}，阶段=业务围栏", entity.getOperationId());
            throw denied;
        } catch (RuntimeException denied) {
            // 外部签名可能已完成；本地依赖失败只说明结果未知，保留受控恢复语义。
            markUncertain(entity, RevocationConstant.GUARD_UNAVAILABLE_REASON);
            log.warn("签名结果复核暂不可用：operationId={}，异常类型={}",
                    entity.getOperationId(), denied.getClass().getSimpleName());
            throw new IllegalStateException(RevocationConstant.GUARD_UNAVAILABLE, denied);
        }
        entity.setCommittedSignature(io.github.surezzzzzz.sdk.license.core.codec.Base64Url.encode(signature));
        entity.setState(OperationState.READY.getCode());
        try {
            repository.update(entity);
        } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
            log.warn("签发状态迁移并发冲突：operationId={}, 阶段=READY", entity.getOperationId());
            throw new IllegalStateException("签发任务状态被并发修改，操作未生效", conflict);
        }
        // 只有持久化确认后才记录READY，不能把失败的CAS提交记成成功。
        log.info("签发任务 READY：operationId={}, credentialId={}, signingProfileId={}, kmsCallRef={}",
                entity.getOperationId(), entity.getCredentialId(), entity.getSigningProfileId(), kmsCallRef);
        return CompactJws.join(entity.getFrozenHeader(), entity.getFrozenPayload(),
                io.github.surezzzzzz.sdk.license.core.codec.Base64Url.encode(signature));
    }

    private void requireUsable(SigningOperationEntity entity) {
        if (revocationGuard != null) {
            revocationGuard.requireCredentialUsable(entity);
        }
    }

    /** 档案持久化公钥快照（hex→SPKI 字节）；档案缺失即在途档案被清理，按验签失败处理。 */
    private byte[] snapshotSpki(String signingProfileId) {
        SigningProfileEntity profile = profiles.findById(signingProfileId).orElse(null);
        if (profile == null) {
            throw new IllegalStateException("签名档案不存在（在途任务档案被清理）：" + signingProfileId);
        }
        String hex = profile.getRegisteredPublicKeyHex();
        byte[] spki = new byte[hex.length() / 2];
        for (int i = 0; i < spki.length; i++) {
            spki[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return spki;
    }

    private void markUncertain(SigningOperationEntity entity, String reason) {
        entity.setState(OperationState.UNCERTAIN.getCode());
        entity.setReasonCategory(reason);
        repository.update(entity);
    }
}
