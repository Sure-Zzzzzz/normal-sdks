package io.github.surezzzzzz.sdk.feedback.server.support;

import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;

/**
 * Owner 派生 Helper（KMS/CRM owner 定式：iam:{subjectId} / aksk:{clientId}）。
 *
 * <p>非直观约束：owner 只能从已验证请求上下文派生，永不出现在请求体（DESIGN §2）。</p>
 *
 * @author surezzzzzz
 */
public final class OwnerHelper {

    private OwnerHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构造人员 owner。
     *
     * @param subjectId 提交人 subjectId（裸值）
     * @return owner 形态 "iam:{subjectId}"
     */
    public static String buildIamOwner(String subjectId) {
        return String.format(SimpleFeedbackServerConstant.TEMPLATE_OWNER_IAM, subjectId);
    }

    /**
     * 构造机器 owner（首发无发放对象，保留定义）。
     *
     * @param clientId AKSK 客户端标识
     * @return owner 形态 "aksk:{clientId}"
     */
    public static String buildAkskOwner(String clientId) {
        return String.format(SimpleFeedbackServerConstant.TEMPLATE_OWNER_AKSK, clientId);
    }

    /**
     * 从人员 owner 剥出 subjectId。
     *
     * @param owner owner 形态字符串
     * @return subjectId 裸值；非 iam: 前缀时返回 null
     */
    public static String extractSubjectId(String owner) {
        String prefix = String.format(SimpleFeedbackServerConstant.TEMPLATE_OWNER_IAM, "");
        if (owner != null && owner.startsWith(prefix)) {
            return owner.substring(prefix.length());
        }
        return null;
    }
}
