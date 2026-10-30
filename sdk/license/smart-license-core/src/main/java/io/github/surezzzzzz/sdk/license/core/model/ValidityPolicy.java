package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.util.Collections;
import java.util.List;

/**
 * 授权期限：FIXED（三截止）或 PERPETUAL（仅 startsAt，可选维护截止与宽限动作）。
 *
 * <p>FIXED 要求 startsAt &lt; serviceUntil &lt;= commercialGraceUntil；PERPETUAL 禁止
 * serviceUntil/commercialGraceUntil。永久使用权与维护期分开，维护到期不终止运行权。</p>
 *
 * @author surezzzzzz
 */
public final class ValidityPolicy {

    /** 期限种类。 */
    public enum Kind {
        FIXED("FIXED"), PERPETUAL("PERPETUAL");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final Kind kind;
    private final long startsAt;
    private final Long serviceUntil;
    private final Long commercialGraceUntil;
    private final Long maintenanceUntil;
    private final List<String> graceActions;

    private ValidityPolicy(Kind kind, long startsAt, Long serviceUntil, Long commercialGraceUntil,
                           Long maintenanceUntil, List<String> graceActions) {
        this.kind = kind;
        this.startsAt = startsAt;
        this.serviceUntil = serviceUntil;
        this.commercialGraceUntil = commercialGraceUntil;
        this.maintenanceUntil = maintenanceUntil;
        this.graceActions = graceActions == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<String>(graceActions));
    }

    /** FIXED 期限：serviceUntil &lt;= commercialGraceUntil，宽限截止不为 null。 */
    public static ValidityPolicy fixed(long startsAt, long serviceUntil, long commercialGraceUntil,
                                       List<String> graceActions) {
        if (startsAt >= serviceUntil || serviceUntil > commercialGraceUntil) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
        }
        return new ValidityPolicy(Kind.FIXED, startsAt, serviceUntil, commercialGraceUntil, null, graceActions);
    }

    /** PERPETUAL 期限：无 serviceUntil/commercialGraceUntil，可选维护截止。 */
    public static ValidityPolicy perpetual(long startsAt, Long maintenanceUntil, List<String> graceActions) {
        return new ValidityPolicy(Kind.PERPETUAL, startsAt, null, null, maintenanceUntil, graceActions);
    }

    /** FIXED 期限带可选维护截止（维护阶段只约束声明需要维护权益的动作，不自动终止运行）。 */
    public static ValidityPolicy of(long startsAt, long serviceUntil, long commercialGraceUntil,
                                     Long maintenanceUntil, List<String> graceActions) {
        if (startsAt >= serviceUntil || serviceUntil > commercialGraceUntil) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
        }
        if (maintenanceUntil != null && maintenanceUntil < startsAt) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
        }
        return new ValidityPolicy(Kind.FIXED, startsAt, serviceUntil, commercialGraceUntil,
                maintenanceUntil, graceActions);
    }

    public Kind getKind() {
        return kind;
    }

    public long getStartsAt() {
        return startsAt;
    }

    /** FIXED 专用；PERPETUAL 为 null。 */
    public Long getServiceUntil() {
        return serviceUntil;
    }

    /** FIXED 专用；PERPETUAL 为 null。 */
    public Long getCommercialGraceUntil() {
        return commercialGraceUntil;
    }

    /** 可选独立维护截止。 */
    public Long getMaintenanceUntil() {
        return maintenanceUntil;
    }

    /** 商业宽限/维护期允许的动作子集。 */
    public List<String> getGraceActions() {
        return graceActions;
    }

    /** 当前商业阶段。 */
    public Decision.CommercialPhase commercialPhaseAt(long now) {
        if (now < startsAt) {
            return Decision.CommercialPhase.NOT_STARTED;
        }
        if (kind == Kind.PERPETUAL) {
            return Decision.CommercialPhase.ACTIVE;
        }
        if (now < serviceUntil) {
            return Decision.CommercialPhase.ACTIVE;
        }
        if (now < commercialGraceUntil) {
            return Decision.CommercialPhase.COMMERCIAL_GRACE;
        }
        return Decision.CommercialPhase.ENDED;
    }
}
