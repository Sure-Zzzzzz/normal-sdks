package io.github.surezzzzzz.sdk.license.client.model;

/**
 * 客户端侧租约结果：离线可验文件与硬截止。
 *
 * <p>refreshAfter 是续取建议不是硬截止；所有截止来自服务端签名，客户端不得本地延长。</p>
 *
 * @author surezzzzzz
 */
public final class ClientLease {

    private final String leaseId;
    private final String signedLeaseJws;
    private final long refreshAfter;
    private final long leaseUntil;
    private final long outageUntil;

    public ClientLease(String leaseId, String signedLeaseJws, long refreshAfter,
                       long leaseUntil, long outageUntil) {
        this.leaseId = leaseId;
        this.signedLeaseJws = signedLeaseJws;
        this.refreshAfter = refreshAfter;
        this.leaseUntil = leaseUntil;
        this.outageUntil = outageUntil;
    }

    public String getLeaseId() {
        return leaseId;
    }

    /** 已签名租约 Compact JWS 文本（交由 Verifier 验签使用，客户端不解释内容）。 */
    public String getSignedLeaseJws() {
        return signedLeaseJws;
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
}
