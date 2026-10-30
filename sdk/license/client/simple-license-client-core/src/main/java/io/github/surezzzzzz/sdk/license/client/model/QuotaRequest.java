package io.github.surezzzzzz.sdk.license.client.model;

import io.github.surezzzzzz.sdk.license.client.constant.ClientProtocolConstant;

/**
 * 额度占用请求值对象：并发预占与累计扣减共用，业务操作编号必填（去重键）。
 *
 * @author surezzzzzz
 */
public final class QuotaRequest {

    private final String grantId;
    private final String metric;
    private final long quantity;
    private final String businessOperationId;
    private final String productId;
    private final String subjectKind;
    private final String action;
    private final long expectedGrantRevision;
    private final String assignmentId;
    private final String activationId;

    /** 保留旧构造签名；缺少上下文的请求不能发送，须迁移到完整构造方法。 */
    public QuotaRequest(String grantId, String metric, long quantity, String businessOperationId) {
        this(null, grantId, null, null, metric, quantity, businessOperationId, 0L, null, null);
    }

    /** 完整额度请求；上下文由调用方提供，不从授权编号猜造产品、动作或修订。 */
    public QuotaRequest(String productId, String grantId, String subjectKind, String action,
                        String metric, long quantity, String businessOperationId,
                        long expectedGrantRevision, String assignmentId, String activationId) {
        if (grantId == null || grantId.isEmpty() || metric == null || metric.isEmpty()) {
            throw new IllegalArgumentException("授权与指标编号不得为空");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("占用数量必须为正整数");
        }
        if (businessOperationId == null || businessOperationId.isEmpty()) {
            throw new IllegalArgumentException("业务操作编号不得为空");
        }
        this.grantId = grantId;
        this.metric = metric;
        this.quantity = quantity;
        this.businessOperationId = businessOperationId;
        this.productId = productId;
        this.subjectKind = subjectKind;
        this.action = action;
        this.expectedGrantRevision = expectedGrantRevision;
        this.assignmentId = assignmentId;
        this.activationId = activationId;
        if (productId != null || subjectKind != null || action != null || expectedGrantRevision != 0L) {
            requireCompleteContext();
        }
    }

    /** 发送前拒绝旧的不完整便捷请求；对象联合形态与服务端契约保持一致。 */
    public void requireCompleteContext() {
        if (productId == null || productId.isEmpty() || action == null || action.isEmpty()
                || !("PERSON".equals(subjectKind) || "SERVICE".equals(subjectKind) || "DEPLOYMENT".equals(subjectKind))
                || expectedGrantRevision < 1 || expectedGrantRevision > ClientProtocolConstant.SAFE_INTEGER_MAX
                || quantity > ClientProtocolConstant.SAFE_INTEGER_MAX) {
            throw new IllegalArgumentException("额度请求必须提供产品、对象种类、动作和预期授权修订");
        }
        if ("DEPLOYMENT".equals(subjectKind)) {
            if (activationId == null || activationId.isEmpty() || assignmentId != null) {
                throw new IllegalArgumentException("部署额度请求必须携带激活编号且禁止固定资格编号");
            }
        } else if (activationId != null || "SERVICE".equals(subjectKind)
                && (assignmentId == null || assignmentId.isEmpty())) {
            throw new IllegalArgumentException("人员或服务额度请求的资格引用形态无效");
        }
    }

    public String getProductId() {
        return productId;
    }

    public String getSubjectKind() {
        return subjectKind;
    }

    public String getAction() {
        return action;
    }

    public long getExpectedGrantRevision() {
        return expectedGrantRevision;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public String getActivationId() {
        return activationId;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getMetric() {
        return metric;
    }

    public long getQuantity() {
        return quantity;
    }

    public String getBusinessOperationId() {
        return businessOperationId;
    }
}
