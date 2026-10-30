package io.github.surezzzzzz.sdk.license.core.spi;

/**
 * 本地额度台账端口：原子 checkAndReserve/commit/release，操作编号去重。
 *
 * <p>CONCURRENT 与 CONSUMPTION 分开；NAMED_SEAT 仅验证 Server 已分配的引用，不在本地创建名单/扩席。
 * 产品必须把占用与业务动作关联；Decision ALLOW 不是额度消费成功。</p>
 *
 * @author surezzzzzz
 */
public interface QuotaLedger {

    /** 原子检查并预占（CONCURRENT 预占或 CONSUMPTION 扣减）。 */
    ReservationResult checkAndReserve(String metric, long quantity, String businessOperationId);

    /** 释放并发占用（累计扣减不可释放）。 */
    boolean release(String reservationId);

    /** 预占/扣减结果。 */
    final class ReservationResult {

        private final boolean granted;
        private final String reservationId;
        private final long fence;
        private final long validUntil;
        private final String reasonCategory;

        public ReservationResult(boolean granted, String reservationId, long fence, long validUntil,
                                 String reasonCategory) {
            this.granted = granted;
            this.reservationId = reservationId;
            this.fence = fence;
            this.validUntil = validUntil;
            this.reasonCategory = reasonCategory;
        }

        public boolean isGranted() {
            return granted;
        }

        public String getReservationId() {
            return reservationId;
        }

        public long getFence() {
            return fence;
        }

        public long getValidUntil() {
            return validUntil;
        }

        public String getReasonCategory() {
            return reasonCategory;
        }
    }
}
