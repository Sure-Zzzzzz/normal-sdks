package io.github.surezzzzzz.sdk.license.client.model;

import io.github.surezzzzzz.sdk.license.client.constant.ClientProtocolConstant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线判定请求值对象：字段演进经新增构建方法表达，不破坏既有调用方二进制兼容。
 *
 * <p>activationId 仅部署请求携带；assignmentId 为固定资格索引；businessOperationId 在消费
 * 类动作必填（绑定消费引用去重）。facts 为本次声明字段的运行事实，入参防御复制。</p>
 *
 * @author surezzzzzz
 */
public final class AuthorizationRequest {

    private final String productId;
    private final String grantId;
    private final String subjectKind;
    private final String action;
    private final String activationId;
    private final String assignmentId;
    private final String businessOperationId;
    private final Map<String, Object> facts;
    private final List<String> reservationReferences;

    private AuthorizationRequest(String productId, String grantId, String subjectKind, String action,
                                 String activationId, String assignmentId, String businessOperationId,
                                 Map<String, Object> facts) {
        this(productId, grantId, subjectKind, action, activationId, assignmentId, businessOperationId,
                facts, Collections.<String>emptyList());
    }

    private AuthorizationRequest(String productId, String grantId, String subjectKind, String action,
                                 String activationId, String assignmentId, String businessOperationId,
                                 Map<String, Object> facts, List<String> reservationReferences) {
        this.productId = productId;
        this.grantId = grantId;
        this.subjectKind = subjectKind;
        this.action = action;
        this.activationId = activationId;
        this.assignmentId = assignmentId;
        this.businessOperationId = businessOperationId;
        this.facts = Collections.unmodifiableMap(new HashMap<String, Object>(facts));
        List<String> references = reservationReferences == null
                ? Collections.<String>emptyList() : new ArrayList<String>(reservationReferences);
        java.util.Set<String> unique = new java.util.HashSet<String>();
        if (references.size() > ClientProtocolConstant.RESERVATION_REFERENCES_MAX) {
            throw new IllegalArgumentException("占用引用超过协议上限");
        }
        for (String reference : references) {
            if (reference == null || reference.isEmpty() || !unique.add(reference)) {
                throw new IllegalArgumentException("占用引用必须为非空且不重复的编号");
            }
        }
        this.reservationReferences = Collections.unmodifiableList(references);
    }

    /** 基础判定（人员/服务在线、无消费去重诉求）。 */
    public static AuthorizationRequest of(String productId, String grantId, String subjectKind,
                                          String action, Map<String, Object> facts) {
        return new AuthorizationRequest(productId, grantId, subjectKind, action,
                null, null, null, facts == null ? Collections.<String, Object>emptyMap() : facts);
    }

    /** 部署判定（携带激活编号）。 */
    public static AuthorizationRequest deployment(String productId, String grantId, String action,
                                                  String activationId, Map<String, Object> facts) {
        return new AuthorizationRequest(productId, grantId, "DEPLOYMENT", action,
                activationId, null, null, facts == null ? Collections.<String, Object>emptyMap() : facts);
    }

    /** 固定资格判定（人员/服务携带资格索引）。 */
    public static AuthorizationRequest assigned(String productId, String grantId, String subjectKind,
                                                String action, String assignmentId,
                                                Map<String, Object> facts) {
        return new AuthorizationRequest(productId, grantId, subjectKind, action,
                null, assignmentId, null, facts == null ? Collections.<String, Object>emptyMap() : facts);
    }

    /** 消费类判定（业务操作编号必填，绑定去重）。 */
    public static AuthorizationRequest consumption(String productId, String grantId, String subjectKind,
                                                   String action, String businessOperationId,
                                                   Map<String, Object> facts) {
        if (businessOperationId == null || businessOperationId.isEmpty()) {
            throw new IllegalArgumentException("消费类判定的业务操作编号不得为空");
        }
        return new AuthorizationRequest(productId, grantId, subjectKind, action,
                null, null, businessOperationId, facts == null ? Collections.<String, Object>emptyMap() : facts);
    }

    public String getProductId() {
        return productId;
    }

    /** 保留既有对象绑定并携带额度引用，返回独立只读请求。 */
    public AuthorizationRequest withReservationReferences(List<String> references) {
        return new AuthorizationRequest(productId, grantId, subjectKind, action, activationId, assignmentId,
                businessOperationId, facts, references);
    }

    /** 保留部署/固定资格上下文并关联本次稳定业务操作编号。 */
    public AuthorizationRequest withBusinessOperationId(String operationId) {
        if (operationId == null || operationId.isEmpty()) {
            throw new IllegalArgumentException("业务操作编号不得为空");
        }
        return new AuthorizationRequest(productId, grantId, subjectKind, action, activationId, assignmentId,
                operationId, facts, reservationReferences);
    }

    /** 本次真实额度占用/消费引用，不能修改或与其他请求共享可变集合。 */
    public List<String> getReservationReferences() {
        return reservationReferences;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getSubjectKind() {
        return subjectKind;
    }

    public String getAction() {
        return action;
    }

    /** 仅部署请求非空。 */
    public String getActivationId() {
        return activationId;
    }

    /** 固定资格索引；无固定资格为 null。 */
    public String getAssignmentId() {
        return assignmentId;
    }

    /** 消费类动作非空。 */
    public String getBusinessOperationId() {
        return businessOperationId;
    }

    /** 本次运行事实（只读视图）。 */
    public Map<String, Object> getFacts() {
        return facts;
    }
}
