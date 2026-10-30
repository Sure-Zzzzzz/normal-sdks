package io.github.surezzzzzz.sdk.license.core.model;

import java.util.Collections;
import java.util.List;

/**
 * 来源中立判定结果：ALLOW/DENY、阶段、原因分类、动作、模型版本、判定时刻与最早重查截止。
 *
 * <p>无效基础材料不暴露完整权益；结果不含原始 JWS。recheckAt 是本次已知商业/维护/租约/成员/
 * 占用/新鲜度最早失效截止，不是缓存许可。</p>
 *
 * @author surezzzzzz
 */
public final class Decision {

    /** 判定结论。 */
    public enum Outcome {
        ALLOW("ALLOW"), DENY("DENY");

        private final String code;

        Outcome(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /** 商业时间阶段。 */
    public enum CommercialPhase {
        NOT_STARTED("NOT_STARTED"), ACTIVE("ACTIVE"), COMMERCIAL_GRACE("COMMERCIAL_GRACE"), ENDED("ENDED");

        private final String code;

        CommercialPhase(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /** 连接阶段。 */
    public enum ConnectivityPhase {
        NOT_APPLICABLE("NOT_APPLICABLE"), NORMAL("NORMAL"), OUTAGE("OUTAGE"), ENDED("ENDED");

        private final String code;

        ConnectivityPhase(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /** 拒绝原因安全分类。 */
    public enum ReasonCategory {
        ENTITLED("ENTITLED"),
        RULE_DENIED("RULE_DENIED"),
        FACT_MISSING("FACT_MISSING"),
        COMMERCIAL_PHASE("COMMERCIAL_PHASE"),
        CONNECTIVITY_PHASE("CONNECTIVITY_PHASE"),
        SUBJECT_INELIGIBLE("SUBJECT_INELIGIBLE"),
        GRANT_SUSPENDED("GRANT_SUSPENDED"),
        REVOKED("REVOKED"),
        CAPABILITY_MISSING("CAPABILITY_MISSING");

        private final String code;

        ReasonCategory(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final Outcome outcome;
    private final String action;
    private final long modelVersion;
    private final CommercialPhase commercialPhase;
    private final ConnectivityPhase connectivityPhase;
    private final long evaluatedAt;
    private final Long recheckAt;
    private final ReasonCategory reasonCategory;
    private final List<String> reservationFenceReferences;
    private final String productId;
    private final String grantId;
    private final DecisionBinding subjectBinding;
    private final Long grantRevision;
    private final Long grantStateVersion;

    private Decision(Outcome outcome, String action, long modelVersion, CommercialPhase commercialPhase,
                     ConnectivityPhase connectivityPhase, long evaluatedAt, Long recheckAt,
                     ReasonCategory reasonCategory, List<String> reservationFenceReferences,
                     String productId, String grantId, DecisionBinding subjectBinding,
                     Long grantRevision, Long grantStateVersion) {
        this.outcome = outcome;
        this.action = action;
        this.modelVersion = modelVersion;
        this.commercialPhase = commercialPhase;
        this.connectivityPhase = connectivityPhase;
        this.evaluatedAt = evaluatedAt;
        this.recheckAt = recheckAt;
        this.reasonCategory = reasonCategory;
        this.reservationFenceReferences = reservationFenceReferences == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<String>(reservationFenceReferences));
        if (productId == null && grantId == null && subjectBinding == null
                && grantRevision == null && grantStateVersion == null) {
            this.productId = null;
            this.grantId = null;
            this.subjectBinding = null;
            this.grantRevision = null;
            this.grantStateVersion = null;
        } else if (productId != null && !productId.isEmpty() && grantId != null && !grantId.isEmpty()
                && subjectBinding != null && grantRevision != null && grantRevision > 0L
                && grantRevision <= io.github.surezzzzzz.sdk.license.core.support.ProtocolValueHelper.SAFE_INTEGER_MAX
                && grantStateVersion != null && grantStateVersion > 0L
                && grantStateVersion <= io.github.surezzzzzz.sdk.license.core.support.ProtocolValueHelper.SAFE_INTEGER_MAX) {
            this.productId = productId;
            this.grantId = grantId;
            this.subjectBinding = subjectBinding;
            this.grantRevision = grantRevision;
            this.grantStateVersion = grantStateVersion;
        } else {
            throw new IllegalArgumentException(io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage
                    .PROTOCOL_BINDING_INVALID);
        }
    }

    public static Decision allow(String action, long modelVersion, CommercialPhase commercialPhase,
                                 ConnectivityPhase connectivityPhase, long evaluatedAt, Long recheckAt,
                                 List<String> fenceReferences) {
        return new Decision(Outcome.ALLOW, action, modelVersion, commercialPhase, connectivityPhase,
                evaluatedAt, recheckAt, ReasonCategory.ENTITLED, fenceReferences,
                null, null, null, null, null);
    }

    public static Decision deny(String action, long modelVersion, CommercialPhase commercialPhase,
                                ConnectivityPhase connectivityPhase, long evaluatedAt,
                                ReasonCategory reasonCategory) {
        return new Decision(Outcome.DENY, action, modelVersion, commercialPhase, connectivityPhase,
                evaluatedAt, null, reasonCategory, null, null, null, null, null, null);
    }

    /**
     * 复制判定并附加本次在线请求对应的授权身份与版本快照。
     * 离线签名的 SubjectBinding 不复用到此处，避免在线服务伪造签名代数。
     */
    public Decision withAuthorizationMetadata(String productId, String grantId, DecisionBinding subjectBinding,
                                              long grantRevision, long grantStateVersion) {
        return new Decision(outcome, action, modelVersion, commercialPhase, connectivityPhase, evaluatedAt,
                recheckAt, reasonCategory, reservationFenceReferences, productId, grantId, subjectBinding,
                grantRevision, grantStateVersion);
    }

    public boolean isAllowed() {
        return outcome == Outcome.ALLOW;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getAction() {
        return action;
    }

    public long getModelVersion() {
        return modelVersion;
    }

    public CommercialPhase getCommercialPhase() {
        return commercialPhase;
    }

    public ConnectivityPhase getConnectivityPhase() {
        return connectivityPhase;
    }

    public long getEvaluatedAt() {
        return evaluatedAt;
    }

    /** 最早重查截止；永久且无截止时为 null，不是缓存许可。 */
    public Long getRecheckAt() {
        return recheckAt;
    }

    public ReasonCategory getReasonCategory() {
        return reasonCategory;
    }

    public List<String> getReservationFenceReferences() {
        return reservationFenceReferences;
    }

    public String getProductId() {
        return productId;
    }

    public String getGrantId() {
        return grantId;
    }

    /** 在线响应中的对象绑定；未知授权的通用拒绝为 null。 */
    public DecisionBinding getSubjectBinding() {
        return subjectBinding;
    }

    public Long getGrantRevision() {
        return grantRevision;
    }

    public Long getGrantStateVersion() {
        return grantStateVersion;
    }
}
