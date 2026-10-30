package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;

/**
 * 组织成员资格政策：OFFLINE 禁止 required=true；ONLINE/HYBRID 另有观察时效与成员租约。
 *
 * <p>CUSTOMER_MEMBERS 必须 required=true；ASSIGNED_SUBJECTS 可选择另要求成员资格（求交）。
 * false 时不携带其他属性。</p>
 *
 * @author surezzzzzz
 */
public final class OrganizationPolicy {

    private final boolean required;
    private final Integer maxObservationAgeSeconds;
    private final Integer membershipLeaseSeconds;

    private OrganizationPolicy(boolean required, Integer maxObservationAgeSeconds,
                               Integer membershipLeaseSeconds) {
        if (!required && (maxObservationAgeSeconds != null || membershipLeaseSeconds != null)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (required) {
            if (maxObservationAgeSeconds == null || membershipLeaseSeconds == null
                    || maxObservationAgeSeconds < 1 || maxObservationAgeSeconds > 86400
                    || membershipLeaseSeconds < 1 || membershipLeaseSeconds > maxObservationAgeSeconds) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
        }
        this.required = required;
        this.maxObservationAgeSeconds = maxObservationAgeSeconds;
        this.membershipLeaseSeconds = membershipLeaseSeconds;
    }

    /** 无组织依赖。 */
    public static OrganizationPolicy notRequired() {
        return new OrganizationPolicy(false, null, null);
    }

    /** 要求组织成员资格（ONLINE/HYBRID 人员专用）。 */
    public static OrganizationPolicy required(int maxObservationAgeSeconds, int membershipLeaseSeconds) {
        return new OrganizationPolicy(true, maxObservationAgeSeconds, membershipLeaseSeconds);
    }

    public boolean isRequired() {
        return required;
    }

    /** required=true 时 1..86400。 */
    public Integer getMaxObservationAgeSeconds() {
        return maxObservationAgeSeconds;
    }

    /** required=true 时 1..maxObservationAgeSeconds。 */
    public Integer getMembershipLeaseSeconds() {
        return membershipLeaseSeconds;
    }
}
