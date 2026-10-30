package io.github.surezzzzzz.sdk.license.core.verify;

import io.github.surezzzzzz.sdk.license.core.model.SubjectBinding;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.SubjectRef;
import java.util.Optional;

/**
 * 受控安装上下文：验签目标（issuer/product/customer/grant 与稳定对象键）来自受控登记，
 * 不由待验文件自报。
 *
 * @author surezzzzzz
 */
public final class VerificationContext {

    private final String issuerId;
    private final String productId;
    private final String customerId;
    private final String grantId;
    private final SubjectBinding expectedBinding;
    private final long installedTrustVersionFloor;

    private VerificationContext(String issuerId, String productId, String customerId, String grantId,
                                SubjectBinding expectedBinding, long installedTrustVersionFloor) {
        this.issuerId = issuerId;
        this.productId = productId;
        this.customerId = customerId;
        this.grantId = grantId;
        this.expectedBinding = expectedBinding;
        this.installedTrustVersionFloor = installedTrustVersionFloor;
    }

    public static VerificationContext of(String issuerId, String productId, String customerId, String grantId,
                                         SubjectBinding expectedBinding) {
        return new VerificationContext(issuerId, productId, customerId, grantId, expectedBinding, 0L);
    }

    /** 需要最低信任资料版本时使用；0表示沿用当前受控信任域的最低要求。 */
    public static VerificationContext of(String issuerId, String productId, String customerId, String grantId,
                                         SubjectBinding expectedBinding, long installedTrustVersionFloor) {
        if (installedTrustVersionFloor < 0L) {
            throw new IllegalArgumentException("信任版本下限不能为负数");
        }
        return new VerificationContext(issuerId, productId, customerId, grantId, expectedBinding,
                installedTrustVersionFloor);
    }

    public String getIssuerId() {
        return issuerId;
    }

    public String getProductId() {
        return productId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getGrantId() {
        return grantId;
    }

    public SubjectBinding getExpectedBinding() {
        return expectedBinding;
    }

    public long getInstalledTrustVersionFloor() {
        return installedTrustVersionFloor;
    }

    /** 期望的人员/服务真实身份（部署上下文为空）。 */
    public Optional<SubjectRef> expectedSubjectRef() {
        if (expectedBinding == null || expectedBinding.getKind() == SubjectKind.DEPLOYMENT) {
            return Optional.empty();
        }
        return Optional.of(expectedBinding.subjectRef());
    }
}
