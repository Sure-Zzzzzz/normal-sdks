package io.github.surezzzzzz.sdk.license.client.model;

import io.github.surezzzzzz.sdk.license.client.constant.ClientProtocolConstant;

/**
 * 候选发现请求：分页显式提供，部署必须绑定真实激活，不用授权编号猜造对象。
 *
 * @author surezzzzzz
 */
public final class GrantSelectionRequest {

    private final String productId;
    private final String subjectKind;
    private final String action;
    private final String activationId;
    private final int page;
    private final int size;

    public GrantSelectionRequest(String productId, String subjectKind, String action,
                                 String activationId, int page, int size) {
        if (productId == null || productId.isEmpty() || action == null || action.isEmpty()
                || !("PERSON".equals(subjectKind) || "SERVICE".equals(subjectKind)
                || "DEPLOYMENT".equals(subjectKind))) {
            throw new IllegalArgumentException("候选发现必须提供产品、对象种类和动作");
        }
        if ("DEPLOYMENT".equals(subjectKind)) {
            if (activationId == null || activationId.isEmpty()) {
                throw new IllegalArgumentException("部署候选发现必须携带激活编号");
            }
        } else if (activationId != null) {
            throw new IllegalArgumentException("人员或服务候选发现禁止激活编号");
        }
        if (page < 1 || size < 1 || size > ClientProtocolConstant.SELECTION_SIZE_MAX) {
            throw new IllegalArgumentException("候选发现页码或页大小超出范围");
        }
        this.productId = productId;
        this.subjectKind = subjectKind;
        this.action = action;
        this.activationId = activationId;
        this.page = page;
        this.size = size;
    }

    /** 翻页保留同一产品、动作和激活绑定。 */
    public GrantSelectionRequest withPage(int nextPage) {
        return new GrantSelectionRequest(productId, subjectKind, action, activationId, nextPage, size);
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

    public String getActivationId() {
        return activationId;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
