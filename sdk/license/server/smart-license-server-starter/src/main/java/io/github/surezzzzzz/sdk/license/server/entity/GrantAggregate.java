package io.github.surezzzzzz.sdk.license.server.entity;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.constant.GrantState;

/**
 * 客户授权聚合（内存领域形态；持久化映射由 repository 层承担）。
 *
 * <p>客户/产品/对象种类不可原地换；商业修订不可变且版本唯一；管理状态与时间阶段分离。</p>
 *
 * @author surezzzzzz
 */
public final class GrantAggregate {

    private final String grantId;
    private final String customerId;
    private final String productId;
    private final SubjectKind subjectKind;
    private final EligibilityPolicy eligibilityPolicy;
    private ValidityPolicy validity;
    private final EligibilityPolicy.Mode mode;

    private GrantState state;
    private long grantRevision;
    private long grantStateVersion;

    private GrantAggregate(String grantId, String customerId, String productId, SubjectKind subjectKind,
                           EligibilityPolicy eligibilityPolicy, ValidityPolicy validity,
                           EligibilityPolicy.Mode mode, GrantState state,
                           long grantRevision, long grantStateVersion) {
        this.grantId = grantId;
        this.customerId = customerId;
        this.productId = productId;
        this.subjectKind = subjectKind;
        this.eligibilityPolicy = eligibilityPolicy;
        this.validity = validity;
        this.mode = mode;
        this.state = state;
        this.grantRevision = grantRevision;
        this.grantStateVersion = grantStateVersion;
        EligibilityPolicy.validateCombination(subjectKind, eligibilityPolicy, mode);
    }

    /** 新建授权（DRAFT 起步，修订/状态版本均为 1）。 */
    public static GrantAggregate draft(String grantId, String customerId, String productId,
                                       SubjectKind subjectKind, EligibilityPolicy eligibilityPolicy,
                                       ValidityPolicy validity, EligibilityPolicy.Mode mode) {
        return new GrantAggregate(grantId, customerId, productId, subjectKind, eligibilityPolicy,
                validity, mode, GrantState.DRAFT, 1L, 1L);
    }

    /** 从持久层装载既有授权。 */
    public static GrantAggregate load(String grantId, String customerId, String productId,
                                      SubjectKind subjectKind, EligibilityPolicy eligibilityPolicy,
                                      ValidityPolicy validity, EligibilityPolicy.Mode mode,
                                      GrantState state, long grantRevision, long grantStateVersion) {
        return new GrantAggregate(grantId, customerId, productId, subjectKind, eligibilityPolicy,
                validity, mode, state, grantRevision, grantStateVersion);
    }

    /** 发布启用（DRAFT→ENABLED，一次性，状态版本+1）。 */
    public void enable() {
        if (state != GrantState.DRAFT) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        state = GrantState.ENABLED;
        grantStateVersion++;
    }

    /** 暂停/恢复：ENABLED 与 SUSPENDED 间转换，状态版本+1；REVOKED 终态。 */
    public void suspendOrResume(boolean suspend) {
        GrantState target = suspend ? GrantState.SUSPENDED : GrantState.ENABLED;
        if (state != GrantState.ENABLED && state != GrantState.SUSPENDED) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (state == target) {
            return;
        }
        state = target;
        grantStateVersion++;
    }

    /**
     * 商业修订：期限随新修订生效（版本+1），客户/产品/对象种类不可变。
     * 新期限非空且与旧期限不同才推进修订；修订不可把期限改回 null（走吊销/治理路径）。
     */
    public long revise(ValidityPolicy newValidity) {
        if (state == GrantState.REVOKED) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (newValidity == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        grantRevision++;
        this.validity = newValidity;
        return grantRevision;
    }

    /** 在线判定的管理状态门槛：仅 ENABLED 可用。 */
    public boolean isUsable() {
        return state == GrantState.ENABLED;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductId() {
        return productId;
    }

    public SubjectKind getSubjectKind() {
        return subjectKind;
    }

    public EligibilityPolicy getEligibilityPolicy() {
        return eligibilityPolicy;
    }

    public ValidityPolicy getValidity() {
        return validity;
    }

    public EligibilityPolicy.Mode getMode() {
        return mode;
    }

    public GrantState getState() {
        return state;
    }

    public long getGrantRevision() {
        return grantRevision;
    }

    public long getGrantStateVersion() {
        return grantStateVersion;
    }
}
