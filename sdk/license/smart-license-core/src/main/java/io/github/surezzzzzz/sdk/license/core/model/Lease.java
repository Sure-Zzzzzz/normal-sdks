package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.util.Collections;
import java.util.List;

/**
 * HYBRID 短期签名租约：硬截止（leaseUntil &lt;= outageUntil）+ 断网动作集 + 已签占用引用。
 *
 * <p>refreshAfter 是建议续取时刻不是硬截止；所有截止签入凭据，重启/网络失败/下载不能重置。
 * 已知权威否定不能因网络故障回退旧租约。</p>
 *
 * @author surezzzzzz
 */
public final class Lease {

    private final String leaseId;
    private final long refreshAfter;
    private final long leaseUntil;
    private final long outageUntil;
    private final List<String> outageActions;
    private final SubjectRef requester;
    private final List<ReservationReference> reservationReferences;

    /** 已签并发占用引用。 */
    public static final class ReservationReference {

        private final String reservationId;
        private final String metric;
        private final long quantity;
        private final long fence;
        private final long validUntil;

        public ReservationReference(String reservationId, String metric, long quantity, long fence, long validUntil) {
            this.reservationId = reservationId;
            this.metric = metric;
            this.quantity = quantity;
            this.fence = fence;
            this.validUntil = validUntil;
        }

        public String getReservationId() {
            return reservationId;
        }

        public String getMetric() {
            return metric;
        }

        public long getQuantity() {
            return quantity;
        }

        public long getFence() {
            return fence;
        }

        public long getValidUntil() {
            return validUntil;
        }
    }

    private Lease(String leaseId, long refreshAfter, long leaseUntil, long outageUntil,
                  List<String> outageActions, SubjectRef requester, List<ReservationReference> references) {
        if (leaseId == null || leaseId.isEmpty() || requester == null
                || requester.getKind() != SubjectKind.SERVICE) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (refreshAfter > leaseUntil || leaseUntil > outageUntil) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME, ErrorMessage.PROTOCOL_TIME_INVALID);
        }
        this.leaseId = leaseId;
        this.refreshAfter = refreshAfter;
        this.leaseUntil = leaseUntil;
        this.outageUntil = outageUntil;
        this.outageActions = outageActions == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<String>(outageActions));
        this.requester = requester;
        this.reservationReferences = references == null
                ? Collections.<ReservationReference>emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<ReservationReference>(references));
    }

    public static Lease of(String leaseId, long refreshAfter, long leaseUntil, long outageUntil,
                           List<String> outageActions, SubjectRef requester,
                           List<ReservationReference> references) {
        return new Lease(leaseId, refreshAfter, leaseUntil, outageUntil, outageActions, requester, references);
    }

    public String getLeaseId() {
        return leaseId;
    }

    public long getRefreshAfter() {
        return refreshAfter;
    }

    public long getLeaseUntil() {
        return leaseUntil;
    }

    public long getOutageUntil() {
        return outageUntil;
    }

    public List<String> getOutageActions() {
        return outageActions;
    }

    /** 领取调用者（服务身份引用，不替代人员对象）。 */
    public SubjectRef getRequester() {
        return requester;
    }

    /** 已签并发占用引用（非累计）。 */
    public List<ReservationReference> getReservationReferences() {
        return reservationReferences;
    }

    /** 当前连接阶段。 */
    public Decision.ConnectivityPhase connectivityPhaseAt(long now) {
        if (now < leaseUntil) {
            return Decision.ConnectivityPhase.NORMAL;
        }
        if (now < outageUntil) {
            return Decision.ConnectivityPhase.OUTAGE;
        }
        return Decision.ConnectivityPhase.ENDED;
    }
}
