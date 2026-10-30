package io.github.surezzzzzz.sdk.license.client.model;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端侧判定结果（client-core 自有值对象，与 Verifier Decision 语义对齐但不共用内部类型）。
 *
 * <p>判定类调用无 last-known-good：失败即异常且业务不执行；本对象只承载合法响应。</p>
 *
 * @author surezzzzzz
 */
public final class ClientDecision {

    private final boolean allowed;
    private final String action;
    private final long modelVersion;
    private final String commercialPhase;
    private final String connectivityPhase;
    private final long evaluatedAt;
    private final Long recheckAt;
    private final String reasonCategory;
    private final String requestId;
    private final List<String> reservationFenceReferences;
    private final String productId;
    private final String grantId;
    private final ClientDecisionBinding subjectBinding;
    private final Long grantRevision;
    private final Long grantStateVersion;

    private ClientDecision(boolean allowed, String action, long modelVersion, String commercialPhase,
                           String connectivityPhase, long evaluatedAt, Long recheckAt,
                           String reasonCategory, String requestId, List<String> fenceReferences,
                           String productId, String grantId, ClientDecisionBinding subjectBinding,
                           Long grantRevision, Long grantStateVersion) {
        this.allowed = allowed;
        this.action = action;
        this.modelVersion = modelVersion;
        this.commercialPhase = commercialPhase;
        this.connectivityPhase = connectivityPhase;
        this.evaluatedAt = evaluatedAt;
        this.recheckAt = recheckAt;
        this.reasonCategory = reasonCategory;
        this.requestId = requestId;
        this.reservationFenceReferences = fenceReferences == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(fenceReferences));
        this.productId = productId;
        this.grantId = grantId;
        this.subjectBinding = subjectBinding;
        this.grantRevision = grantRevision;
        this.grantStateVersion = grantStateVersion;
    }

    public static ClientDecision allow(String action, long modelVersion, String commercialPhase,
                                       Long recheckAt, List<String> fenceReferences) {
        return allow(action, modelVersion, commercialPhase, null, 0L, null, recheckAt, fenceReferences);
    }

    /** 保留在线判定的连接阶段、权威判定时刻和请求关联号，供调用方审计与重查。 */
    public static ClientDecision allow(String action, long modelVersion, String commercialPhase,
                                       String connectivityPhase, long evaluatedAt, String requestId,
                                       Long recheckAt, List<String> fenceReferences) {
        return new ClientDecision(true, action, modelVersion, commercialPhase, connectivityPhase, evaluatedAt,
                recheckAt, "ENTITLED", requestId, fenceReferences,
                null, null, null, null, null);
    }

    public static ClientDecision allow(String action, long modelVersion, String commercialPhase,
                                       String connectivityPhase, long evaluatedAt, String requestId,
                                       Long recheckAt, List<String> fenceReferences,
                                       String productId, String grantId, ClientDecisionBinding subjectBinding,
                                       long grantRevision, long grantStateVersion) {
        return new ClientDecision(true, action, modelVersion, commercialPhase, connectivityPhase, evaluatedAt,
                recheckAt, "ENTITLED", requestId, fenceReferences, productId, grantId, subjectBinding,
                grantRevision, grantStateVersion);
    }

    public static ClientDecision deny(String action, long modelVersion, String commercialPhase,
                                      String reasonCategory) {
        return deny(action, modelVersion, commercialPhase, null, 0L, null, null, reasonCategory);
    }

    public static ClientDecision deny(String action, long modelVersion, String commercialPhase,
                                      String connectivityPhase, long evaluatedAt, String requestId,
                                      Long recheckAt, String reasonCategory) {
        return new ClientDecision(false, action, modelVersion, commercialPhase, connectivityPhase, evaluatedAt,
                recheckAt, reasonCategory, requestId, null, null, null, null, null, null);
    }

    public static ClientDecision deny(String action, long modelVersion, String commercialPhase,
                                      String connectivityPhase, long evaluatedAt, String requestId,
                                      Long recheckAt, String reasonCategory,
                                      String productId, String grantId, ClientDecisionBinding subjectBinding,
                                      long grantRevision, long grantStateVersion) {
        return new ClientDecision(false, action, modelVersion, commercialPhase, connectivityPhase, evaluatedAt,
                recheckAt, reasonCategory, requestId, null, productId, grantId, subjectBinding,
                grantRevision, grantStateVersion);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getAction() {
        return action;
    }

    public long getModelVersion() {
        return modelVersion;
    }

    /** 商业阶段；合法拒绝尚未识别授权时为 null，允许结论必须有实际阶段。 */
    public String getCommercialPhase() {
        return commercialPhase;
    }

    public String getConnectivityPhase() {
        return connectivityPhase;
    }

    public long getEvaluatedAt() {
        return evaluatedAt;
    }

    /** 本次已知的最早失效截止，无截止为 null，不是缓存许可。 */
    public Long getRecheckAt() {
        return recheckAt;
    }

    public String getReasonCategory() {
        return reasonCategory;
    }

    public String getRequestId() {
        return requestId;
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

    public ClientDecisionBinding getSubjectBinding() {
        return subjectBinding;
    }

    public Long getGrantRevision() {
        return grantRevision;
    }

    public Long getGrantStateVersion() {
        return grantStateVersion;
    }
}
