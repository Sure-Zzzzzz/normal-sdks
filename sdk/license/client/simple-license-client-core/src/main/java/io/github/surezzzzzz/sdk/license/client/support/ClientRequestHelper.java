package io.github.surezzzzzz.sdk.license.client.support;

import io.github.surezzzzzz.sdk.license.client.model.GrantSelectionRequest;
import io.github.surezzzzzz.sdk.license.client.model.QuotaRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 四种传输共用的请求映射，防止值对象退回位置参数时丢失新字段。
 *
 * @author surezzzzzz
 */
public final class ClientRequestHelper {

    private ClientRequestHelper() {
    }

    /** 显式分页请求保留部署绑定，客户端不自行代理其他人员或服务。 */
    public static Map<String, Object> selection(GrantSelectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("候选发现请求不得为空");
        }
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("productId", request.getProductId());
        body.put("subjectKind", request.getSubjectKind());
        body.put("action", request.getAction());
        body.put("page", request.getPage());
        body.put("size", request.getSize());
        optional(body, "activationId", request.getActivationId());
        return body;
    }

    /** 映射判定请求，额度引用即使为空也明确输出。 */
    public static Map<String, Object> decision(String productId, String grantId, String subjectKind,
                                               String action, String activationId, String assignmentId,
                                               String businessOperationId, Map<String, Object> facts,
                                               List<String> reservationReferences) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("productId", productId);
        body.put("grantId", grantId);
        body.put("subjectKind", subjectKind);
        body.put("action", action);
        optional(body, "activationId", activationId);
        optional(body, "assignmentId", assignmentId);
        optional(body, "businessOperationId", businessOperationId);
        body.put("facts", facts == null ? Collections.<String, Object>emptyMap() : facts);
        body.put("reservationReferences", reservationReferences == null ? Collections.<String>emptyList()
                : new ArrayList<String>(reservationReferences));
        return body;
    }

    /** 映射完整额度请求，拒绝无法提供真实产品、动作或修订的旧便捷请求。 */
    public static Map<String, Object> quota(QuotaRequest request, String kind) {
        if (request == null) {
            throw new IllegalArgumentException("额度请求不得为空");
        }
        request.requireCompleteContext();
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("productId", request.getProductId());
        body.put("grantId", request.getGrantId());
        body.put("subjectKind", request.getSubjectKind());
        body.put("action", request.getAction());
        body.put("metric", request.getMetric());
        body.put("quantity", request.getQuantity());
        body.put("kind", kind);
        body.put("businessOperationId", request.getBusinessOperationId());
        body.put("expectedGrantRevision", request.getExpectedGrantRevision());
        optional(body, "assignmentId", request.getAssignmentId());
        optional(body, "activationId", request.getActivationId());
        return body;
    }

    private static void optional(Map<String, Object> body, String key, String value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
