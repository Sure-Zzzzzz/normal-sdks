package io.github.surezzzzzz.sdk.license.server.support;

import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.constant.GrantValidityConstant;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

/**
 * 在线判定与离线签发共用的持久期限读取；不根据读取或签发时刻补造期限。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class GrantValidityHelper {

    private GrantValidityHelper() {
    }

    /**
     * 从授权行读取严格期限，缺失或非法事实明确拒绝，交由受控迁移补齐。
     *
     * @param grant 授权持久行
     * @return 已校验的固定或永久期限
     */
    public static ValidityPolicy fromGrant(GrantRevisionEntity grant) {
        String kind = grant.getValidityKind();
        if (kind == null || grant.getStartsAt() == null) {
            throw missing(grant);
        }
        if (grant.getStartsAt() <= 0L) {
            throw invalid(grant);
        }
        if (ValidityPolicy.Kind.PERPETUAL.getCode().equals(kind)) {
            if (grant.getServiceUntil() != null || grant.getGraceUntil() != null) {
                throw invalid(grant);
            }
            return ValidityPolicy.perpetual(grant.getStartsAt(), null, Collections.<String>emptyList());
        }
        if (!ValidityPolicy.Kind.FIXED.getCode().equals(kind)) {
            throw invalid(grant);
        }
        if (grant.getServiceUntil() == null || grant.getGraceUntil() == null) {
            throw missing(grant);
        }
        try {
            return ValidityPolicy.fixed(grant.getStartsAt(), grant.getServiceUntil(), grant.getGraceUntil(),
                    Collections.<String>emptyList());
        } catch (LicenseCoreException failure) {
            throw invalid(grant);
        }
    }

    private static LicenseHttpException missing(GrantRevisionEntity grant) {
        log.debug("授权期限读取拒绝：grantId={}, reason=missing-validity", grant.getGrantId());
        return LicenseHttpException.unprocessable(GrantValidityConstant.MISSING_VALIDITY);
    }

    private static LicenseHttpException invalid(GrantRevisionEntity grant) {
        log.debug("授权期限读取拒绝：grantId={}, reason=invalid-validity", grant.getGrantId());
        return LicenseHttpException.unprocessable(GrantValidityConstant.INVALID_VALIDITY);
    }
}
