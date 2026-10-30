package io.github.surezzzzzz.sdk.license.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 在线判定请求（纯 ONLINE 与 HYBRID 在线动作共用，无文件前置）。
 *
 * <p>仅 DEPLOYMENT 携带 activationId，人员/服务禁止；消费动作必须携带 businessOperationId
 * 并绑定消费引用。候选发现由在线客户端完成，本请求必须明确选定一份 grant。</p>
 *
 * @author surezzzzzz
 */
public final class OnlineAuthorizationRequest {

    private final String productId;
    private final String grantId;
    private final SubjectKind subjectKind;
    private final String action;
    private final String activationId;
    private final String assignmentId;
    private final String businessOperationId;
    private final Map<String, Object> facts;
    private final List<String> reservationReferences;

    private OnlineAuthorizationRequest(String productId, String grantId, SubjectKind subjectKind, String action,
                                       String activationId, String assignmentId, String businessOperationId,
                                       Map<String, Object> facts, List<String> reservationReferences) {
        this.productId = productId;
        this.grantId = grantId;
        this.subjectKind = subjectKind;
        this.action = action;
        this.activationId = activationId;
        this.assignmentId = assignmentId;
        this.businessOperationId = businessOperationId;
        this.facts = facts == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new java.util.HashMap<String, Object>(facts));
        this.reservationReferences = reservationReferences == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<String>(reservationReferences));
    }

    /** 部署在线判定请求（必须携带 activationId）。 */
    public static OnlineAuthorizationRequest deployment(String productId, String grantId, String action,
                                                        String activationId, Map<String, Object> facts,
                                                        List<String> reservationReferences) {
        validateCommon(productId, grantId, action);
        if (activationId == null || activationId.isEmpty()) {
            throw new IllegalArgumentException("部署在线判定必须携带 activationId");
        }
        return new OnlineAuthorizationRequest(productId, grantId, SubjectKind.DEPLOYMENT, action,
                activationId, null, null, facts, reservationReferences);
    }

    /** 人员/服务在线判定请求（禁止 activationId；固定资格可携 assignmentId 索引）。 */
    public static OnlineAuthorizationRequest personOrService(String productId, String grantId,
                                                              SubjectKind subjectKind, String action,
                                                              String assignmentId,
                                                              Map<String, Object> facts,
                                                              List<String> reservationReferences) {
        validateCommon(productId, grantId, action);
        if (!subjectKind.isSubjectRefKind()) {
            throw new IllegalArgumentException("仅 PERSON/SERVICE 可用此构造；部署用 deployment()");
        }
        return new OnlineAuthorizationRequest(productId, grantId, subjectKind, action,
                null, assignmentId, null, facts, reservationReferences);
    }

    /** 消费动作请求：businessOperationId 必填。 */
    public static OnlineAuthorizationRequest consumption(OnlineAuthorizationRequest base, String businessOperationId) {
        if (businessOperationId == null || businessOperationId.isEmpty()) {
            throw new IllegalArgumentException("消费动作必须携带 businessOperationId");
        }
        return new OnlineAuthorizationRequest(base.productId, base.grantId, base.subjectKind, base.action,
                base.activationId, base.assignmentId, businessOperationId,
                base.facts, base.reservationReferences);
    }

    private static void validateCommon(String productId, String grantId, String action) {
        if (productId == null || productId.isEmpty() || grantId == null || grantId.isEmpty()
                || action == null || action.isEmpty()) {
            throw new IllegalArgumentException("productId/grantId/action 均为必填");
        }
    }

    public String getProductId() {
        return productId;
    }

    public String getGrantId() {
        return grantId;
    }

    public SubjectKind getSubjectKind() {
        return subjectKind;
    }

    public String getAction() {
        return action;
    }

    /** 仅部署请求非空。 */
    public String getActivationId() {
        return activationId;
    }

    /** 固定资格索引（可空，服务端仍以真实 SubjectRef 核对）。 */
    public String getAssignmentId() {
        return assignmentId;
    }

    /** 消费动作业务编号（仅消费请求非空）。 */
    public String getBusinessOperationId() {
        return businessOperationId;
    }

    /** 本次运行事实（已声明字段类型严格校验由服务端执行）。 */
    public Map<String, Object> getFacts() {
        return facts;
    }

    /** 并发占用/累计消费引用编号。 */
    public List<String> getReservationReferences() {
        return reservationReferences;
    }
}
