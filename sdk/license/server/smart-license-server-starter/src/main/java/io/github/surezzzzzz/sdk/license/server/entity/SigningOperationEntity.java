package io.github.surezzzzzz.sdk.license.server.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.server.constant.SigningOperationConstant;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

/**
 * 签发/协作任务持久化实体：冻结签名输入、档案引用、执行代数与状态。
 *
 * <p>受理事务先持久化本行，再事务外调用 KMS；UNCERTAIN 不自动重签，受控恢复提升执行代数。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_signing_operation", indexes = @Index(
        name = "idx_license_signing_operation_activation", columnList = "activation_id,grant_id,operation_id"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_license_signing_operation_idempotency", columnNames = "idempotency_digest"),
                @UniqueConstraint(name = "uk_license_signing_operation_credential", columnNames = "credential_id")})
@Slf4j
public class SigningOperationEntity {

    @Id
    @Column(name = "operation_id", length = 128, nullable = false)
    private String operationId;

    @Column(name = "credential_id", length = 128, nullable = false)
    private String credentialId;

    @Column(name = "grant_id", length = 128, nullable = false)
    private String grantId;

    /** 由冻结载荷受理时派生的部署激活编号；人员/服务凭据为空，用于 SQL 分页前筛选。 */
    @Getter
    @Column(name = "activation_id", length = SigningOperationConstant.IDENTIFIER_MAX_LENGTH)
    private String activationId;

    @Column(name = "signing_profile_id", length = 128, nullable = false)
    private String signingProfileId;

    @Column(name = "state", length = 16, nullable = false)
    private String state;

    @Column(name = "execution_generation", nullable = false)
    private long executionGeneration;

    /** 冻结的 protected header 原文（Base64URL 文本）。 */
    @Column(name = "frozen_header", length = 8192, nullable = false)
    private String frozenHeader;

    /** 冻结的 JWS payload 段（无 padding 的 Base64URL 文本；签名输入保持原段字节）。 */
    @Lob
    @Column(name = "frozen_payload", nullable = false)
    private String frozenPayload;

    /** 已提交签名结果（READY 后非空）。 */
    @Lob
    @Column(name = "committed_signature")
    private String committedSignature;

    @Column(name = "idempotency_digest", length = 128, nullable = false,
            columnDefinition = "varchar(128) COLLATE utf8mb4_bin")
    private String idempotencyDigest;

    @Column(name = "reason_category", length = 64)
    private String reasonCategory;

    /** 最近一次签名服务调用的追踪号（License 侧生成，进实现方日志，与 KMS 审计对账的关联锚）。 */
    @Column(name = "last_kms_call_ref", length = 64)
    private String lastKmsCallRef;

    /** 最近一次签名服务调用时刻（KMS 审计按主体+时间窗对账定位 requestId）。 */
    @Column(name = "last_kms_call_at")
    private java.time.Instant lastKmsCallAt;

    @Version
    @lombok.Setter
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected SigningOperationEntity() {
    }

    public SigningOperationEntity(String operationId, String credentialId, String grantId,
                                   String signingProfileId, String frozenHeader, String frozenPayload,
                                   String idempotencyDigest) {
        this.operationId = operationId;
        this.credentialId = credentialId;
        this.grantId = grantId;
        this.signingProfileId = signingProfileId;
        this.state = "PENDING_SIGN";
        this.executionGeneration = 1L;
        this.frozenHeader = frozenHeader;
        this.frozenPayload = frozenPayload;
        this.activationId = activationIdFrom(frozenPayload);
        this.idempotencyDigest = idempotencyDigest;
    }

    /**
     * 元数据索引从冻结输入派生，不接受独立可改的激活归属，避免索引与签名内容漂移。
     * 旧的非协议任务占位输入允许无归属；可解析的部署绑定缺少编号则拒绝受理。
     */
    private static String activationIdFrom(String payloadSegment) {
        ObjectNode payload;
        try {
            payload = StrictJson.parse(new String(Base64Url.decode(payloadSegment), StandardCharsets.UTF_8),
                    ObjectNode.class);
        } catch (RuntimeException failure) {
            log.debug("冻结输入未形成部署归属索引：failureType={}", failure.getClass().getSimpleName());
            return null;
        }
        if (payload == null) {
            return null;
        }
        JsonNode binding = payload.path(SigningOperationConstant.SUBJECT_BINDING);
        if (!SubjectKind.DEPLOYMENT.getCode().equals(binding.path(SigningOperationConstant.KIND).asText())) {
            return null;
        }
        JsonNode id = binding.path(SigningOperationConstant.ACTIVATION_ID);
        if (!id.isTextual() || id.asText().trim().isEmpty()
                || id.asText().length() > SigningOperationConstant.IDENTIFIER_MAX_LENGTH) {
            throw new IllegalArgumentException(SigningOperationConstant.INVALID_DEPLOYMENT_BINDING);
        }
        return id.asText();
    }

    public String getOperationId() {
        return operationId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getSigningProfileId() {
        return signingProfileId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public long getExecutionGeneration() {
        return executionGeneration;
    }

    public void setExecutionGeneration(long executionGeneration) {
        this.executionGeneration = executionGeneration;
    }

    public String getFrozenHeader() {
        return frozenHeader;
    }

    public String getFrozenPayload() {
        return frozenPayload;
    }

    public String getCommittedSignature() {
        return committedSignature;
    }

    public void setCommittedSignature(String committedSignature) {
        this.committedSignature = committedSignature;
    }

    public String getIdempotencyDigest() {
        return idempotencyDigest;
    }

    public String getReasonCategory() {
        return reasonCategory;
    }

    public void setReasonCategory(String reasonCategory) {
        this.reasonCategory = reasonCategory;
    }

    public String getLastKmsCallRef() {
        return lastKmsCallRef;
    }

    public void setLastKmsCallRef(String lastKmsCallRef) {
        this.lastKmsCallRef = lastKmsCallRef;
    }

    public java.time.Instant getLastKmsCallAt() {
        return lastKmsCallAt;
    }

    public void setLastKmsCallAt(java.time.Instant lastKmsCallAt) {
        this.lastKmsCallAt = lastKmsCallAt;
    }

    public long getRowVersion() {
        return rowVersion;
    }
}
