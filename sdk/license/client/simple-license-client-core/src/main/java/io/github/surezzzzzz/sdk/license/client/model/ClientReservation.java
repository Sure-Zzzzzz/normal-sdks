package io.github.surezzzzzz.sdk.license.client.model;

/**
 * 客户端侧完整占用/扣减回执：保留操作关联、围栏与真实终态。
 *
 * <p>回执不是动作授权。幂等重放可能返回 RELEASED/EXPIRED，调用方不得将收到回执当作仍在占用。</p>
 *
 * @author surezzzzzz
 */
public final class ClientReservation {

    private final String reservationId;
    private final String grantId;
    private final String metric;
    private final long quantity;
    private final String kind;
    private final String action;
    private final long grantRevision;
    private final String businessOperationId;
    private final long fence;
    private final long validUntil;
    private final String state;

    /** 历史回执缺少关联及状态，只保留读取兼容；不能被判断为 ACTIVE。 */
    @Deprecated
    public ClientReservation(String reservationId, String metric, long quantity, long fence, long validUntil) {
        this(reservationId, null, metric, quantity, null, null, 0L, null, fence, validUntil, null);
    }

    /** 完整回执由官方传输严格解析；自定义传输也应核对所有字段与请求关联。 */
    public ClientReservation(String reservationId, String grantId, String metric, long quantity,
                             String kind, String action, long grantRevision, String businessOperationId,
                             long fence, long validUntil, String state) {
        this.reservationId = reservationId;
        this.grantId = grantId;
        this.metric = metric;
        this.quantity = quantity;
        this.kind = kind;
        this.action = action;
        this.grantRevision = grantRevision;
        this.businessOperationId = businessOperationId;
        this.fence = fence;
        this.validUntil = validUntil;
        this.state = state;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getMetric() {
        return metric;
    }

    public long getQuantity() {
        return quantity;
    }

    public String getKind() {
        return kind;
    }

    public String getAction() {
        return action;
    }

    public long getGrantRevision() {
        return grantRevision;
    }

    public String getBusinessOperationId() {
        return businessOperationId;
    }

    public long getFence() {
        return fence;
    }

    public long getValidUntil() {
        return validUntil;
    }

    public String getState() {
        return state;
    }

    /** 仅判断服务器回执状态；调用方仍需核对截止并取得关联动作的允许判定。 */
    public boolean isActive() {
        return "ACTIVE".equals(state);
    }
}
